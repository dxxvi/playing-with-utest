package home.sparkjava

import akka.actor.{Actor, Props}
import org.apache.logging.log4j.ThreadContext

object StockActor {
    def props(symbol: String): Props = Props(new StockActor(symbol))
}

class StockActor(symbol: String) extends Actor with Util {
    val _receive: Receive = {
        case x => println(x)
    }
    override def receive: Receive = sideEffect andThen _receive

    private def sideEffect: PartialFunction[Any, Any] = { case x => ThreadContext.put("symbol", symbol); x }
}
