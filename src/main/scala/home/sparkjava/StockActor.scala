package home.sparkjava

import akka.actor.{Actor, ActorLogging, Props}
import com.typesafe.scalalogging.Logger
import home.sparkjava.model.{Fundamental, Order, Quote}

object StockActor {
    def props(symbol: String): Props = Props(new StockActor(symbol))
}

class StockActor(symbol: String) extends Actor with ActorLogging {
    import spray.json._
    import model.QuoteProtocol._
    import model.FundamentalProtocol._

    var qo: Option[Quote] = None
    var fo: Option[Fundamental] = None

    val logger: Logger = Logger[StockActor]

    override def receive: Receive = {
        case q: Quote =>
            this.qo = Some(q)
            context.actorSelection(s"../../${WebSocketActor.NAME}") ! s"${q.symbol}: QUOTE: ${q.toJson.compactPrint}"
        case f: Fundamental =>
            this.fo = Some(f)
            context.actorSelection(s"../../${WebSocketActor.NAME}") ! s"${this.symbol}: FUNDAMENTAL: ${f.toJson.compactPrint}"
        case filledOrders: Vector[Order] => logger.debug(s"Need to send $filledOrders to webSocketActor")
    }
}
