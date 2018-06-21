package home.sparkjava

import scala.concurrent.duration._
import akka.actor.{Actor, ActorLogging, ActorRef, Props, Timers}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse, StatusCodes, Uri}
import akka.http.scaladsl.model.Uri.Query
import akka.http.scaladsl.settings.ConnectionPoolSettings
import akka.stream.{ActorMaterializer, ActorMaterializerSettings}
import com.typesafe.config.Config
import com.typesafe.scalalogging.Logger

object OrderActor {
    val NAME = "orderActor"
    def props(config: Config): Props = Props(new OrderActor(config))

    case class AllOrdersResponse(httpResponse: HttpResponse, symbol: String)
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
        case AllOrders.Get(symbol) =>
            Main.instrument2Symbol.collectFirst {
                case (instrument, _symbol) if _symbol == symbol => instrument
            } foreach { instrument =>
                val uri = Uri(SERVER + "orders/") withQuery Query(("instrument", instrument))
                http.singleRequest(HttpRequest(uri = uri), settings = connectionPoolSettings)
                        .map { (_, symbol) }
                        .pipeTo(self)
            }
        case AllOrdersResponse(httpResonse, symbol) => httpResonse match {
            case HttpResponse(StatusCodes.OK, _, entity, _) =>
            case HttpResponse(statusCode, _, entity, _) =>
        }
        case Tick if symbols.nonEmpty => logger.debug("I need to get the orders here")
        case Tick =>  // do nothing
        case x => logger.debug(s"Don't know what to do with $x yet")
    }
}
