package home.sparkjava

import concurrent.duration._
import akka.actor.{Actor, ActorLogging, Props, Timers}
import com.typesafe.scalalogging.Logger
import home.sparkjava.model.{Fundamental, Order, Quote}

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
        case o: Order => orders += o
        case Tick if justStarted => context.actorSelection(s"../../${OrderActor.NAME}") ! AllOrders.Get(symbol)
        case Tick if orders.nonEmpty =>
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

    private def updateOrdersWithMatchId(): Unit = {
        def updateWithMatchId(array: Array[Order]): Unit = {
            var i = 0
            while (i < array.length - 1) {

            }
        }

        orders foreach { _.matchId = "" }
        updateWithMatchId(orders.filter(_.state == "filled").toArray)
    }
}
