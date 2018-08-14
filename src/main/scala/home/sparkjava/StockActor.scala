package home.sparkjava

import java.security.MessageDigest

import akka.actor.{Actor, Props}
import org.apache.logging.log4j.ThreadContext
import org.json4s._
import org.json4s.native.Serialization
import message.{HistoricalOrders, Tick}
import model.{Fundamental, OrderElement, Position, Quote}

import scala.annotation.tailrec

object StockActor {
    def props(symbol: String): Props = Props(new StockActor(symbol))
}

class StockActor(symbol: String) extends Actor with Util {
    val isDow: Boolean = isDow(symbol)
    var fu = new Fundamental(
        Some(-.1), Some(-.1), Some(""), Some(-.1), Some(-.1), Some(-.1), Some(-.1), Some(-.1), Some(-.1), Some(-.1), ""
    )
    var p = new Position(
        Some(-.1), Some(-.1), Some(-.1), Some(-.1), Some(-.1), None, None, Some(-.1), Some(-.1), None, Some(-.1),
        Some(-.1), Some(-.1), Some(-.1)
    )
    var q = new Quote(
        None, None, None, None, None, None, None, None, None, None, None, None, None, None, None
    )

    var lastRoundOrdersHash = ""
    var instrument = ""
    var lastTimeHistoricalOrdersRequested: Long = 0 // in seconds
    val orders: collection.mutable.SortedSet[OrderElement] =
        collection.mutable.SortedSet[OrderElement]()(Ordering.by[OrderElement, String](_.created_at.get)(Main.timestampOrdering.reverse))

    val _receive: Receive = {
        case _fu: Fundamental => if ((_fu.low.isDefined && _fu.high.isDefined) || _fu.open.isDefined) {
            fu = _fu
            sendFundamental
            instrument = fu.instrument
        }
        case _p: Position => if (_p.quantity.isDefined) {
            p = _p
            sendPosition
            if (p.instrument.nonEmpty) instrument = p.instrument.get
            if (p.quantity.exists(_ > 0) && orders.nonEmpty) {

                var totalShares: Int = 0
                val _lastRoundOrders = lastRoundOrders()
                val lastRoundOrdersString = _lastRoundOrders.map(oe => {
                    val cq = oe.cumulative_quantity.get
                    totalShares += (if (oe.side.get == "buy") cq else -cq)
                    s"${oe.toString}  $totalShares"
                }).mkString("\n")
                val s = new String(MessageDigest.getInstance("MD5").digest(lastRoundOrdersString.getBytes))
                if (s != lastRoundOrdersHash) {
                    lastRoundOrdersHash = s
                    logger.debug(s"Orders sent to browser: position ${p.quantity}\n$lastRoundOrdersString")
                }

//                val matchedOrders = assignMatchId(lastRoundOrders)
                sendOrdersToBrowser(_lastRoundOrders)
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
        case HistoricalOrders(_, _, _, _orders, _) =>
            orders ++= _orders.filter(oe => oe.state.isDefined && (oe.state.get == "filled" || oe.state.get.contains("confirmed")))
            println(s"... check $symbol.log ...")
            logger.debug(s"Got HistoricalOrders:\n${orders.toList.map(_.toString).mkString("\n")}")
        case Tick => // purpose: send the symbol to the browser
            if (p.quantity.get >= 0) sendPosition else sendFundamental
    }
    override def receive: Receive = sideEffect andThen _receive
    private def sideEffect: PartialFunction[Any, Any] = { case x => ThreadContext.put("symbol", symbol); x }

    /**
      * @param tbOrders to-browser orders
      */
    private def assignMatchId(tbOrders: Array[OrderElement]): Array[OrderElement] = {
        val result: Array[OrderElement] = tbOrders.clone
        var i = 0
        while (i < tbOrders.length - 1) {
            if (tbOrders(i).quantity == tbOrders(i+1).quantity) {
                if (tbOrders(i).side.contains("buy") && tbOrders(i+1).side.contains("sell") &&
                        tbOrders(i).average_price.isDefined && tbOrders(i+1).average_price.isDefined &&
                        tbOrders(i).average_price.get < tbOrders(i+1).average_price.get) {
                    if (i+2 < tbOrders.length && tbOrders(i+2).side.contains("buy") &&
                            tbOrders(i+2).quantity == tbOrders(i).quantity &&
                            tbOrders(i+2).average_price.isDefined &&
                            tbOrders(i+2).average_price.get < tbOrders(i+1).average_price.get) {
                        if (tbOrders(i).average_price.get > tbOrders(i+2).average_price.get) {
                            val matchId = Some(combineIds(tbOrders(i).id.get, tbOrders(i + 1).id.get))
                            result(i) = tbOrders(i).copy(matchId = matchId)
                            result(i + 1) = tbOrders(i + 1).copy(matchId = matchId)
                            i += 2
                        }
                        else {
                            val matchId = Some(combineIds(tbOrders(i+1).id.get, tbOrders(i + 2).id.get))
                            result(i + 2) = tbOrders(i + 2).copy(matchId = matchId)
                            result(i + 1) = tbOrders(i + 1).copy(matchId = matchId)
                            i += 3
                        }
                    }
                    else {
                        val matchId = Some(combineIds(tbOrders(i).id.get, tbOrders(i + 1).id.get))
                        result(i) = tbOrders(i).copy(matchId = matchId)
                        result(i + 1) = tbOrders(i + 1).copy(matchId = matchId)
                        i += 2
                    }
                }
                else if (tbOrders(i).side.contains("sell") && tbOrders(i+1).side.contains("buy") &&
                        tbOrders(i).average_price.isDefined && tbOrders(i + 1).average_price.isDefined &&
                        tbOrders(i).average_price.get > tbOrders(i + 1).average_price.get) {
                    if (i+2 < tbOrders.length && tbOrders(i+2).side.contains("sell") &&
                            tbOrders(i+2).quantity == tbOrders(i).quantity &&
                            tbOrders(i + 2).average_price.isDefined &&
                            tbOrders(i+2).average_price.get > tbOrders(i+1).average_price.get) {
                        if (tbOrders(i).average_price.get < tbOrders(i+2).average_price.get) {
                            val matchId = Some(combineIds(tbOrders(i).id.get, tbOrders(i+1).id.get))
                            result(i) = tbOrders(i).copy(matchId = matchId)
                            result(i + 1) = tbOrders(i + 1).copy(matchId = matchId)
                            i += 2
                        }
                        else {
                            val matchId = Some(combineIds(tbOrders(i+1).id.get, tbOrders(i+2).id.get))
                            result(i + 1) = tbOrders(i + 1).copy(matchId = matchId)
                            result(i + 2) = tbOrders(i + 2).copy(matchId = matchId)
                            i += 3
                        }
                    }
                    else {
                        val matchId = Some(combineIds(tbOrders(i).id.get, tbOrders(i+1).id.get))
                        result(i) = tbOrders(i).copy(matchId = matchId)
                        result(i + 1) = tbOrders(i + 1).copy(matchId = matchId)
                        i += 2
                    }
                }
            }
        }
        result
    }

    private def combineIds(s1: String, s2: String): String = if (s1 < s2) s"$s1-$s2" else s"$s2-$s1"

    // please make sure position.quantity is defined before calling this
    private def lastRoundOrders(): List[OrderElement] = {
        @tailrec
        def f(givenSum: Int, currentSum: Int, building: List[OrderElement], remain: List[OrderElement]): List[OrderElement] = {
            if (givenSum == currentSum || remain == Nil) building
            else f(givenSum, currentSum + remain.head.quantity.get, building :+ remain.head, remain.tail)
        }

        f(p.quantity.get.toInt, 0, Nil, orders.toList)
/*
        var lastOrder: Option[OrderElement] = None
        var shareCount: Int = 0
        val x = orders.toArray.takeWhile(oe => {
            val cumulative_quantity = oe.cumulative_quantity.get
            shareCount += (if (oe.side.get == "buy") cumulative_quantity else -cumulative_quantity)
            if (shareCount != p.quantity.get) true else {
                lastOrder = Some(oe)
                false
            }
        })
        if (lastOrder.isDefined) x :+ lastOrder.get else x
*/
    }
    private def sendFundamental {
        val message = s"$symbol: FUNDAMENTAL: ${Fundamental.serialize(fu)}"
        context.actorSelection(s"../../${WebSocketActor.NAME}") ! message
    }

    private def sendOrdersToBrowser(os: List[OrderElement]) {
        val message = s"$symbol: ORDERS: ${Serialization.write(os)(DefaultFormats)}"
        context.actorSelection(s"../../${WebSocketActor.NAME}") ! message
    }

    private def sendPosition {
        val message = s"$symbol: POSITION: ${Position.serialize(p)}"
        context.actorSelection(s"../../${WebSocketActor.NAME}") ! message
    }

    private def sendQuote {
        if (q.last_trade_price.isDefined && q.last_trade_price.get > 0) {
            val message = s"$symbol: QUOTE: ${Quote.serialize(q)}"
            context.actorSelection(s"../../${WebSocketActor.NAME}") ! message
        }
    }
}
