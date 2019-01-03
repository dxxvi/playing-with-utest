package home

import akka.actor.{Actor, Timers}
import com.softwaremill.sttp._
import com.typesafe.config.Config
import home.util.{StockDatabase, SttpBackends}

object OrderActor {
    import akka.actor.Props

    val NAME: String = "order"
    val AUTHORIZATION: String = "Authorization"

    sealed trait OrderSealedTrait
    case object Tick extends OrderSealedTrait
    case class OrderHistoryRequest(symbol: String) extends OrderSealedTrait
    private case class TempOrderHistoryRequest(
        symbol: String,
        times: Int,
        nextUrl: Option[String]
    ) extends OrderSealedTrait
    private case class RecentOrderResponseWrapper(r: Response[List[(String, StockActor.Order)]])
            extends OrderSealedTrait
    private case class TempOrderHistoryResponseWrapper(
        symbol: String,
        r: Response[(List[Order], Option[String])],
        times: Int
    ) extends OrderSealedTrait

    private case class Order(
                            averagePrice: Double,
                            createdAt: String,
                            cumulativeQuantity: Double,
                            id: String,
                            price: Double,
                            quantity: Double,
                            side: String,
                            state: String,
                            updatedAt: String
                    )

    def props(config: Config): Props = Props(new OrderActor(config))
}

class OrderActor(config: Config) extends Actor with Timers with SttpBackends {
    import OrderActor._
    import context.dispatcher
    import concurrent.Future
    import concurrent.duration._
    import akka.actor.ActorSelection
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

    var useAkkaHttp: Boolean = true

    timers.startPeriodicTimer(Tick, Tick, 4019.millis)

    override def receive: Receive = {
        case Tick =>
            implicit val backend: SttpBackend[Future, Source[ByteString, Any]] = configureAkkaHttpBackend(config)
            recentOrdersRequest.send().map(RecentOrderResponseWrapper) pipeTo self

            fetchPositionJsonString() match {
                case Right(js) => Util.extractSymbolAndPosition(js).foreach(t => {
                    val stockActor: ActorSelection =
                        context.actorSelection(s"../${DefaultWatchListActor.NAME}/${t._1}")
                    stockActor ! StockActor.Position(t._2)
                })
                case Left(s) => log.error("Error in getting positions: {}", s)
            }

        case RecentOrderResponseWrapper(Response(rawErrorBody, code, statusText, _, _)) =>
            rawErrorBody.fold(
                _ => log.error("Error in getting recent orders: {} {}", code, statusText),
                (list: List[(String, StockActor.Order)]) => list.foreach(t =>
                    context.actorSelection(s"../${DefaultWatchListActor.NAME}/${t._1}") ! t._2
                )
            )

        case OrderHistoryRequest(symbol) => self ! TempOrderHistoryRequest(symbol, 4, Some(""))

        case TempOrderHistoryRequest(symbol, times, nextUrl) => if (times > 0) {
            val uriOption: Option[Uri] = buildUriOptionFromNextUrlOption(symbol, nextUrl)
            uriOption match {
                case Some(uri) if useAkkaHttp =>
                    useAkkaHttp = false
                    implicit val be: SttpBackend[Future, Source[ByteString, Any]] = configureAkkaHttpBackend(config)
                    sttp
                            .header(AUTHORIZATION, authorization)
                            .get(uri)
                            .response(asString.map(extractOrdersAndNextUrl))
                            .send()
                            .map(r => TempOrderHistoryResponseWrapper(symbol, r, times - 1)) pipeTo self

                case Some(uri) if !useAkkaHttp =>
                    useAkkaHttp = true
            }
        }

        case TempOrderHistoryResponseWrapper(symbol, Response(rawErrorBody, code, statusText, _, _), times) =>
            rawErrorBody.fold(
                _ => log.error("Error in getting order history (no url information): {} {}", code, statusText),
                (t: (List[Order], Option[String])) => {
                    val stockActor: ActorSelection =
                        context.actorSelection(s"../${DefaultWatchListActor.NAME}/$symbol")
                    t._1.foreach(o => stockActor ! copyOrderToStockActorOrder(o))
                    if (times > 0 && t._2.nonEmpty) self ! TempOrderHistoryRequest(symbol, times, t._2)
                }
            )
    }

    private def fetchPositionJsonString(): Either[String, String] = {
        implicit val backend: SttpBackend[Id, Nothing] = configureCoreJavaHttpBackend(config)
        positionRequest.send().body
    }

    private def extractOrdersAndNextUrl(js: String): (List[Order], Option[String]) = ???

    private def copyOrderToStockActorOrder(o: Order): StockActor.Order = StockActor.Order(
        o.averagePrice, o.createdAt, o.cumulativeQuantity, o.id, o.price, o.quantity, o.side, o.state, o.updatedAt
    )

    /**
      * @return none if nextUrl is none
      *         uri of https://api.robinhood.com/orders/?instrument=... if nextUrl contains an empty string
      *         uri of the nextUrl content if nextUrl contains something
      */
    private def buildUriOptionFromNextUrlOption(symbol: String, nextUrl: Option[String]): Option[Uri] = nextUrl match {
        case Some("") =>
            import com.softwaremill.sttp.Uri._
            StockDatabase.getInstrumentFromSymbol(symbol)
                    .map(_.instrument)
                    .map(i => {
                        val queryFragment =
                            QueryFragment.KeyValue("instrument", i, valueEncoding = QueryFragmentEncoding.All)
                        uri"$SERVER/orders/".queryFragment(queryFragment)
                    })
        case Some(url) => Some(uri"$url")
    }
}
