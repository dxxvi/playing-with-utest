package home.sparkjava

import java.security.MessageDigest
import java.time.DayOfWeek._
import java.time.{LocalDate, LocalTime}
import java.time.format.DateTimeFormatter

import akka.actor.{Actor, Props, Timers}
import akka.event.{LogSource, Logging, LoggingAdapter}
import com.typesafe.config.Config
import org.json4s._
import org.json4s.native.Serialization
import message.Tick
import model._

import scala.annotation.tailrec
import scala.math._

object StockActor {
    case class QuoteOpenHighLow(q: Quote, open: Double, high: Double, low: Double)

    def props(
                 symbol: String,
                 config: Config,
                 historicalOrders: List[OrderElement],
                 q: Quote,
                 dQuotes: List[DailyQuote],
                 p: Position,
                 todayOpen: Double,
                 todayHigh: Double,
                 todayLow: Double
             ): Props =
        Props(new StockActor(symbol, config, historicalOrders, q, dQuotes, p, todayOpen, todayHigh, todayLow))
}

class StockActor(
                    symbol: String,
                    config: Config,
                    historicalOrders: List[OrderElement],
                    var q: Quote,
                    dQuotes: List[DailyQuote],
                    var p: Position,
                    var todayOpen: Double,
                    var todayHigh: Double,
                    var todayLow: Double
                ) extends Actor with Util with Timers {
    import StockActor._

    implicit val logSource: LogSource[AnyRef] = (_: AnyRef) => symbol
    val log: LoggingAdapter = Logging(context.system, this)
    val isWeekDay: Boolean = ! Seq(SATURDAY, SUNDAY).contains(LocalDate.now.getDayOfWeek)
    val md5Digest: MessageDigest = MessageDigest.getInstance("MD5")
    val isDow: Boolean = isDow(symbol)

    var lastRoundOrdersHash = ""
    var lastEstimateHash = ""
    var lastPositionHash = ""
    var lastFundamentalHash = ""
    var lastQuoteHash = ""
    var lastCurrentStatusHash = ""
    var hlSentToBrowser = false  // HL49, HL31, HL10, HO49, HO10, OL49, OL10, CL49 sent to browser

    var instrument = ""
    var lastTimeBuySell: Long = 0 // in seconds
    val today: String = LocalDate.now.format(DateTimeFormatter.ISO_LOCAL_DATE)

    val orders: collection.mutable.SortedSet[OrderElement] =
        collection.mutable.SortedSet[OrderElement]()(Ordering.by[OrderElement, String](_.created_at)(Main.timestampOrdering.reverse))
    orders ++= historicalOrders.collect {
        case oe @ OrderElement(_, _, _, _, cumulative_quantity, _, _, state, _, _, _, _, _) if isAcceptableOrderState(state, oe) =>
            if (state == "cancelled") oe.copy(state = "filled", quantity = cumulative_quantity) else oe
    }

    var HL49: Double = Double.NaN
    var HL31: Double = Double.NaN
    var HL10: Double = Double.NaN
    var HO49: Double = Double.NaN
    var HO10: Double = Double.NaN
    var OL49: Double = Double.NaN
    var OL10: Double = Double.NaN
    var CL49: Double = Double.NaN
    var L3:   Double = Double.NaN      // the 3rd lowest
    var HL: List[DailyQuote] = Nil
    var HO: List[DailyQuote] = Nil
    var OL: List[DailyQuote] = Nil
    var L: List[DailyQuote] = Nil
    // HPC (todayHigh - previousClose) and PCL (previousClose - todayLow) sorted from lowest - highest
    var HPC: List[(String /* beginsAt */, Double /* today_highest - previous_close */)] = Nil
    var PCL: List[(String /* beginsAt */, Double /* previous_close - today_lowest */)]  = Nil
    setStats()

    var thresholdBuy: Double = -1
    var thresholdSell: Double = -1

    override def receive: Receive = {
        case _o: OrderElement =>
            val o = if (_o.state == "cancelled" && _o.cumulative_quantity.exists(_ > 0))
                _o.copy(state = "filled", quantity = _o.cumulative_quantity)
/*
            // TODO this is for testing on weekends
            else if (_o.state.contains("queued")) _o.copy(state = Some("confirmed"))
*/
            else _o
            orders -= o
            if (o.state == "filled" || o.state =="confirmed") orders += o
            // orders sent to browser when StockActor receives a Position which is every 4 secs

        case _p: Position =>   // we receive this every 4 or 30 seconds
            p = _p
            sendPosition
            if (p.instrument.nonEmpty) instrument = p.instrument

            if (orders.nonEmpty) {
                var totalShares: Int = 0
                val x = lastRoundOrders() // x has (un)confirmed, (partially) filled orders
                val _lastRoundOrders = assignMatchId(x)
                if (instrument != "" && thresholdBuy > 0 && thresholdSell > 0 && shouldDoBuySell) {
                    shouldBuySell(_lastRoundOrders, q.last_trade_price) foreach { t =>
                        // (action, quantity, price, orderElement, reason)
                        context.actorSelection(s"../../${OrderActor.NAME}") ! OrderActor.BuySell(t._1, symbol, instrument, t._2, t._3)
                        lastTimeBuySell = System.currentTimeMillis / 1000
                        log.warning(s"Just ${t._1.toUpperCase} ${t._2} $symbol $$${t._3} " +
                                s"${t._4.copy(instrument = "~")} ${t._5}")
                    }
                }
                val lastRoundOrdersString = _lastRoundOrders.map(oe => {
                    val cq = oe.cumulative_quantity.get
                    totalShares += (if (oe.side == "buy") cq else -cq)
                    s"${oe.toString}  $totalShares"
                }).mkString("\n")
                val newHash = new String(MessageDigest.getInstance("MD5").digest(lastRoundOrdersString.getBytes))
                if (newHash != lastRoundOrdersHash) {
                    log.debug(s"Last round orders before assigning matchId:\n${x.map(_.toString).mkString("\n")}")
                    lastRoundOrdersHash = newHash
                    log.debug(s"Orders sent to browser: position ${p.quantity}\n$lastRoundOrdersString")
                    sendOrdersToBrowser(_lastRoundOrders)
                }
            }
        case QuoteOpenHighLow(_q, open, high, low) => // the QuoteActor is sure that symbol, last_trade_price and instrument are there
            val T = 19
            q = _q
            if (!open.isNaN) todayOpen = open
            if (!high.isNaN) todayHigh = high
            if (!low.isNaN)  todayLow  = low
            sendQuote
            instrument = q.instrument

            val now = System.currentTimeMillis/1000
            if (!hlSentToBrowser) {
                hlSentToBrowser = true
                val map: Map[String, Double] = Map("HL49" -> HL49, "HL31" -> HL31, "HL10" -> HL10, "HO49" -> HO49,
                    "HO10" -> HO10, "OL49" -> OL49, "OL10" -> OL10, "CL49" -> CL49)
                val message = s"$symbol: HISTORICAL_QUOTES: ${Serialization.write(map)(DefaultFormats)}"
                context.actorSelection(s"../../${WebSocketActor.NAME}") ! message
            }
            val map = collection.mutable.Map[String, String]()
            map += ("HCurrent" -> ("HL" + HL.takeWhile(dq => dq.high_price - dq.low_price < todayHigh - q.last_trade_price).size))
            map += ("CurrentL" -> ("HL" + HL.takeWhile(dq => dq.high_price - dq.low_price < q.last_trade_price - todayLow).size))
            if (q.last_trade_price > todayOpen)
                map += ("CurrentO" -> ("HO" + HO.takeWhile(dq => dq.high_price - dq.open_price < q.last_trade_price - todayOpen).size))
            else
                map += ("OCurrent" -> ("OL" + OL.takeWhile(dq => dq.open_price - dq.low_price < todayOpen - q.last_trade_price).size))
            val message = s"$symbol: CURRENT_STATUS: ${Serialization.write(map)(DefaultFormats)}"
            val currentStatusHash = new String(md5Digest.digest(message.getBytes))
            if (lastCurrentStatusHash != currentStatusHash) {
                context.actorSelection(s"/user/${WebSocketActor.NAME}") ! message
                lastCurrentStatusHash = currentStatusHash
            }

        case Tick => // Tick means there's a new web socket connection. purpose: send the symbol to the browser
            if (p.quantity >= 0) sendPosition else sendFundamental
            lastRoundOrdersHash = ""
            lastEstimateHash = ""
            lastPositionHash = ""
            lastFundamentalHash = ""
            lastQuoteHash = ""
            lastCurrentStatusHash = ""
            hlSentToBrowser = false
        case "DEBUG" =>
            val map = debug()
            sender() ! map
    }

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

                    val matchId = combineIds(buySellT._1.id, buySellT._2.id)
                    val buy = buySellT._1.copy(matchId = Some(matchId))
                    val sell = buySellT._2.copy(matchId = Some(matchId))
                    _withMatchIds = withMatchIds :+ buy :+ sell
                    _working = working.filter(oe => !oe.id.contains(buy.id) && !oe.id.contains(sell.id))
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

                    val matchId = combineIds(buySellT._1.id, buySellT._2.id)
                    val buy = buySellT._1.copy(matchId = Some(matchId))
                    val sell = buySellT._2.copy(matchId = Some(matchId))
                    _withMatchIds = withMatchIds :+ buy :+ sell
                    _working = working.filter(oe => !oe.id.contains(buy.id) && !oe.id.contains(sell.id))
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

    private def debug(): Map[String, String] = {
        log.info(
            s"""
              |  ltp -> ${q.last_trade_price}
              |  HL49 -> $HL49
              |  orders -> orders.map(Orders.serialize).mkString("\n")
            """.stripMargin)
        Map(
            "ltp" -> q.last_trade_price.toString,
            "HL49" -> HL49.toString,
            "orders" -> orders.map(Orders.serialize).mkString("\n")
        )
    }

    private def doBuySellMatch(o1: OrderElement, o2: OrderElement): Option[Boolean] = for {
        cumulative_quantity1 <- o1.cumulative_quantity
        cumulative_quantity2 <- o2.cumulative_quantity
        if cumulative_quantity1 == cumulative_quantity2
        if o1.side == "buy" && o2.side == "sell" && o1.state == "filled" && o2.state == "filled"
        average_price1 <- o1.average_price
        average_price2 <- o2.average_price
        if average_price1 < average_price2
    } yield true

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
                .takeWhile(o => o.state == "confirmed" || o.state == "partially_filled")
        val currentSum = cpfOrders.foldLeft(0)((n, oe) =>
            if (oe.state == "partially_filled")
                if (oe.side == "buy") n + oe.cumulative_quantity.getOrElse(0)
                else n - oe.cumulative_quantity.getOrElse(0)
            else n
        )
        f(p.quantity, currentSum, cpfOrders, orders.toList)
    }

    private def readThresholdBuySell() {
        thresholdBuy = if (config.hasPath(s"threshold.$symbol.buy")) config.getDouble(s"threshold.$symbol.buy") else 999
        thresholdSell = if (config.hasPath(s"threshold.$symbol.sell")) config.getDouble(s"threshold.$symbol.sell") else 1
    }

    private def sendFundamental {
/* TODO
        val sentFu = fu.copy(pe_ratio = None, average_volume = None, average_volume_2_weeks = None,
            thresholdBuy = thresholdBuy, thresholdSell = thresholdSell)
        val message = s"$symbol: FUNDAMENTAL: ${Fundamental.serialize(sentFu)}"
        val fundamentalHash = new String(md5Digest.digest(message.getBytes))
        if (fundamentalHash != lastFundamentalHash) {
            lastFundamentalHash = fundamentalHash
            context.actorSelection(s"../../${WebSocketActor.NAME}") ! message
        }
*/
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
        val message = s"$symbol: QUOTE: ${Quote.serialize(q.copy(updated_at = None, ask_price = None,
            ask_size = None, bid_price = None, bid_size = None, adjusted_previous_close = None,
            previous_close = Double.NaN, previous_close_date = None))}"
        val quoteHash = new String(md5Digest.digest(message.getBytes))
        if (quoteHash != lastQuoteHash) {
            lastQuoteHash = quoteHash
            context.actorSelection(s"../../${WebSocketActor.NAME}") ! message
        }
    }

    private def shouldBuySell(
                                 oes: List[OrderElement],
                                 ltp: Double     /* last trade price */,
                                 T: Long = 99    /* the amount of seconds we need to wait since last time buy/sell*/
                             ): Option[(String, Int, Double, OrderElement, String)] = oes match {
        // returns (action, quantity, price, orderElement used to give this decision)
        case Nil =>
            val N = None
            val now = System.currentTimeMillis / 1000
            val quantity = (10 / ltp + 1).round.toInt
            val cond1 = ltp <= todayHigh - HL49
            val cond2 = ltp <= todayOpen - OL49
            val cond3 = ltp < L3
            if ((cond1 || cond2) && cond3 && ltp < thresholdBuy && now - lastTimeBuySell > T) {
                val newBuyReason =
                    if (cond1) "New buy: HL49 reached" else if (cond2) "New buy: OL49 reach" else "shouldn't be seen"
                val buyPrice = (ltp * 100).round.toDouble / 100
                Some(("buy", quantity, buyPrice, OrderElement("_", "_", N, "~", N, N, "~", "~", N, N, "~", N), newBuyReason))
            }
            else
                None
        case _ =>
            val hasBuy  = oes.exists(oe => oe.state == "confirmed" && oe.side == "buy")
            val hasSell = oes.exists(oe => oe.state == "confirmed" && oe.side == "sell")

            val decisionFunction: PartialFunction[OrderElement, (String, Int, Double, OrderElement, String)] = {
                case oe @ OrderElement(updated_at, _, _, _, Some(cumulative_quantity), _, _, _, Some(price), _, "buy", _, _)
                    if !hasSell && isTodayAndMoreThanFromNow(updated_at) && shouldSellForTodayBuy(ltp, price, T).isDefined =>
                    ("sell", cumulative_quantity, (ltp*100).round.toDouble / 100, oe, shouldSellForTodayBuy(ltp, price, T).get)
                case oe @ OrderElement(_, created_at, _, _, Some(cumulative_quantity), _, _, _, Some(price), _, "buy", _, _)
                    if !hasSell && !isToday(created_at) && shouldSellForPastBuy(ltp, price, T).isDefined  =>
                    ("sell", cumulative_quantity, (ltp*100).round.toDouble / 100, oe, shouldSellForPastBuy(ltp, price, T).get)
                case oe @ OrderElement(updated_at, _, _, _, Some(cumulative_quantity), _, _, _, Some(price), _, "sell", _, _)
                    if !hasBuy && isTodayAndMoreThanFromNow(updated_at) && shouldBuyTodaySell(ltp, price, T).isDefined =>
                    ("buy", cumulative_quantity, (ltp*100).round.toDouble / 100, oe, shouldBuyTodaySell(ltp, price, T).get)
                case oe @ OrderElement(_, created_at, _, _, Some(cumulative_quantity), _, _, _, Some(price), _, "sell", _, _)
                    if !hasBuy && !isToday(created_at) && shouldBuyPastSell(ltp, price, T).isDefined =>
                    ("buy", cumulative_quantity, (ltp*100).round.toDouble / 100, oe, shouldBuyPastSell(ltp, price, T).get)
                case oe @ OrderElement(updated_at, _, _, _, Some(cumulative_quantity), _, _, _, Some(price), _, "buy", _, None)
                    if !hasBuy && isTodayAndMoreThanFromNow(updated_at) && shouldBuyMoreToday(ltp, price, T).isDefined =>
                    ("buy", cumulative_quantity + 1, (ltp*100).round.toDouble / 100, oe, shouldBuyMoreToday(ltp, price, T).get)
                case oe @ OrderElement(_, created_at, _, _, Some(cumulative_quantity), _, _, _, Some(price), _, "buy", _, None)
                    if !hasBuy && !isToday(created_at) && shouldBuyMoreForPast(ltp, price, T).isDefined =>
                    ("buy", cumulative_quantity + 1, (ltp*100).round.toDouble / 100, oe, shouldBuyMoreForPast(ltp, price, T).get)
            }
            val filledOEs = oes.filter(_.state.contains("filled"))
            val decision1 = filledOEs.headOption collect decisionFunction
            val decision2 = filledOEs.dropWhile(_.matchId.isDefined).headOption collect decisionFunction

            val decision = if (decision1.isEmpty) decision2
                    else if (decision2.isEmpty) decision1
                    else for {
                        d1 <- decision1
                        d2 <- decision2
                    } yield if (d1._1 == "sell") d1 else d2
            if (decision.nonEmpty && decision.get._1 == "buy" &&
                    filledOEs.nonEmpty && filledOEs.head.side.contains("sell") &&
                    filledOEs.head.price.exists(_ < decision.get._3 + .05))
                None
            else decision
    }

    private def shouldBuyPastSell(ltp: Double, sellPrice: Double, T: Long): Option[String] = {
        val todayDelta = todayHigh - todayLow
        val cond1 = ltp <= todayHigh - HL49 && ltp <= sellPrice - max(.05, todayDelta/9)
        val cond2 = Main.dowFuture > 100 && ltp < sellPrice - HL31 && (ltp < todayHigh - HL10 || ltp < todayOpen - OL10)
        if (ltp < thresholdBuy && (System.currentTimeMillis/1000 - lastTimeBuySell > T) && (cond1 || cond2))
            if (cond1) Some(s"Buy past sell: < todayHighest - HL49 and < sellPrice - todayDelta/9 (${todayDelta/9})")
            else Some(s"Buy past sell: dowFuture > 100 and < sellPrice - HL31 and (< todayHighest - HL10 or openPrice - OL10)")
        else None
    }

    private def shouldBuyTodaySell(ltp: Double, sellPrice: Double, T: Long): Option[String] = {
        val todayDelta = todayHigh - todayLow
        if (ltp < todayLow + todayDelta/4 && ltp <= sellPrice - todayDelta/5 &&
                (ltp < thresholdBuy) && (System.currentTimeMillis/1000 - lastTimeBuySell > T))
            Some(s"Buy today sell: in the lowest quarter and < sellPrice - todayDelta/5")
        else None
    }

    private def shouldBuyMoreForPast(ltp: Double, buyPrice: Double, T: Long): Option[String] = {
        val todayDelta = todayHigh - todayLow
        val cond1 = ltp <= todayHigh - HL49 && ltp <= buyPrice - todayDelta/4
        val cond2 = ltp <= todayOpen - OL49 && ltp <= buyPrice - todayDelta/4
        val cond3 = !HL31.isNaN && ltp < buyPrice - HL31 && ltp < todayHigh - HL31
        if (ltp < thresholdBuy && (System.currentTimeMillis / 1000 - lastTimeBuySell > T) && (cond1 || cond2 || cond3))
            if (cond1) Some(s"Buy more for past: < todayHighest - HL49 and < buyPrice - delta/4")
            else if (cond2) Some(s"Buy more for past: < openPrice - OL49 and < buyPrice - delta/4")
            else Some(s"Buy more for past: < buyPrice - HL31 and < todayHighest - HL31")
        else None
    }

    private def shouldBuyMoreToday(ltp: Double, buyPrice: Double, T: Long): Option[String] =
        if (!HL49.isNaN && ltp <= buyPrice - max(.05, HL49/5) &&
                (System.currentTimeMillis/1000 - lastTimeBuySell > T) && ltp < thresholdBuy)
            Some(s"Buy again today: < buyPrice - HL49/5")
        else None

    // should do buy/sell only when in open hours and already estimated low and high prices
    private def shouldDoBuySell: Boolean = {
        val now = LocalTime.now
        val hour = now.getHour
        val minute = now.getMinute
        isWeekDay && (
                (hour > 9 && hour < 16) || (hour == 9 && minute > 30)
        )
    }

    private def shouldSellForPastBuy(ltp: Double, buyPrice: Double, T: Long): Option[String] = {
        val todayDelta = todayHigh - todayLow
        val cond1 = ltp >= todayOpen + HO49 && ltp > buyPrice + max(.05, todayDelta/15)
        val cond2 = ltp >= todayLow + HL49 && ltp > buyPrice + max(.05, todayDelta/15)
        val cond3_1 = ltp > todayOpen + HO10
        val cond3_2 = ltp > todayLow + HL10
        val cond3 = ltp > buyPrice + HL31 && (cond3_1 || cond3_2)
        if ((cond1 || cond2 || cond3) && (System.currentTimeMillis / 1000 - lastTimeBuySell > T) && ltp > thresholdSell)
            if (cond1) Some(s"Sell past buy: HO49 reached")
            else if (cond2) Some(s"Sell past buy: HL49 reached")
            else if (cond3_1) Some(s"Sell past buy: > openPrice + HO10 and > buyPrice + HL31")
            else Some(s"Sell past buy: > lowestPrice + HL10 and > buyPrice + HL31")
        else None
    }

    private def shouldSellForTodayBuy(ltp: Double, buyPrice: Double, T: Long): Option[String] = {
        val hour = LocalTime.now.getHour
        val todayDelta = todayHigh - todayLow
        val cond1 = hour < 15 && ltp >= buyPrice + max(.05, todayDelta/15) &&
                ltp > todayLow + todayDelta/4
        val cond2 = hour == 15 && ltp >= buyPrice + max(.05, todayDelta/15) &&
                ltp > todayLow + CL49
        if ((cond1 || cond2) && (System.currentTimeMillis / 1000 - lastTimeBuySell > T) && (ltp > thresholdSell)) {
            if (cond1) Some(s"Sell today buy: before 3pm and > todayDelta/15 (${todayDelta/15})")
            else Some(s"Sell today buy: after 3pm and > todayDelta/15 and > todayLowest($todayLow) + CL49($CL49)")
        }
        else None
    }

    private def setStats() {
        val N = 62
        val dailyQuotes: List[DailyQuote] = dQuotes.reverse.take(N)

        HL = dailyQuotes.sortWith((dq1, dq2) => dq1.high_price - dq1.low_price < dq2.high_price - dq2.low_price)
        HL49 = f"${HL(49).high_price - HL(49).low_price}%4.4f".toDouble
        HL31 = f"${HL(31).high_price - HL(31).low_price}%4.4f".toDouble
        HL10 = f"${HL(10).high_price - HL(10).low_price}%4.4f".toDouble
        HO = dailyQuotes.sortWith((dq1, dq2) => dq1.high_price - dq1.open_price < dq2.high_price - dq2.open_price)
        HO49 = f"${HO(49).high_price - HO(49).open_price}%4.4f".toDouble
        HO10 = f"${HO(10).high_price - HO(10).open_price}%4.4f".toDouble
        OL = dailyQuotes.sortWith((dq1, dq2) => dq1.open_price - dq1.low_price < dq2.open_price - dq2.low_price)
        OL49 = f"${OL(49).open_price - OL(49).low_price}%4.4f".toDouble
        OL10 = f"${OL(10).open_price - OL(10).low_price}%4.4f".toDouble
        val CL = dailyQuotes.sortWith((dq1, dq2) => dq1.close_price - dq1.low_price < dq2.close_price - dq2.low_price)
        CL49 = f"${CL(49).close_price - CL(49).low_price}%4.4f".toDouble
        L = dQuotes.reverse.take(10).sortWith((dq1, dq2) => dq1.low_price < dq2.low_price)
        L3 = L(2).low_price

        var temp: Double = 0
        val M = 90                     // calculate HPC, PCL for the last M days only
        HPC = dQuotes
                .map(dq => {
                    val previousClose = temp
                    temp = dq.close_price
                    (dq.begins_at, f"${dq.high_price - previousClose}%4.4f".toDouble)
                })
                .reverse
                .take(M)
                .sortWith(_._2 < _._2)
        PCL = dQuotes
                .map(dq => {
                    val previousClose = temp
                    temp = dq.close_price
                    (dq.begins_at, f"${previousClose - dq.low_price}%4.4f".toDouble)
                })
                .reverse
                .take(M)
                .sortWith(_._2 < _._2)
    }

    readThresholdBuySell()
}
