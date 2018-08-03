package home.sparkjava

import akka.actor.{Actor, ActorIdentity, Identify, Props}
import akka.pattern.ask
import akka.util.Timeout
import com.typesafe.config.Config
import home.sparkjava.message.AddSymbol
import org.apache.logging.log4j.ThreadContext

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.{Failure, Success}

object MainActor {
    val NAME = "mainActor"
    implicit val timeout: Timeout = Timeout(1904.millis)

    def props(config: Config): Props = Props(new MainActor)
}

class MainActor extends Actor with Util {
    import MainActor._
    import context.dispatcher

    val _receive: Receive = {
        case AddSymbol(symbol) if symbol != "TESTING" =>
            context.actorSelection(s"../$NAME/symbol-$symbol") ! Identify(symbol)
        case ActorIdentity(id, None) =>
            val actorRef = context.actorOf(StockActor.props(id.asInstanceOf[String]), s"symbol-$id")
            logger.debug(s"Just created StockActor $actorRef")
        case ActorIdentity(id, Some(actorRef)) => // This StockActor already exists.
        case x => logger.warn(s"$x")
    }

    override def receive: Receive = sideEffect andThen _receive

    private def sideEffect: PartialFunction[Any, Any] = { case x => ThreadContext.put("symbol", NAME); x }
}
