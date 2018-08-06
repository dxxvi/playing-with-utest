package home.sparkjava

import akka.actor.{Actor, Props}
import home.sparkjava.message.Tick
import org.apache.logging.log4j.ThreadContext
import model.Fundamental
import model.Position

object StockActor {
    def props(symbol: String): Props = Props(new StockActor(symbol))
}

class StockActor(symbol: String) extends Actor with Util {
    var fu = new Fundamental(
        Some(-.1), Some(-.1), Some(""), Some(-.1), Some(-.1), Some(-.1), Some(-.1), Some(-.1), Some(-.1), Some(-.1), ""
    )
    var p = new Position(
        Some(-.1), Some(-.1), Some(-.1), Some(-.1), Some(-.1), None, None, Some(-.1), Some(-.1), None, Some(-.1),
        Some(-.1), Some(-.1), Some(-.1)
    )

    val _receive: Receive = {
        case _fu: Fundamental => fu = _fu; sendFundamental
        case _p: Position => p = _p; sendPosition
        case Tick => if (p.quantity.get >= 0) sendPosition else sendFundamental
    }
    override def receive: Receive = sideEffect andThen _receive
    private def sideEffect: PartialFunction[Any, Any] = { case x => ThreadContext.put("symbol", symbol); x }

    private def sendFundamental {
        val message = s"$symbol: FUNDAMENTAL: ${Fundamental.serialize(fu)}"
        context.actorSelection(s"../../${WebSocketActor.NAME}") ! message
        logger.debug(s"Sent a fundamental $message to browser.")
    }

    private def sendPosition {
        val message = s"$symbol: POSITION: ${Position.serialize(p)}"
        context.actorSelection(s"../../${WebSocketActor.NAME}") ! message
        logger.debug(s"Sent a position $message to browser.")
    }
}
