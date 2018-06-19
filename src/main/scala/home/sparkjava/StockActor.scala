package home.sparkjava

import akka.actor.{Actor, ActorLogging, Props}
import com.typesafe.scalalogging.Logger
import home.sparkjava.model.{Order, Quote}

object StockActor {
    def props(): Props = Props(new StockActor)
}

class StockActor extends Actor with ActorLogging {
    import spray.json._
    import model.QuoteProtocol._

    val logger: Logger = Logger[StockActor]

    override def receive: Receive = {
        case q: Quote =>
            context.actorSelection(s"../../${WebSocketActor.NAME}") ! s"${q.symbol}: QUOTE: ${q.toJson.compactPrint}"
        case filledOrders: Vector[Order] => logger.debug(s"Need to send $filledOrders to webSocketActor")
    }
}
