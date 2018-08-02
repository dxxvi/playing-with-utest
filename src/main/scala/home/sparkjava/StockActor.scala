package home.sparkjava

import akka.actor.{Actor, Props}

object StockActor {
    def props(symbol: String): Props = Props(new StockActor(symbol))
}

class StockActor(symbol: String) extends Actor with Util {
    val _receive: Receive = {
        case x => println(x)
    }
    override def receive: Receive = Main.sideEffect andThen _receive
}
