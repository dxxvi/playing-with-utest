package home.sparkjava

import scala.concurrent.duration._
import akka.actor.{Actor, ActorLogging, Props, Timers}
import com.typesafe.scalalogging.Logger
import home.sparkjava.model._

object MainActor {
    val NAME = "mainActor"
    def props(): Props = Props(new MainActor)
}

class MainActor extends Actor with Timers with ActorLogging {
    val logger: Logger = Logger[MainActor]

    timers.startPeriodicTimer(Tick, Tick, 19482.millis)

    val symbolsHavingActor: collection.mutable.Set[String] = collection.mutable.Set()

    override def receive: Receive = {
        case Tick =>
            logger.debug(s"instrument2Symbol: ${Main.instrument2Symbol.size}")
        case x: AddSymbol =>
            if (!symbolsHavingActor.contains(x.symbol)) {
                symbolsHavingActor += x.symbol
                context.actorOf(StockActor.props(), s"symbol-${x.symbol}")
            }

            context.actorSelection(s"../${QuoteActor.NAME}") ! x
            context.actorSelection(s"../${OrderActor.NAME}") ! x
/*
        case position: Position =>
            val symbol: Option[String] = Main.instrument2Symbol.get(position.instrument)

            symbol.foreach { s =>
                if (!symbolsHavingActor.contains(s)) {
                    symbolsHavingActor += s
                    val actorRef = context.actorOf(StockActor.props(), s"symbol-$s")
                    logger.debug(s"stock actor ${actorRef.path}")
                }
            }

            symbol.map(AddSymbol).foreach { addSymbol =>
                Main.name2ActorRef.get(QuoteActor.NAME).foreach { _ ! addSymbol }
                Main.name2ActorRef.get(OrderActor.NAME).foreach { _ ! addSymbol }
            }
*/
        case x => logger.debug(s"Don't know what to do with $x yet")
    }
}
