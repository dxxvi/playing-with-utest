package home.sparkjava

import java.util.concurrent.atomic.AtomicLong

import akka.actor.{Actor, ActorIdentity, Identify, Props, Timers}
import akka.util.Timeout
import com.typesafe.config.Config
import home.sparkjava.message.{AddSymbol, Tick}
import org.apache.logging.log4j.ThreadContext

import scala.concurrent.duration._

object MainActor {
    val NAME = "mainActor"
    implicit val timeout: Timeout = Timeout(1904.millis)

    def props(config: Config): Props = Props(new MainActor(config))

    case object CanStockActorSendHistoricalOrders
    case object GoAheadSendHistoricalOrders
    case object DonotSendHistoricalOrders
}

class MainActor(config: Config) extends Actor with Timers with Util {
    import MainActor._
    /**
      * When a StockActor wants to send a HistoricalOrders message to the OrderActor, it must check if
      * historicalOrdersTicket > 0. If yes, it can send that message and decrease the historicalOrdersTicket by 1.
      */
    var historicalOrdersTicket: Long = 1

    timers.startPeriodicTimer(Tick, Tick, (Main.calculateShortDuration().toMillis / 4).milliseconds)

    val _receive: Receive = {
        case Tick =>
            historicalOrdersTicket = historicalOrdersTicket + 1
            if (historicalOrdersTicket > 10) historicalOrdersTicket = 7
        case AddSymbol(symbol) if symbol != "TESTING" =>
            context.actorSelection(s"../$NAME/symbol-$symbol") ! Identify(symbol)
        case ActorIdentity(id, None) =>
            val actorRef = context.actorOf(StockActor.props(id.asInstanceOf[String], config), s"symbol-$id")
            logger.debug(s"Just created StockActor $actorRef")
        case ActorIdentity(_, Some(_)) => // This StockActor already exists.
        case CanStockActorSendHistoricalOrders =>
            if (historicalOrdersTicket > 0) {
//                println(s"$sender() asks if she can send HistoricalOrders message historicalOrdersTicket: $historicalOrdersTicket")
                historicalOrdersTicket = historicalOrdersTicket - 1
                sender() ! GoAheadSendHistoricalOrders
            }
            else sender() ! DonotSendHistoricalOrders
        case x => logger.warn(s"$x")
    }

    override def receive: Receive = sideEffect andThen _receive

    private def sideEffect: PartialFunction[Any, Any] = { case x => ThreadContext.put("symbol", NAME); x }
}
