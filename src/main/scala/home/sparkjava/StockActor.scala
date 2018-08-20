package home.sparkjava

import java.security.MessageDigest

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

    var instrument = ""
    var lastTimeHistoricalOrdersRequested: Long = 0 // in seconds
    var lastTimeHistoricalQuotesRequested: Long = 0
    val orders: collection.mutable.SortedSet[OrderElement] =
        collection.mutable.SortedSet[OrderElement]()(Ordering.by[OrderElement, String](_.created_at.get)(Main.timestampOrdering.reverse))
    var changeFromHigh: Double = 0
    var changeFromLow: Double = 0

    val _receive: Receive = {
        case _fu: Fundamental => if ((_fu.low.isDefined && _fu.high.isDefined) || _fu.open.isDefined) {
            fu = _fu
            sendFundamental
            instrument = fu.instrument
        }
        case o: OrderElement =>
            orders += o
            // orders sent to browser when StockActor receives a Position which is every 4 secs
        case _p: Position => if (_p.quantity.isDefined) {
            p = _p
            sendPosition
            if (p.instrument.nonEmpty) instrument = p.instrument.get

            if (p.quantity.exists(_ > 0) && orders.nonEmpty) {
                var totalShares: Int = 0
                val _lastRoundOrders = assignMatchId(lastRoundOrders())
                val lastRoundOrdersString = _lastRoundOrders.map(oe => {
                    val cq = oe.cumulative_quantity.get
                    totalShares += (if (oe.side.get == "buy") cq else -cq)
                    s"${oe.toString}  $totalShares"
                }).mkString("\n")
                val s = new String(MessageDigest.getInstance("MD5").digest(lastRoundOrdersString.getBytes))
                if (s != lastRoundOrdersHash) {
                    lastRoundOrdersHash = s
                    logger.debug(s"Orders sent to browser: position ${p.quantity}\n$lastRoundOrdersString")
                    println(s"                               ...... $symbol.log matched orders ...")
                    sendOrdersToBrowser(_lastRoundOrders)
                }
            }
        }
        case _q: Quote => // the QuoteActor is sure that symbol, last_trade_price and instrument are there
            q = _q
            sendQuote
            instrument = q.instrument.get
            val now = System.currentTimeMillis / 1000
            if (p.quantity.exists(_ > 0) && orders.isEmpty && (now - lastTimeHistoricalOrdersRequested > 15)) {
                // we should wait for the OrderActor a bit because we receive quote every 4 seconds
                lastTimeHistoricalOrdersRequested = now
                context.actorSelection(s"../../${OrderActor.NAME}") !
                        HistoricalOrders(symbol, instrument, 4, Seq[OrderElement](), None)
            }
            if (changeFromHigh == 0 && changeFromLow == 0 && (now - lastTimeHistoricalQuotesRequested > 15)) {
                lastTimeHistoricalQuotesRequested = now
                context.actorSelection(s"../../${QuoteActor.NAME}") ! GetDailyQuote(List(symbol), 0)
            }
            sendEstimate()
        case HistoricalOrders(_, _, _, _orders, _) =>
            orders ++= _orders.filter(oe => oe.state.isDefined && (oe.state.get == "filled" || oe.state.get.contains("confirmed")))
            println(s"... $symbol.log historicals orders ...")
            logger.debug(s"Got HistoricalOrders:\n${orders.toList.map(_.toString).mkString("\n")}")
        case DailyQuoteReturn(dQuotes) =>
            val n = dQuotes.size
            var dailyQuotes = if (n >= 10) dQuotes.drop(n - 10) else Nil
            // the day low_price/high_price smallest
            val d1 = dailyQuotes.foldLeft(("", Double.MaxValue))((b, dq) =>
                    if (dq.low_price.get / dq.high_price.get < b._2)
                        (dq.begins_at.get, dq.low_price.get / dq.high_price.get)
                    else (b._1, b._2)
            )._1
            // the day low_price/high_price biggest
            val d2 = dailyQuotes.foldLeft(("", Double.MinValue))((b, dq) =>
                    if (dq.low_price.get / dq.high_price.get > b._2)
                        (dq.begins_at.get, dq.low_price.get / dq.high_price.get)
                    else (b._1, b._2)
            )._1
            // remove the days where low_price/high_price is smallest or biggest
            dailyQuotes = dailyQuotes.filter(dq => !dq.begins_at.contains(d1) && !dq.begins_at.contains(d2))
            changeFromHigh = dailyQuotes.map(dq => dq.low_price.get / dq.high_price.get).sum / dailyQuotes.size
            changeFromLow  = dailyQuotes.map(dq => dq.high_price.get / dq.low_price.get).sum / dailyQuotes.size
        case Tick => // purpose: send the symbol to the browser
            if (p.quantity.get >= 0) sendPosition else sendFundamental
            lastRoundOrdersHash = ""
            lastEstimateHash = ""
            lastPositionHash = ""
            lastFundamentalHash = ""
            lastQuoteHash = ""
    }
    override def receive: Receive = sideEffect andThen _receive
    private def sideEffect: PartialFunction[Any, Any] = { case x => ThreadContext.put("symbol", symbol); x }

    /**
      * @param tbOrders to-browser orders
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
                    collection.mutable.SortedSet[OrderElement]()(Ordering.by[OrderElement, String](_.created_at.get)(Main.timestampOrdering.reverse))
                set ++= withMatchIds
                set ++= working
                set.toList
            }
        }
        f(List[OrderElement](), tbOrders)
    }

    private def doBuySellMatch(o1: OrderElement, o2: OrderElement): Option[Boolean] = for {
        side1 <- o1.side
        side2 <- o2.side
        if side1 == "buy" && side2 == "sell"
        quantity1 <- o1.quantity
        quantity2 <- o2.quantity
        if quantity1 == quantity2
        average_price1 <- o1.average_price
        average_price2 <- o2.average_price
        if average_price1 < average_price2
    } yield true

    private def combineIds(s1: String, s2: String): String = if (s1 < s2) s"$s1-$s2" else s"$s2-$s1"

    // please make sure position.quantity is defined before calling this
    private def lastRoundOrders(): List[OrderElement] = {
        @tailrec
        def f(givenSum: Int, currentSum: Int, building: List[OrderElement], remain: List[OrderElement]): List[OrderElement] = {
            if (givenSum == currentSum || remain == Nil) building
            else f(
                givenSum,
                currentSum + (if (remain.head.side.contains("buy")) remain.head.quantity.get else -remain.head.quantity.get),
                building :+ remain.head,
                remain.tail
            )
        }

        f(p.quantity.get, 0, Nil, orders.toList)
    }

    private def sendEstimate() {
        if (fu.low.exists(_ > 0) && fu.high.exists(_ > 0) && changeFromHigh > 0 && changeFromLow > 0) {
            // now we can estimate the low and high prices for today
            val message = if (q.last_trade_price.get - fu.low.get < fu.high.get - q.last_trade_price.get)  // closer to low
                s"""$symbol: ESTIMATE: {"low":${fu.high.get * changeFromHigh},"high":${fu.high.get}}"""
            else
                s"""$symbol: ESTIMATE: {"low":${fu.low.get},"high":${fu.low.get * changeFromLow}}"""
            val estimateHash = new String(md5Digest.digest(message.getBytes))
            if (estimateHash != lastEstimateHash) {
                lastEstimateHash = estimateHash
                context.actorSelection(s"../../${WebSocketActor.NAME}") ! message
            }
        }
    }

    private def sendFundamental {
        val message = s"$symbol: FUNDAMENTAL: ${Fundamental.serialize(fu.copy(pe_ratio = None))}"
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
            val message = s"$symbol: QUOTE: ${Quote.serialize(q.copy(updated_at = None))}"
            val quoteHash = new String(md5Digest.digest(message.getBytes))
            if (quoteHash != lastQuoteHash) {
                lastQuoteHash = quoteHash
                context.actorSelection(s"../../${WebSocketActor.NAME}") ! message
            }
        }
    }
}
