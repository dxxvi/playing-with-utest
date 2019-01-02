package home

import akka.actor.{Actor, Timers}
import com.softwaremill.sttp._
import com.typesafe.config.Config
import home.util.SttpBackends

object OrderActor {
    import akka.actor.Props

    val NAME: String = "order"
    val AUTHORIZATION: String = "Authorization"

    sealed trait OrderSealedTrait
    case object Tick extends OrderSealedTrait
    case class OrderHistoryRequest(symbol: String) extends OrderSealedTrait
    case class RecentOrderResponseWrapper(r: Response[List[(String, StockActor.Order)]]) extends OrderSealedTrait

    def props(config: Config): Props = Props(new OrderActor(config))
}

class OrderActor(config: Config) extends Actor with Timers with SttpBackends {
    import OrderActor._
    import context.dispatcher
    import concurrent.Future
    import concurrent.duration._
    import akka.event._
    import akka.pattern.pipe
    import akka.stream.scaladsl.Source
    import akka.util.ByteString
    import home.util.Util

    implicit val logSource: LogSource[AnyRef] = (_: AnyRef) => NAME
    val log: LoggingAdapter = Logging(context.system, this)

    val SERVER: String = config.getString("server")
    val authorization: String = if (config.hasPath(AUTHORIZATION)) config.getString(AUTHORIZATION) else "No-token"

    val recentOrdersRequest: RequestT[Id, List[(String, StockActor.Order)], Nothing] = sttp
            .header(AUTHORIZATION, authorization)
            .get(uri"${SERVER}orders/")
            .response(asString.map(Util.extractSymbolAndOrder))

    val positionRequest: Request[String, Nothing] = sttp
            .header(AUTHORIZATION, authorization)
            .get(uri"$SERVER/positions/")

    timers.startPeriodicTimer(Tick, Tick, 4019.millis)

    override def receive: Receive = {
        case Tick =>
            implicit val backend: SttpBackend[Future, Source[ByteString, Any]] = configureAkkaHttpBackend(config)
            recentOrdersRequest.send().map(RecentOrderResponseWrapper) pipeTo self

            fetchPositionJsonString() match {
                case Right(js) => Util.extractSymbolAndPosition(js).foreach(t =>
                        context.actorSelection(s"../${DefaultWatchListActor.NAME}/${t._1}") ! StockActor.Position(t._2)
                )
                case Left(s) => log.error("Error in getting positions: {}", s)
            }

        case RecentOrderResponseWrapper(Response(rawErrorBody, code, statusText, _, _)) =>
            rawErrorBody.fold(
                _ => log.error("Error in getting recent orders: {} {}", code, statusText),
                (list: List[(String, StockActor.Order)]) => list.foreach(t =>
                    context.actorSelection(s"../${DefaultWatchListActor.NAME}/${t._1}") ! t._2
                )
            )
    }

    private def fetchPositionJsonString(): Either[String, String] = {
        implicit val backend: SttpBackend[Id, Nothing] = configureCoreJavaHttpBackend(config)
        positionRequest.send().body
    }
}
