package home.sparkjava

import java.nio.file.{Files, Paths, StandardOpenOption}
import java.util.UUID

import concurrent.duration._
import akka.actor.{Actor, ActorLogging, Props, Timers}
import com.typesafe.scalalogging.Logger
import home.sparkjava.model.{Fundamental, Order, Position, Quote}

object StockActor {
    def props(symbol: String): Props = Props(new StockActor(symbol))
}

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
    val logger: Logger = Logger[StockActor]

    timers.startPeriodicTimer(Tick, Tick, 4019.millis)

    override def receive: Receive = {
        case q: Quote =>
            this.qo = Some(q)
            context.actorSelection(s"../../${WebSocketActor.NAME}") ! s"${q.symbol}: QUOTE: ${q.toJson.compactPrint}"
        case f: Fundamental =>
            this.fo = Some(f)
            context.actorSelection(s"../../${WebSocketActor.NAME}") ! s"${this.symbol}: FUNDAMENTAL: ${f.toJson.compactPrint}"
        case p: Position => this.po = Some(p)
        case o: Order => o.state match {
            case "cancelled" => orders -= o
            case x if x == "filled" || x == "confirmed" =>
                orders -= o
                orders += o
            case "queued" =>  // ignore it
            case x => logger.debug(s"What to do with this order state ${o.state}")
        }
        case Tick if justStarted => context.actorSelection(s"../../${OrderActor.NAME}") ! AllOrders.Get(symbol)
        case Tick if orders.nonEmpty =>
            trimOrders()
            updateOrdersWithMatchId()
            sendOrdersToBrowser()
        case Tick =>  // do nothing
        case AllOrders.Here(_orders) =>
            justStarted = false
            orders.clear()
            orders ++= _orders
        case x => logger.info(s"Don't know what to do with $x yet")
    }

    private def sendOrdersToBrowser(): Unit = {
        context.actorSelection(s"../../${WebSocketActor.NAME}") ! s"$symbol: ORDERS: ${orders.toList.toJson.compactPrint}"
    }

    private val lines = collection.mutable.MutableList[String]()
    private def updateOrdersWithMatchId(): Unit = {
        def updateWithMatchId(a: Array[Order]): Unit = {
            if (symbol == "HTZ") {
                orders.foreach { b =>
                    lines += s"${b.matchId} ${b.side} ${b.createdAt.replace("2018-", "").replace("T", " ").replaceAll("\\.\\d+Z$", "")} ${b.quantity} x ${b.averagePrice}"
                }
                lines += "--------------------------------------------------------"
            }

            var i = 0
            var breakFlag = false
            while ((i < a.length - 1) && !breakFlag) {
                if (a(i).quantity == a(i+1).quantity) {
                    if (a(i).side == "buy" && a(i+1).side == "sell" && a(i).averagePrice < a(i+1).averagePrice) {
                        breakFlag = true
                        val matchId = UUID.randomUUID().toString.substring(9)
                        if (i+2 < a.length && a(i+2).side == "buy" && a(i+2).quantity == a(i).quantity &&
                                a(i+2).averagePrice < a(i+1).averagePrice) {
                            if (a(i).averagePrice > a(i+2).averagePrice) {
                                a(i).matchId = matchId
                            }
                            else {
                                a(i+2).matchId = matchId
                            }
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
                            if (a(i).averagePrice < a(i+2).averagePrice) {
                                a(i).matchId = matchId
                            }
                            else {
                                a(i+2).matchId = matchId
                            }
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
        if (lines.nonEmpty) {
            import collection.JavaConverters._
//            Files.write(Paths.get(s"C:\\tdangvu\\HTZ-${System.currentTimeMillis}.txt"), lines.asJava, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
            lines.clear()
        }
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
}
