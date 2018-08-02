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
        case AddSymbol(symbol) =>
            val identity = context.actorSelection(s"../$NAME/symbol-$symbol") ? Identify("hi")
            Await.result(identity, 2904.millis) match {
                case Success(ActorIdentity(_, None)) => context.actorOf(StockActor.props(symbol), s"symbol-$symbol")
                case Failure(exception) =>
                    logger.error(s"Got this error while checking if there's a StockActor for $symbol: $exception")
                case _ => // ignored
            }
        case AddSymbol("TESTING") =>
        case ActorIdentity(_, aro) =>
    }

    override def receive: Receive = Main.sideEffect andThen _receive
}
