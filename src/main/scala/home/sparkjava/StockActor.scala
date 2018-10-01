package home.sparkjava

import java.security.MessageDigest
import java.time.DayOfWeek._
import java.time.{LocalDate, LocalTime}
import java.time.format.DateTimeFormatter
import java.util.Date

import akka.actor.{Actor, Props}
import org.apache.logging.log4j.ThreadContext
import org.json4s._
import org.json4s.native.Serialization
import message.{DailyQuoteReturn, GetDailyQuote, HistoricalOrders, Tick}
import model._

import scala.annotation.tailrec

object StockActor {
    def props(symbol: String): Props = Props(new StockActor(symbol))
}

class StockActor(symbol: String) extends Actor with Util {
    val isWeekDay: Boolean = ! Seq(SATURDAY, SUNDAY).contains(LocalDate.now.getDayOfWeek)
    val md5Digest: MessageDigest = MessageDigest.getInstance("MD5")
    val isDow: Boolean = isDow(symbol)
    var fu = new Fundamental(
        Some(-.1), Some(-.1), Some(""), Some(-.1), Some(-.1), Some(-.1), Some(-.1), Some(-.1), Some(-.1), Some(-.1), ""
    )
    var p = new Position(
        Some(-.1), Some(-.1), Some(-.1), Some(-.1), Some(-.1), None, None, Some(-.1), Some(-.1), None, Some(-.1),
        Some(-.1), Some(-.1), Some(-1)
    )
    var q = new Quote(
        None, None, None, None, None, None, None, None, None, None, None, None, None, None, None
    )

    var lastRoundOrdersHash = ""
    var lastEstimateHash = ""
    var lastPositionHash = ""
    var lastFundamentalHash = ""
    var lastQuoteHash = ""
    var gotHistoricalOrders = false

    var instrument = ""
    var lastTimeHistoricalOrdersRequested: Long = 0 // in seconds
    var lastTimeHistoricalQuotesRequested: Long = 0
    var lastTimeBuy:  Long = 0 // in seconds
    var lastTimeSell: Long = 0
    val today: String = LocalDate.now.format(DateTimeFormatter.ISO_LOCAL_DATE)
    val orders: collection.mutable.SortedSet[OrderElement] =
        collection.mutable.SortedSet[OrderElement]()(Ordering.by[OrderElement, String](_.created_at)(Main.timestampOrdering.reverse))
    /*
     * This is the meaning of changeFromHigh: lowest_of_day = highest_of_day * changeFromHigh.
     * We get the average of changeFromHigh's of the last 10 days (remove the highest & lowest days).
     * If last_trade_price is closer to lowest_of_day, then we guess that the
     * real lowest_of_day = highest_of_day * changeFromHigh
     */
    var changeFromHigh: Double = 0
    var changeFromLow: Double = 0
    var estimatedLow: Double = 0
    var estimatedHigh: Double = Double.MaxValue
    var recentLowest: Double = 0 // this is the lowest in the last couple of days
    var debug: Boolean = false

    val _receive: Receive = {
        case _fu: Fundamental => if ((_fu.low.isDefined && _fu.high.isDefined) || _fu.open.isDefined) {
            fu = _fu
            sendFundamental
            instrument = fu.instrument
        }
        case _o: OrderElement => if (gotHistoricalOrders) {
            val o = if (_o.state.contains("cancelled") && _o.cumulative_quantity.exists(_ > 0))
                _o.copy(state = Some("filled"))
/*
            // TODO this is for testing on weekends
            else if (_o.state.contains("queued")) _o.copy(state = Some("confirmed"))
*/
            else _o
            orders -= o
            if (o.state.exists(_.contains("filled")) || o.state.exists(_.contains("confirmed"))) orders += o
            // orders sent to browser when StockActor receives a Position which is every 4 secs
        }
        case _p: Position =>   // we receive this every 4 seconds
            p = _p
            sendPosition
            if (p.instrument.nonEmpty) instrument = p.instrument.get

            val currentHour = LocalTime.now.getHour
            if (orders.nonEmpty) {
                var totalShares: Int = 0
                val x = lastRoundOrders() // x has (un)confirmed, (partially) filled orders
                val _lastRoundOrders = assignMatchId(x)
                if (q.last_trade_price.isDefined && instrument != "" && shouldDoBuySell) {
                    shouldBuySell(_lastRoundOrders, q.last_trade_price.get, debug) foreach { t =>
                        // (action, quantity, price, orderElement)
                        context.actorSelection(s"../../${OrderActor.NAME}") ! OrderActor.BuySell(t._1, symbol, instrument, t._2, t._3)
                        t._1 match {
                            case "buy" => lastTimeBuy = System.currentTimeMillis / 1000
                            case "sell" => lastTimeSell = System.currentTimeMillis / 1000
                        }
                        logger.warn(s"Just ${t._1.toUpperCase} ${t._2} $symbol $$${t._3} ${t._4}")
                    }
                }
                val lastRoundOrdersString = _lastRoundOrders.map(oe => {
                    val cq = oe.cumulative_quantity.get
                    totalShares += (if (oe.side.get == "buy") cq else -cq)
                    s"${oe.toString}  $totalShares"
                }).mkString("\n")
                val newHash = new String(MessageDigest.getInstance("MD5").digest(lastRoundOrdersString.getBytes))
                if (newHash != lastRoundOrdersHash) {
                    logger.debug(s"Last round orders before assigning matchId:\n${x.map(_.toString).mkString("\n")}")
                    lastRoundOrdersHash = newHash
                    logger.debug(s"Orders sent to browser: position ${p.quantity}\n$lastRoundOrdersString")
                    sendOrdersToBrowser(_lastRoundOrders)
                }
            }
            debug = false
        case _q: Quote => // the QuoteActor is sure that symbol, last_trade_price and instrument are there
            val T = 19
            q = _q
            sendQuote
            instrument = q.instrument.get
            val now = System.currentTimeMillis / 1000
            if (p.quantity.exists(_ >= 0) && orders.isEmpty && (now - lastTimeHistoricalOrdersRequested > T)
                    && !gotHistoricalOrders) {
                // Use T because we should wait for the OrderActor a bit because we receive quote every 4 seconds
                lastTimeHistoricalOrdersRequested = now
                context.actorSelection(s"../../${OrderActor.NAME}") !
                        HistoricalOrders(symbol, instrument, 4, Seq[OrderElement](), None)
            }
            if (changeFromHigh == 0 && changeFromLow == 0 && (now - lastTimeHistoricalQuotesRequested > T)) {
                lastTimeHistoricalQuotesRequested = now
                context.actorSelection(s"../../${QuoteActor.NAME}") ! GetDailyQuote(List(symbol), 0)
            }
            sendEstimate()
        case HistoricalOrders(_, _, _, _orders, _) =>
            gotHistoricalOrders = true
            orders ++= _orders.collect {
                case oe @ OrderElement(_, _, _, _, _, _, _, Some(state), _, _, _, _, _) if isAcceptableOrderState(state, oe) =>
                    if (state == "cancelled") oe.copy(state = Some("filled")) else oe
            }
            logger.debug(s"Got HistoricalOrders:\n${orders.toList.map(_.toString).mkString("\n")}")
        case DailyQuoteReturn(dQuotes) =>
            val n = dQuotes.size
            var dailyQuotes = if (n >= 10) dQuotes.drop(n - 10) else Nil
            // the day when low_price/high_price is smallest
            val d1 = dailyQuotes.foldLeft(("", Double.MaxValue))((b, dq) =>
                    if (dq.low_price.get / dq.high_price.get < b._2)
                        (dq.begins_at.get, dq.low_price.get / dq.high_price.get)
                    else (b._1, b._2)
            )._1
            // the day when low_price/high_price is biggest
            val d2 = dailyQuotes.foldLeft(("", Double.MinValue))((b, dq) =>
                    if (dq.low_price.get / dq.high_price.get > b._2)
                        (dq.begins_at.get, dq.low_price.get / dq.high_price.get)
                    else (b._1, b._2)
            )._1
            // remove the days where low_price/high_price is smallest or biggest
            dailyQuotes = dailyQuotes.filter(dq => !dq.begins_at.contains(d1) && !dq.begins_at.contains(d2))
            changeFromHigh = dailyQuotes.map(dq => dq.low_price.get / dq.high_price.get).sum / dailyQuotes.size
            changeFromLow  = dailyQuotes.map(dq => dq.high_price.get / dq.low_price.get).sum / dailyQuotes.size
            recentLowest = dailyQuotes.map(_.low_price.get).min
        case Tick => // Tick means there's a new web socket connection. purpose: send the symbol to the browser
            if (p.quantity.get >= 0) sendPosition else sendFundamental
            lastRoundOrdersHash = ""
            lastEstimateHash = ""
            lastPositionHash = ""
            lastFundamentalHash = ""
            lastQuoteHash = ""
        case "DEBUG" =>
            debug = true
            logger.debug(
                s"""isDow: $isDow, Fundamental: $fu, Position: $p, Quote: $q,
                   | gotHistoricalOrders: $gotHistoricalOrders, instrument: $instrument,
                   | lastTimeHistoricalOrdersRequested: ${new Date(lastTimeHistoricalOrdersRequested * 1000)},
                   | lastTimeHistoricalQuotesRequested: ${new Date(lastTimeHistoricalQuotesRequested * 1000)},
                   | lastTimeBuy: ${new Date(lastTimeBuy * 1000)}, lastTimeSell: ${new Date(lastTimeSell * 1000)},
                   | orders: ${orders.map(_.toString).mkString("\n")},
                   | estimatedLow: $estimatedLow, estimatedHigh: $estimatedHigh
                   | """.stripMargin)
    }
    override def receive: Receive = sideEffect andThen _receive
    private def sideEffect: PartialFunction[Any, Any] = { case x => ThreadContext.put("symbol", symbol); x }

    /**
      * @param tbOrders to-browser orders
      * @return including confirmed orders. TODO remove this todo after checking
      */
    private def assignMatchId(tbOrders: List[OrderElement]): List[OrderElement] = {
        @tailrec
        def f(withMatchIds: List[OrderElement], working: List[OrderElement]): List[OrderElement] = {
            var _withMatchIds = List[OrderElement]()
            var _working = List[OrderElement]()
            var i = 0
            var matchFound = false
            while (i < working.length - 1 && !matchFound) {
                if (doBuySellMatch(working(i), working(i + 1)).contains(true)) {
                    matchFound = true
                    val buySellT: (OrderElement, OrderElement) =
                        if (i + 2 < working.length && doBuySellMatch(working(i + 2), working(i + 1)).contains(true)) {
                            if (working(i).average_price.get < working(i + 2).average_price.get)
                                (working(i + 2), working(i + 1))
                            else (working(i), working(i+1))
                        }
                        else (working(i), working(i + 1))

                    val matchId = combineIds(buySellT._1.id.get, buySellT._2.id.get)
                    val buy = buySellT._1.copy(matchId = Some(matchId))
                    val sell = buySellT._2.copy(matchId = Some(matchId))
                    _withMatchIds = withMatchIds :+ buy :+ sell
                    _working = working.filter(oe => !oe.id.contains(buy.id.get) && !oe.id.contains(sell.id.get))
                }
                else if (doBuySellMatch(working(i + 1), working(i)).contains(true)) {
                    matchFound = true
                    val buySellT: (OrderElement, OrderElement) =
                        if (i + 2 < working.length && doBuySellMatch(working(i+1), working(i+2)).contains(true)) {
                            if (working(i).average_price.get < working(i+2).average_price.get) {
                                (working(i+1), working(i))
                            }
                            else (working(i+1), working(i + 2))
                        }
                        else (working(i + 1), working(i))

                    val matchId = combineIds(buySellT._1.id.get, buySellT._2.id.get)
                    val buy = buySellT._1.copy(matchId = Some(matchId))
                    val sell = buySellT._2.copy(matchId = Some(matchId))
                    _withMatchIds = withMatchIds :+ buy :+ sell
                    _working = working.filter(oe => !oe.id.contains(buy.id.get) && !oe.id.contains(sell.id.get))
                }
                i += 1
            }

            if (matchFound) f(_withMatchIds, _working)
            else {
                val set: collection.mutable.SortedSet[OrderElement] =
                    collection.mutable.SortedSet[OrderElement]()(Ordering.by[OrderElement, String](_.created_at)(Main.timestampOrdering.reverse))
                set ++= withMatchIds
                set ++= working
                set.toList
            }
        }
        f(List[OrderElement](), tbOrders)
    }

    private def combineIds(s1: String, s2: String): String = if (s1 < s2) s"$s1-$s2" else s"$s2-$s1"

    private def doBuySellMatch(o1: OrderElement, o2: OrderElement): Option[Boolean] = for {
        side1 <- o1.side
        side2 <- o2.side
        if side1 == "buy" && side2 == "sell"
        cumulative_quantity1 <- o1.cumulative_quantity
        cumulative_quantity2 <- o2.cumulative_quantity
        if cumulative_quantity1 == cumulative_quantity2
        average_price1 <- o1.average_price
        average_price2 <- o2.average_price
        if average_price1 < average_price2
    } yield true

/*
    private def findFilledBuyNonMatchedOrderBefore(created_at: String, oes: List[OrderElement]): Option[OrderElement] = {
        import java.time.Instant
        val instant = Instant.parse(created_at)
        oes.find()
    }
*/

    private def isAcceptableOrderState(state: String, oe: OrderElement): Boolean =
        state == "filled" || state.contains("confirmed") || (
                state == "cancelled" && oe.cumulative_quantity.exists(_ > 0)
        )

    /**
      * @param s in the form of 2018-06-04T22:00:03.783713Z or 2018-06-04T22:00:03Z or 2018-06-04T22:00:03
      */
    private def isToday(s: String): Boolean = s.startsWith(today)

    /**
      * @param s in the form of 2018-06-04T22:00:03.783713Z or 2018-06-04T22:00:03Z or 2018-06-04T22:00:03
      */
    private def isTodayAndMoreThanFromNow(s: String, T: Int = 19): Boolean = {
        if (s.startsWith(today)) {
            import java.time.Instant
            val a1 = s.split("[-T:Z]")
            val a2 = Instant.now().toString.split("[-T:Z]")
            a2(3).toInt * 60 + a2(4).toInt - a1(3).toInt * 60 - a1(4).toInt > T
        }
        else
            false
    }

    // please make sure position.quantity is defined before calling this
    private def lastRoundOrders(): List[OrderElement] = {
        @tailrec
        def f(givenSum: Int, currentSum: Int, building: List[OrderElement], remain: List[OrderElement]): List[OrderElement] = {
            if (givenSum == currentSum || remain == Nil) building
            else f(
                givenSum,
                currentSum + (
                        // confirmed orders have cumulative_quantity of 0
                        if (remain.head.side.contains("buy")) remain.head.cumulative_quantity.get
                        else -remain.head.cumulative_quantity.get
                ),
                building :+ remain.head,
                remain.tail
            )
        }

        // cpfOrders contains (un)confirmed and partially_filled orders
        val cpfOrders = orders.toList
                .takeWhile(_.state.exists(s => s.contains("confirmed") || s.contains("partially_filled")))
        val currentSum = cpfOrders.foldLeft(0)((n, oe) =>
            if (oe.state.exists(_.contains("partially_filled")))
                if (oe.side.contains("buy")) n + oe.cumulative_quantity.getOrElse(0)
                else n - oe.cumulative_quantity.getOrElse(0)
            else n
        )
        f(p.quantity.get, currentSum, cpfOrders, orders.toList)
    }

    private def sendEstimate() {
        if (fu.low.exists(_ > 0) && fu.high.exists(_ > 0) && changeFromHigh > 0 && changeFromLow > 0) {
            // now we can estimate the low and high prices for today
            if (q.last_trade_price.get - fu.low.get < fu.high.get - q.last_trade_price.get) { // closer to low
                estimatedLow = fu.high.get * changeFromHigh
                estimatedLow = if (estimatedLow < fu.low.get) estimatedLow else fu.low.get
                estimatedHigh = fu.high.get
            }
            else {
                estimatedLow = fu.low.get
                estimatedHigh = fu.low.get * changeFromLow
                estimatedHigh = if (estimatedHigh > fu.high.get) estimatedHigh else fu.high.get
            }
            val message = s"""$symbol: ESTIMATE: {"low":$estimatedLow,"high":$estimatedHigh}"""
            val estimateHash = new String(md5Digest.digest(message.getBytes))
            if (estimateHash != lastEstimateHash) {
                lastEstimateHash = estimateHash
                context.actorSelection(s"../../${WebSocketActor.NAME}") ! message
            }
        }
    }

    private def sendFundamental {
        val message = s"$symbol: FUNDAMENTAL: ${Fundamental.serialize(fu.copy(pe_ratio = None, average_volume = None, average_volume_2_weeks = None))}"
        val fundamentalHash = new String(md5Digest.digest(message.getBytes))
        if (fundamentalHash != lastFundamentalHash) {
            lastFundamentalHash = fundamentalHash
            context.actorSelection(s"../../${WebSocketActor.NAME}") ! message
        }
    }

    private def sendOrdersToBrowser(os: List[OrderElement]) {
        val message = s"$symbol: ORDERS: ${Serialization.write(os)(DefaultFormats)}"
        context.actorSelection(s"../../${WebSocketActor.NAME}") ! message
    }

    private def sendPosition {
        val message = s"$symbol: POSITION: ${Position.serialize(p.copy(created_at = None, updated_at = None))}"
        val positionHash = new String(md5Digest.digest(message.getBytes))
        if (positionHash != lastPositionHash) {
            lastPositionHash = positionHash
            context.actorSelection(s"../../${WebSocketActor.NAME}") ! message
        }
    }

    private def sendQuote {
        if (q.last_trade_price.isDefined && q.last_trade_price.get > 0) {
            val message = s"$symbol: QUOTE: ${Quote.serialize(q.copy(updated_at = None, ask_price = None,
                ask_size = None, bid_price = None, bid_size = None, adjusted_previous_close = None,
                previous_close = None, previous_close_date = None))}"
            val quoteHash = new String(md5Digest.digest(message.getBytes))
            if (quoteHash != lastQuoteHash) {
                lastQuoteHash = quoteHash
                context.actorSelection(s"../../${WebSocketActor.NAME}") ! message
            }
        }
    }

    private def shouldBuySell(
                                     oes: List[OrderElement],
                                     ltp: Double      /* last trade price */,
                                     _d: Boolean      /* true means debug */,
                                     T: Long = 60
                             ): Option[(String, Int, Double, OrderElement)] = oes match {
        // returns (action, quantity, price, orderElement used to give this decision)
        case Nil =>
            val N = None
            val now = System.currentTimeMillis / 1000
            val quantity = (10 / ltp + 1).round.toInt
            if (ltp < 1.01*recentLowest && ltp < .997*estimatedLow && fu.low.exists(ltp < 1.005*_) && (now - lastTimeBuy > T)) {
                val buyPrice = (ltp * 100).round.toDouble / 100
                logger.warn(s"Buy new $quantity $symbol at $buyPrice estimatedLow: $estimatedLow, fundamental low: ${fu.low.get}")
                Some(("buy", quantity, buyPrice, OrderElement("_", "_", N, N, N, N, "_", N, N, N, N, N)))
            }
            else
                None
        case _ =>
            if (_d) logger.debug(s"oes: ${oes.map(_.toString).mkString("\n")}")
            val hasBuy  = oes.exists(oe => oe.state.exists(_.contains("confirmed")) && oe.side.contains("buy"))
            val hasSell = oes.exists(oe => oe.state.exists(_.contains("confirmed")) && oe.side.contains("sell"))
            if (_d) logger.debug(s"hasBuy: $hasBuy, hasSell: $hasSell")

            val now = System.currentTimeMillis / 1000
            val decisionFunction: PartialFunction[OrderElement, (String, Int, Double, OrderElement)] = {
                case oe @ OrderElement(updated_at, _, _, _, Some(cumulative_quantity), _, _, _, Some(price), _, Some("buy"), _, _)
                    if !hasSell && isTodayAndMoreThanFromNow(updated_at) && (ltp > 1.007*price) && (now - lastTimeSell > T) =>
                    ("sell", cumulative_quantity, (ltp*100).round.toDouble / 100, oe)
                case oe @ OrderElement(_, created_at, _, _, Some(cumulative_quantity), _, _, _, Some(price), _, Some("buy"), _, _)
                    if !hasSell && !isToday(created_at) && (ltp > 1.01*price) && fu.high.exists(ltp > .992*_) && (now - lastTimeSell > T)  =>
                    ("sell", cumulative_quantity, (ltp*100).round.toDouble / 100, oe)
                case oe @ OrderElement(updated_at, _, _, _, Some(cumulative_quantity), _, _, _, Some(price), _, Some("sell"), _, _)
                    if !hasBuy && isTodayAndMoreThanFromNow(updated_at) && (ltp < .993*price) && (now - lastTimeBuy > T) =>
                    ("buy", cumulative_quantity, (ltp*100).round.toDouble / 100, oe)
                case oe @ OrderElement(_, created_at, _, _, Some(cumulative_quantity), _, _, _, Some(price), _, Some("sell"), _, _)
                    if !hasBuy && !isToday(created_at) && (ltp < .99*price) && fu.low.exists(ltp < 1.008*_) && (now - lastTimeBuy > T) =>
                    ("buy", cumulative_quantity, (ltp*100).round.toDouble / 100, oe)
                case oe @ OrderElement(updated_at, _, _, _, Some(cumulative_quantity), _, _, _, Some(price), _, Some("buy"), _, None)
                    if !hasBuy && isTodayAndMoreThanFromNow(updated_at) && (ltp < .991*price) && (now - lastTimeBuy > T) =>
                    ("buy", cumulative_quantity + 1, (ltp*100).round.toDouble / 100, oe)
                case oe @ OrderElement(_, created_at, _, _, Some(cumulative_quantity), _, _, _, Some(price), _, Some("buy"), _, None)
                    if !hasBuy && !isToday(created_at) && (ltp < .985*price) && fu.low.exists(ltp < 1.005*_) && (ltp < estimatedLow) && (now - lastTimeBuy > T) =>
                    ("buy", cumulative_quantity + 1, (ltp*100).round.toDouble / 100, oe)
            }
            val filledOEs = oes.filter(_.state.contains("filled"))
            val decision1: Option[(String, Int, Double, OrderElement)] = filledOEs.headOption collect decisionFunction
            if (_d) logger.debug(s"decision1: $decision1")
            val decision2 = filledOEs.dropWhile(_.matchId.isDefined).headOption collect decisionFunction
            if (_d) logger.debug(s"decision2: $decision2")
            if (decision1.isEmpty) decision2
            else if (decision2.isEmpty) decision1
            else for {
                d1 <- decision1
                d2 <- decision2
            } yield if (d1._1 == "sell") d1 else d2
    }

    private def shouldDoBuySell: Boolean = {
        val now = LocalTime.now
        val hour = now.getHour
        val minute = now.getMinute
        isWeekDay && (
                (hour > 9 && hour < 16) || (hour == 9 && minute >= 30)
        )
    }
}
