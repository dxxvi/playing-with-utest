package home.sparkjava

import java.util.UUID

import concurrent.duration._
import akka.actor.{Actor, ActorLogging, Props, Timers}
import com.typesafe.scalalogging.Logger
import home.sparkjava.model.{Fundamental, Order, Position, Quote}

object StockActor {
    def props(symbol: String): Props = Props(new StockActor(symbol))
}
class LastCreatedAt(var buy: Long = 0, var sell: Long = 0)

class StockActor(symbol: String) extends Actor with Timers with ActorLogging {
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
    val logger: Logger = Logger(s"${classOf[StockActor].getName}.$symbol")

    timers.startPeriodicTimer(Tick, Tick, 4019.millis)

    override def receive: Receive = {
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
            case "cancelled" => orders -= o
            case x if x == "filled" || x == "confirmed" || x == "unconfirmed" =>
                orders -= o
                orders += o
            case "partially_filled" | "queued" =>  // ignore it
            case x => logger.debug(s"What to do with this order state ${o.state}")
        }
        case Tick if justStarted => context.actorSelection(s"../../${OrderActor.NAME}") ! AllOrders.Get(symbol)
        case Tick if orders.nonEmpty =>
            if (debug) logger.debug("Trimming orders")
            trimOrders()
            checkToBuyOrSell()
            if (debug) logger.debug("Update orders with match id")
            updateOrdersWithMatchId()
            if (debug) logger.debug("Update orders with match id 2")
            updateOrdersWithMatchId2()
            if (debug) logger.debug("Sending all orders enriched with matchIds to browser")
            sendOrdersToBrowser()
        case Tick =>  // do nothing
        case AllOrders.Here(_orders) =>
            justStarted = false
            orders.clear()
            orders ++= _orders
        case "DEBUG_ON" => debug = true
        case "DEBUG_OFF" => debug = false
        case x => logger.info(s"Don't know what to do with $x yet")
    }

    private def sendOrdersToBrowser(): Unit = {
        context.actorSelection(s"../../${WebSocketActor.NAME}") ! s"$symbol: ORDERS: ${orders.toList.toJson.compactPrint}"
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

    private def checkToBuyOrSell() {
        val noConfirmedSell = !orders.exists(o => o.state == "confirmed" && o.side == "sell")
        val noConfirmedBuy  = !orders.exists(o => o.state == "confirmed" && o.side == "buy")
        val firstFilledOption = orders.find(_.state == "filled")
        firstFilledOption.foreach { firstFilled =>
            if (debug) logger.debug(s"""noConfirmedSell: $noConfirmedSell, noConfirmedBuy: $noConfirmedBuy,
                   |first filled order: ${firstFilled.toJson.compactPrint}
                   |orders:
                   |${orders.toList.toJson.prettyPrint}""".stripMargin)
            if (firstFilled.side == "buy"
                    && (System.currentTimeMillis - lastCreatedAt.sell > 15000)
                    && noConfirmedSell
                    && qo.nonEmpty && qo.get.lastTradePrice > 0.99 * firstFilled.averagePrice
                    && fo.nonEmpty && qo.get.lastTradePrice > 1.015 * fo.get.open
            ) {
                logger.info(s"You should sell ${firstFilled.quantity} $symbol at ${qo.get.lastTradePrice}.")
                lastCreatedAt.sell = System.currentTimeMillis
            }
            if (firstFilled.side == "sell"
                    && (System.currentTimeMillis - lastCreatedAt.buy > 15000)
                    && noConfirmedBuy
                    && qo.nonEmpty && qo.get.lastTradePrice < 0.99 * firstFilled.averagePrice
                    && fo.nonEmpty && qo.get.lastTradePrice < 1.025 * fo.get.open
            ) {
                logger.info(s"You should buy ${firstFilled.quantity} $symbol at ${qo.get.lastTradePrice}.")
                lastCreatedAt.buy = System.currentTimeMillis
            }
        }

        if (firstFilledOption.isEmpty && fo.nonEmpty
                && qo.nonEmpty && qo.get.lastTradePrice < 0.97 * fo.get.open
                && (System.currentTimeMillis - lastCreatedAt.buy > 15000) ) {
            logger.info(s"You should buy 1 $symbol at ${qo.get.lastTradePrice}")
            lastCreatedAt.buy = System.currentTimeMillis
        }
    }
}
