package home.sparkjava

import java.util.UUID

import concurrent.duration._
import akka.actor.{Actor, Props, Timers}
import home.sparkjava.model.{Fundamental, Order, Position, Quote}
import org.apache.logging.log4j.ThreadContext
import org.apache.logging.log4j.scala.Logging

import scala.util.Random

object StockActor {
    def props(symbol: String): Props = Props(new StockActor(symbol))
}
class LastCreatedAt(var buy: Long = 0, var sell: Long = 0)

class StockActor(symbol: String) extends Actor with Timers with Logging {
    import spray.json._
    import model.OrderProtocol._
    import model.QuoteProtocol._
    import model.FundamentalProtocol._

    var qo: Option[Quote] = None
    var fo: Option[Fundamental] = None
    var po: Option[Position] = None
    val orders: collection.mutable.SortedSet[Order] =
        collection.mutable.SortedSet[Order]()(Ordering.by[Order, String](_.createdAt)(Main.timestampOrdering.reverse))
    var justStarted = true
    var debug = false
    val lastCreatedAt = new LastCreatedAt

    timers.startPeriodicTimer(Tick, Tick, 4019.millis)

    val _receive: Receive = {
        case q: Quote =>
            this.qo = Some(q)
            val message = s"${q.symbol}: QUOTE: ${q.toJson.compactPrint}"
            context.actorSelection(s"../../${WebSocketActor.NAME}") ! message
            if (debug) logger.debug(s"Received a $q, sent $message to browser.")
        case f: Fundamental =>
            this.fo = Some(f)
            val message = s"${this.symbol}: FUNDAMENTAL: ${f.toJson.compactPrint}"
            context.actorSelection(s"../../${WebSocketActor.NAME}") ! message
            if (debug) logger.debug(s"Received a $f, sent $message to browser.")
        case p: Position =>
            this.po = Some(p)
            if (debug) logger.debug(s"Received a $p")
        case o: Order => o.state match {
            case "cancelled" =>
                orders -= o
                if (debug) logger.debug(s"Remove this $o")
            case x if x == "filled" || x == "confirmed" || x == "unconfirmed" =>
                orders -= o
                orders += o
                if (debug) logger.debug(s"Update this $o")
            case "partially_filled" | "queued" =>  // ignore it
            case _ => logger.debug(s"What to do with this order state $o")
        }
        case Tick if justStarted =>
            Thread.sleep(Random.nextInt(41) * 1001)
            context.actorSelection(s"../../${QuoteActor.NAME}") ! AllQuotes.Get(symbol)
            context.actorSelection(s"../../${OrderActor.NAME}") ! AllOrders.Get(symbol)
        case Tick if orders.nonEmpty =>
//            logger.debug(s"$symbol Trimming orders")
            trimOrders()
            updateOrdersWithMatchId()
            updateOrdersWithMatchId2()
            checkToBuyOrSell(orders.filter(o => o.matchId == ""))
            checkToBuyOrSell(orders)
//            logger.debug(s"$symbol Sending all orders enriched with matchIds to browser")
            sendOrdersToBrowser()
        case Tick =>  // do nothing
        case AllOrders.Here(_orders) =>
            justStarted = false
            orders.clear()
            orders ++= _orders
        case "DEBUG_ON" => debug = true
        case "DEBUG_OFF" => debug = false
        case x => logger.info(s"$symbol Don't know what to do with $x yet")
    }

    val sideEffect: PartialFunction[Any, Any] = {
        case x =>
            ThreadContext.put("symbol", symbol)
            x
    }

    override def receive: Receive = sideEffect andThen _receive

    private def sendOrdersToBrowser(): Unit = {
        val message = s"$symbol: ORDERS: ${orders.toList.toJson.compactPrint}"
        if (debug) logger.debug(s"Send to browser: $message")
        context.actorSelection(s"../../${WebSocketActor.NAME}") ! message
    }

    // gives transactions, that are next to each other, have different sides, same quantity and make profit, match ids
    private def updateOrdersWithMatchId(): Unit = {
        def updateWithMatchId(a: Array[Order]): Unit = {
            var i = 0
            var breakFlag = false
            while ((i < a.length - 1) && !breakFlag) {
                if (a(i).quantity == a(i+1).quantity) {
                    if (a(i).side == "buy" && a(i+1).side == "sell" && a(i).averagePrice < a(i+1).averagePrice) {
                        breakFlag = true
                        val matchId = UUID.randomUUID().toString.substring(9)
                        if (i+2 < a.length && a(i+2).side == "buy" && a(i+2).quantity == a(i).quantity &&
                                a(i+2).averagePrice < a(i+1).averagePrice) {
                            val j = if (a(i).averagePrice > a(i+2).averagePrice) i else i + 2
                            a(j).matchId = matchId
                            a(i+1).matchId = matchId
                        }
                        else {
                            a(i).matchId = matchId
                            a(i+1).matchId = matchId
                        }
                    }
                    else if (a(i).side == "sell" && a(i+1).side == "buy" && a(i).averagePrice > a(i+1).averagePrice) {
                        breakFlag = true
                        val matchId = UUID.randomUUID().toString.substring(10)
                        if (i+2 < a.length && a(i+2).side == "sell" && a(i+2).quantity == a(i).quantity &&
                                a(i+2).averagePrice > a(i+1).averagePrice) {
                            val j = if (a(i).averagePrice < a(i+2).averagePrice) i else i + 2
                            a(j).matchId = matchId
                            a(i+1).matchId = matchId
                        }
                        else {
                            a(i).matchId = matchId
                            a(i+1).matchId = matchId
                        }
                    }
                }
                i = i + 1
            }
            if (breakFlag) {
                updateWithMatchId(a.filter(_.matchId == ""))
            }
        }

        orders foreach { _.matchId = "" }
        updateWithMatchId(orders.filter(_.state == "filled").toArray)
    }

    // gives transactions, that are 1 sell which cancels some next buys, match ids
    private def updateOrdersWithMatchId2(): Unit = {
        val a = orders.filter(o => o.state == "filled" && o.matchId == "").dropWhile(_.side == "buy").toArray
    }

    // keep orders until we have 0 share of this stock
    private def trimOrders(): Unit = {
        if (po.nonEmpty) {
            var n = -po.get.quantity
            val trimmedOrders = orders.takeWhile { order =>
                if (n != 0) {
                    if (order.state == "filled") {
                        n = n + (if (order.side == "buy") order.quantity else -order.quantity)
                    }
                    true
                }
                else false
            }
            orders.clear()
            orders ++= trimmedOrders
        }
    }

    private def checkToBuyOrSell(_orders: collection.mutable.SortedSet[Order]) {
        val noConfirmedSell = !_orders.exists(o => (o.state == "confirmed" || o.state == "unconfirmed") && o.side == "sell")
        val noConfirmedBuy  = !_orders.exists(o => (o.state == "confirmed" || o.state == "unconfirmed") && o.side == "buy")
        val firstFilledOption = _orders.find(_.state == "filled")
        firstFilledOption.foreach { firstFilled =>
            def printDebug(): Unit =
                logger.debug(s"""$symbol noConfirmedSell: $noConfirmedSell, noConfirmedBuy: $noConfirmedBuy,
                   |first filled order: ${firstFilled.toJson.compactPrint}
                   |orders:
                   |${_orders.toList.toJson.prettyPrint}""".stripMargin)
            if (firstFilled.side == "buy"
                    && (System.currentTimeMillis - lastCreatedAt.sell > 15000)
                    && noConfirmedSell
                    && qo.nonEmpty && qo.get.lastTradePrice > 1.01 * firstFilled.averagePrice
                    && fo.nonEmpty && qo.get.lastTradePrice > 1.015 * fo.get.open
            ) {
                printDebug()
                val message = s"You should sell ${firstFilled.quantity.toInt} $symbol at ${qo.get.lastTradePrice}."
                logger.info(message)
                context.actorSelection(s"../../${WebSocketActor.NAME}") ! s"NOTICE: PRIMARY: $message"
                lastCreatedAt.sell = System.currentTimeMillis
            }
            if (firstFilled.side == "buy" && firstFilled.matchId == ""
                    && (System.currentTimeMillis - lastCreatedAt.buy > 15000)
                    && noConfirmedBuy
                    && qo.nonEmpty && qo.get.lastTradePrice < 0.97 * firstFilled.averagePrice
                    && fo.nonEmpty && qo.get.lastTradePrice < 0.98 * fo.get.open
            ) {
                printDebug()
                val message = s"You should buy ${firstFilled.quantity.toInt + 1 + (20/qo.get.lastTradePrice).toInt} $symbol at ${qo.get.lastTradePrice}."
                logger.info(message)
                context.actorSelection(s"../../${WebSocketActor.NAME}") ! s"NOTICE: DANGER: $message"
                lastCreatedAt.buy = System.currentTimeMillis
            }
            if (firstFilled.side == "sell"
                    && (System.currentTimeMillis - lastCreatedAt.buy > 15000)
                    && noConfirmedBuy
                    && qo.nonEmpty && qo.get.lastTradePrice < 0.97 * firstFilled.averagePrice
                    && fo.nonEmpty && qo.get.lastTradePrice < 0.98 * fo.get.open
            ) {
                printDebug()
                val message = s"You should buy ${firstFilled.quantity.toInt} $symbol at ${qo.get.lastTradePrice}."
                logger.info(message)
                context.actorSelection(s"../../${WebSocketActor.NAME}") ! s"NOTICE: DANGER: $message"
                lastCreatedAt.buy = System.currentTimeMillis
            }
        }

        if (firstFilledOption.isEmpty && fo.nonEmpty
                && qo.nonEmpty && qo.get.lastTradePrice < 0.97 * fo.get.open
                && (System.currentTimeMillis - lastCreatedAt.buy > 15000) ) {
            logger.debug(s"""orders:
                 |${_orders.toList.toJson.prettyPrint}""".stripMargin)
            logger.info(s"$symbol You should buy 1 $symbol at ${qo.get.lastTradePrice}")
            lastCreatedAt.buy = System.currentTimeMillis
        }
    }
}
