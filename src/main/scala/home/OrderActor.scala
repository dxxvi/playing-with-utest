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
    case object Debug extends OrderSealedTrait
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

    // timers.startPeriodicTimer(Tick, Tick, 4019.millis)

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
                    implicit val be: SttpBackend[Id, Nothing] = configureCoreJavaHttpBackend(config)
                    sttp.header(AUTHORIZATION, authorization).get(uri).send().body match {
                        case Right(js) =>
                            val t: (List[Order], Option[String]) = extractOrdersAndNextUrl(js)
                            sendOrdersToStockActor_CreateNextRequest(t, symbol, times - 1)
                        case Left(s) => log.error("Error in getting order history {}: {}", uri, s)
                    }
            }
        }

        case TempOrderHistoryResponseWrapper(symbol, Response(rawErrorBody, code, statusText, _, _), times) =>
            rawErrorBody.fold(
                _ => log.error("Error in getting order history (no url information): {} {}", code, statusText),
                (t: (List[Order], Option[String])) => sendOrdersToStockActor_CreateNextRequest(t, symbol, times)
            )

        case Debug => debug()
    }

    private def sendOrdersToStockActor_CreateNextRequest(t: (List[Order], Option[String]), symbol: String, times: Int) {
        val stockActor: ActorSelection = context.actorSelection(s"../${DefaultWatchListActor.NAME}/$symbol")
        t._1.foreach(o => stockActor ! copyOrderToStockActorOrder(o))
        if (times > 0 && t._2.nonEmpty) self ! TempOrderHistoryRequest(symbol, times, t._2)
    }

    private def fetchPositionJsonString(): Either[String, String] = {
        implicit val backend: SttpBackend[Id, Nothing] = configureCoreJavaHttpBackend(config)
        positionRequest.send().body
    }

    /**
      * @param js is like this
      * {
      *   "previous": null,
      *   "results": [
      *     {
      *       "updated_at": "2018-12-14T20:24:30.223556Z",
      *       "ref_id": null,
      *       "time_in_force": "gfd",
      *       "fees": "0.00",
      *       "cancel": null,
      *       "response_category": "unknown",
      *       "id": "c31b57e9-4094-4361-a8fc-5c8e76b488ba",
      *       "cumulative_quantity": "10.00000",
      *       "stop_price": null,
      *       "reject_reason": null,
      *       "instrument": "https://api.robinhood.com/instruments/cfa64e84-2864-45e7-aac9-fd02e7d1e369/",
      *       "state": "filled",
      *       "trigger": "immediate",
      *       "override_dtbp_checks": false,
      *       "type": "limit",
      *       "last_transaction_at": "2018-12-14T20:24:29.995000Z",
      *       "price": "12.84000000",
      *       "executions": [
      *         {
      *           "timestamp": "2018-12-14T20:24:29.986000Z",
      *           "price": "12.84000000",
      *           "settlement_date": "2018-12-18",
      *           "id": "b8e7b30a-0752-48b0-a4f6-45d4937ffbbe",
      *           "quantity": "1.00000"
      *         },
      *         {
      *           "timestamp": "2018-12-14T20:24:29.995000Z",
      *           "price": "12.84000000",
      *           "settlement_date": "2018-12-18",
      *           "id": "ddbea042-b328-4785-9d90-87b4ff767c78",
      *           "quantity": "9.00000"
      *         }
      *       ],
      *       "extended_hours": false,
      *       "account": "https://api.robinhood.com/accounts/5RY82436/",
      *       "url": "https://api.robinhood.com/orders/c31b57e9-4094-4361-a8fc-5c8e76b488ba/",
      *       "created_at": "2018-12-14T20:24:26.841574Z",
      *       "side": "buy",
      *       "override_day_trade_checks": false,
      *       "position": "https://api.robinhood.com/positions/5RY82436/cfa64e84-2864-45e7-aac9-fd02e7d1e369/",
      *       "average_price": "12.84000000",
      *       "quantity": "10.00000"
      *     },
      *     {
      *       "updated_at": "2018-07-27T23:18:44.134967Z",
      *       "ref_id": null,
      *       "time_in_force": "gfd",
      *       "fees": "0.02",
      *       "cancel": null,
      *       "response_category": "unknown",
      *       "id": "f3569b6e-e3a2-497a-9f5f-5555c07b4bb8",
      *       "cumulative_quantity": "8.00000",
      *       "stop_price": null,
      *       "reject_reason": null,
      *       "instrument": "https://api.robinhood.com/instruments/cfa64e84-2864-45e7-aac9-fd02e7d1e369/",
      *       "state": "filled",
      *       "trigger": "immediate",
      *       "override_dtbp_checks": false,
      *       "type": "limit",
      *       "last_transaction_at": "2018-07-27T14:24:57.707000Z",
      *       "price": "18.31000000",
      *       "executions": [
      *         {
      *           "timestamp": "2018-07-27T14:24:57.707000Z",
      *           "price": "18.31000000",
      *           "settlement_date": "2018-07-31",
      *           "id": "192db9ac-487a-4ba8-9626-1eb3837e3270",
      *           "quantity": "8.00000"
      *         }
      *       ],
      *       "extended_hours": false,
      *       "account": "https://api.robinhood.com/accounts/5RY82436/",
      *       "url": "https://api.robinhood.com/orders/f3569b6e-e3a2-497a-9f5f-5555c07b4bb8/",
      *       "created_at": "2018-07-27T14:11:40.076797Z",
      *       "side": "sell",
      *       "override_day_trade_checks": false,
      *       "position": "https://api.robinhood.com/positions/5RY82436/cfa64e84-2864-45e7-aac9-fd02e7d1e369/",
      *       "average_price": "18.31000000",
      *       "quantity": "8.00000"
      *     }
      *   ],
      *   "next": "https://api.robinhood.com/orders/?cursor=cD0yMDE4LTA3LTI3KzE0JTNBMTElM0E0MC4wNzY3OTclMkIwMCUzQTAw&instrument=https%3A%2F%2Fapi.robinhood.com%2Finstruments%2Fcfa64e84-2864-45e7-aac9-fd02e7d1e369%2F"
      * }
      */
    private def extractOrdersAndNextUrl(js: String): (List[Order], Option[String]) = {
        import org.json4s._
        import org.json4s.native.JsonMethods._
        val jValue: JValue = parse(js)
        val nextUrlOption: Option[String] = Util.fromJValueToOption[String](jValue \ "next")
        val list: List[(String, home.StockActor.Order)] = Util.extractSymbolAndOrder(js)
        (
                list.map(t => Order(
                    t._2.averagePrice,
                    t._2.createdAt,
                    t._2.cumulativeQuantity,
                    t._2.id,
                    t._2.price,
                    t._2.quantity,
                    t._2.side,
                    t._2.state,
                    t._2.updatedAt
                )),
                nextUrlOption
        )
    }

    private def copyOrderToStockActorOrder(o: Order): StockActor.Order = StockActor.Order(
        o.averagePrice, o.createdAt, o.cumulativeQuantity, o.id, o.price, o.quantity, o.side, o.state, o.updatedAt
    )

    /**
      * @return none if nextUrl is none
      *         uri of https://api.robinhood.com/orders/?instrument=... if nextUrl contains an empty string
      *         uri of the nextUrl content if nextUrl contains something
      */
    private def buildUriOptionFromNextUrlOption(symbol: String, nextUrl: Option[String]): Option[Uri] = nextUrl match {
        case Some("") | None =>
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

    private def debug() {
        log.info(s"$NAME debug information:")
    }
}
