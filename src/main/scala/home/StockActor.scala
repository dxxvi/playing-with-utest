package home

import akka.actor.Actor

object StockActor {
    import akka.actor.Props

    sealed trait StockSealedTrait // TODO what's the purpose of this sealed trait?
    case class Quote(lastTradePrice: Double) extends StockSealedTrait

    def props(symbol: String): Props = Props(new StockActor(symbol))
}

class StockActor(symbol: String) extends Actor {
    import StockActor._
    import akka.event._

    implicit val logSource: LogSource[AnyRef] = new LogSource[AnyRef] {
        override def genString(t: AnyRef): String = symbol
    }
    val log: LoggingAdapter = Logging(context.system, this)

    var ltp: Double = Double.NaN                 // last trade price

    override def receive: Receive = {
        case Quote(lastTradePrice) =>
            ltp = lastTradePrice
    }
}
