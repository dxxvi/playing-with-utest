package home

import akka.actor.{Actor, Props}

object StockActor {
    sealed trait StockSealedTrait // TODO what's the purpose of this sealed trait?

    def props(symbol: String): Props = Props(new StockActor(symbol))
}

class StockActor(symbol: String) extends Actor {
    import StockActor._
    import akka.event.Logging
    import akka.event.LogSource

    implicit val logSource: LogSource[AnyRef] = new LogSource[AnyRef] {
        override def genString(t: AnyRef): String = symbol
    }
    val log = Logging(context.system, this)

    override def receive: Receive = {
        case s: String => log.debug(s)
    }
}
