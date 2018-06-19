package home.sparkjava

import scala.concurrent.duration._
import akka.actor.{Actor, ActorLogging, ActorRef, Props, Timers}
import akka.http.scaladsl.Http
import akka.http.scaladsl.settings.ConnectionPoolSettings
import akka.stream.{ActorMaterializer, ActorMaterializerSettings}
import com.typesafe.config.Config
import com.typesafe.scalalogging.Logger

object OrderActor {
    val NAME = "orderActor"
    def props(config: Config): Props = Props(new OrderActor(config))
}

class OrderActor(config: Config) extends Actor with Timers with ActorLogging with Util {
    import OrderActor._
    import akka.pattern.pipe
    import context.dispatcher

    val logger: Logger = Logger[OrderActor]

    implicit val materializer: ActorMaterializer = ActorMaterializer(ActorMaterializerSettings(context.system))

    val SERVER: String = config.getString("server")
    val connectionPoolSettings: ConnectionPoolSettings = getConnectionPoolSettings(config, context.system)

    val symbols: collection.mutable.Set[String] = collection.mutable.Set[String]()
    val http = Http(context.system)

    timers.startPeriodicTimer(Tick, Tick, 4019.millis)

    override def receive: Receive = {
        case x: AddSymbol => symbols += x.symbol.toUpperCase
        case x: RemoveSymbol => symbols -= x.symbol.toUpperCase
        case Tick if symbols.nonEmpty => logger.debug("I need to get the orders here")
        case Tick =>  // do nothing
        case x => logger.debug(s"Don't know what to do with $x yet")
    }
}
