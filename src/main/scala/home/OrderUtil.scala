package home

import java.nio.file.{Files, Path, StandardOpenOption}

import akka.event.LoggingAdapter
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.softwaremill.sttp.Uri.{QueryFragment, QueryFragmentEncoding}
import com.softwaremill.sttp._
import home.model.Order
import home.util.JsonUtil
import org.json4s._
import org.json4s.native.JsonMethods._
import org.json4s.native.Serialization

import scala.annotation.tailrec
import scala.concurrent.{ExecutionContext, Future}

trait OrderUtil extends JsonUtil with AppUtil {
    /**
      * @param orders will be checked if it is sorted descendingly in created_at. Exit the app if it's not.
      * @return only (un)confirmed, filled, partially-filled orders that make up this quantity
      */
    def getEffectiveOrders(_quantity: Double, orders: List[Order], log: LoggingAdapter): List[Order] = {
        // check if orders is sorted correctly
        orders.foldLeft("9999-12-31T23:59:59.654321Z")((ca, o) => {
            if (compareCreatedAts(ca, o.createdAt) <= 0) {
                log.error("Not sorted descendingly in created_at {}", orders)
                System.exit(-1)
            }
            o.createdAt
        })

        val quantity = _quantity.toInt
        var q = quantity
        val effectiveOrders = orders.toStream
                .map(standardizeOrder)
                .filter(o => Seq("filled", "confirmed", "queued").exists(o.state contains _))
                .takeWhile(o => {
                    val b = q != 0
                    if (o.state == "filled")
                        q += (if (o.side == "buy") -o.quantity.toInt else o.quantity.toInt)
                    o.state == "confirmed" || b
                })
                .toList
        assignMatchId(effectiveOrders)
        if (quantity == 0) { // effectiveOrders is empty but there might be confirmed orders
            orders.filter(_.state contains "confirmed").toList
        } else effectiveOrders
    }

    /**
      * Write to file order-history.json in the current directory
      */
    def writeOrderHistoryJsonToFile(accessToken: String, instruments: List[String])
                                   (implicit be: SttpBackend[Id, Nothing],
                                             ec: ExecutionContext,
                                             log: LoggingAdapter): Unit = {
        @tailrec
        def f(nextUriOption: Option[Uri], result: JArray, sleepFlag: Int = 0): JArray = {
            if (nextUriOption.isEmpty) return result
            val js: String = sttp
                    .auth.bearer(accessToken)
                    .get(nextUriOption.get)
                    .send()
                    .body match {
                        case Left(error) =>
                            log.error(s"${nextUriOption.get} returns error: {}", error)
                            """{"next": null, "results": []}"""
                        case Right(s) => s
                    }
            val jValue = parse(js)
            val nresult = JArray(result.arr ++ (jValue \ "results").asInstanceOf[JArray].arr)
            fromJValueToOption[String](jValue \ "next") match {
                case None => nresult
                case Some(url) =>
                    if (sleepFlag == 0) Thread.sleep(3000)
                    f(Some(uri"$url"), nresult, (sleepFlag + 1) % 3)
            }
        }

        val jArray = instruments
                .map(instrument => {
                    val ins = s"https://api.robinhood.com/instruments/$instrument/"
                    val nextUriOption: Option[Uri] = Some(
                        uri"https://api.robinhood.com/orders/"
                                .queryFragment(QueryFragment.KeyValue("instrument", ins, valueEncoding = QueryFragmentEncoding.All))
                    )
                    f(nextUriOption, JArray(Nil))
                })
                .foldLeft(JArray(Nil))((ja1, ja2) => JArray(ja1.arr ++ ja2.arr))
        implicit val defaultFormats: DefaultFormats = DefaultFormats
        Files.write(
            Path.of("order-history.json"),
            Serialization.writePretty(jArray).getBytes(),
            StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING
        )
    }

    /**
      * The returned json is like the one with extractNextAndOrders
      * @param instrument is like e39ed23a-7bd1-4587-b060-71988d9ef483 (Tesla)
      * @return including cancelled orders
      */
    def getAllStandardizedOrdersForInstrument(accessToken: String, instrument: String)
                                             (implicit be: SttpBackend[Id, Nothing],
                                                       ec: ExecutionContext,
                                                       log: LoggingAdapter): List[Order] = {
        @tailrec
        def f(nextUriOption: Option[Uri], result: List[Order], sleepFlag: Int = 0): List[Order] = {
            if (nextUriOption.isEmpty) return result
            val x: (Option[String], List[Order]) = sttp
                    .auth.bearer(accessToken)
                    .get(nextUriOption.get)
                    .send()
                    .body match {
                        case Left(error) =>
                            log.error(s"${nextUriOption.get} returns error: {}", error)
                            extractNextAndOrders("""{"results": [], "next": null}""")
                        case Right(s) => extractNextAndOrders(s)
                    }
            if (x._1.isEmpty) result ++ x._2.map(standardizeOrder)
            else {
                if (sleepFlag == 0) Thread.sleep(4000)
                f(Some(uri"${x._1.get}"), result ++ x._2.map(standardizeOrder), (sleepFlag + 1) % 3)
            }
        }

        val ins = s"https://api.robinhood.com/instruments/$instrument/"
        val nextUriOption: Option[Uri] = Some(
            uri"https://api.robinhood.com/orders/"
                .queryFragment(QueryFragment.KeyValue("instrument", ins, valueEncoding = QueryFragmentEncoding.All))
                .queryFragment(QueryFragment.KeyValue("page_size", 200.toString))
        )
        f(nextUriOption, Nil)
    }

    def getRecentStandardizedOrders(accessToken: String)
                                   (implicit be: SttpBackend[Future, Source[ByteString, Any]],
                                             ec: ExecutionContext,
                                             log: LoggingAdapter): Future[List[Order]] = {
        sttp
                .auth.bearer(accessToken)
                .get(uri"https://api.robinhood.com/orders/")
                .response(asString)
                .send()
                .collect {
                    case Response(Left(_), _, statusText, _, _) =>
                        log.error("/orders/ returns errors: {}, so skip the next heart beat", statusText)
                        val heartbeatActor = Main.actorSystem.actorSelection(s"/user/${HeartbeatActor.NAME}")
                        heartbeatActor ! HeartbeatActor.Skip
                        """{"result": []}"""
                    case Response(Right(s), _, _, _, _) => s
                }
                .map(extractNextAndOrders)
                .map(t => t._2.map(standardizeOrder))
    }

    // erase all the existing matchId's
    def assignMatchId(_orders: List[Order]): Unit = {
        _orders.foreach(o => o.matchId = None)

        def areMatchedOrders(o1: Order, o2: Order): Boolean =
            if (o1.side == "buy" && o2.side == "sell" && o1.price < o2.price && o1.quantity == o2.quantity) true
            else if (o1.side == "sell" && o2.side == "buy" && o1.price > o2.price && o1.quantity == o2.quantity) true
            else false

        def f(orders: Array[Order]): Unit = {
            val n = orders.length
            var i = 0
            while (i + 1 < n)
                if (areMatchedOrders(orders(i), orders(i + 1))) {
                    if (i + 2 < n && areMatchedOrders(orders(i + 1), orders(i + 2))) {
                        if (orders(i).side == "buy" && orders(i).price < orders(i + 2).price) i += 1
                        else if (orders(i).side == "sell" && orders(i).price > orders(i + 2).price) i += 1
                    }
                    val matchId = putTogetherUuid(orders(i).id, orders(i + 1).id)
                    orders(i).matchId = Some(matchId)
                    orders(i + 1).matchId = Some(matchId)
                    f(orders.slice(0, i) ++ orders.slice(i + 2, n))
                    i = n // so that we can break
                }
                else i += 1
        }

        val orders = _orders.filter(_.state contains "filled").toArray
        f(orders)
        var i = 0
        var j = 0
        while (i < orders.length - 1) {
            j = i + 1
            while (orders(i).matchId.isEmpty && j < orders.length) {
                if (orders(j).matchId.isEmpty && areMatchedOrders(orders(i), orders(j))) {
                    val matchId = putTogetherUuid(orders(i).id, orders(j).id)
                    orders(i).matchId = Some(matchId)
                    orders(j).matchId = Some(matchId)
                    j = orders.length // so that we can break this while loop
                }
                j += 1
            }
            i += 1
        }
    }

    /**
      * @return if o is cancelled (state) and cumulativeQuantity > 0, return a filled order with quantiy equal to that
      *         cumulativeQuantity
      */
    def standardizeOrder(o: Order): Order =
        if (o.state == "cancelled" && !o.cumulativeQuantity.isNaN && o.cumulativeQuantity > 0)
            o.copy(state = "filled", cumulativeQuantity = 0, quantity = o.cumulativeQuantity)
        else if (o.state.contains("partial") && !o.cumulativeQuantity.isNaN && o.cumulativeQuantity > 0)
            o.copy(quantity = o.cumulativeQuantity)
        else o

    def cancelOrder(accessToken: String, orderId: String)
                   (implicit be: SttpBackend[Id, Nothing],
                             ec: ExecutionContext,
                             log: LoggingAdapter): String =
        sttp
                .auth.bearer(accessToken)
                .post(uri"https://api.robinhood.com/orders/$orderId/cancel/")
                .send()
                .body match {
            case Right(_) => "no problem"
            case Left(s) => s
        }

    def makeOrder(accessToken: String, symbol: String, instrument: String, side: String, quantity: Int, account: String,
                  price: Double)
                 (implicit be: SttpBackend[Future, Source[ByteString, Any]],
                           ec: ExecutionContext,
                           log: LoggingAdapter): Unit /*Future[Response[String]]*/ = {
        log.warning(s"""$side $quantity $symbol at $price""")
/*
        val jObject = JObject(
            "account"       -> JString(account),
            "instrument"    -> JString(s"https://api.robinhood.com/instruments/$instrument/"),
            "symbol"        -> JString(symbol),
            "type"          -> JString("limit"),
            "time_in_force" -> JString("gfd"),
            "trigger"       -> JString("immediate"),
            "price"         -> JDouble(price),
            "quantity"      -> JInt(quantity),
            "side"          -> JString(side)
        )
        sttp
                .auth.bearer(accessToken)
                .header(HeaderNames.ContentType, MediaTypes.Json)
                .body(Serialization.write(jObject)(DefaultFormats))
                .post(uri"https://api.robinhood.com/orders/")
                .response(asString)
                .send()
*/
    }

    /**
      * @param s is like
      * {
      *   "previous": null,
      *   "results": [
      *     {
      *       "updated_at": "2018-12-19T19:58:54.189248Z",
      *       "ref_id": null,
      *       "time_in_force": "gfd",
      *       "fees": "0.00",
      *       "cancel": null,
      *       "response_category": "unknown",
      *       "id": "6fae5051-5fad-4215-9091-4c60d6916a4a",
      *       "cumulative_quantity": "12.00000",
      *       "stop_price": null,
      *       "reject_reason": null,
      *       "instrument": "https://api.robinhood.com/instruments/3d9a6798-df9e-4e29-90e3-3184f2379471/",
      *       "state": "filled",
      *       "trigger": "immediate",
      *       "override_dtbp_checks": false,
      *       "type": "limit",
      *       "last_transaction_at": "2018-12-19T19:58:53.930000Z",
      *       "price": "3.77000000",
      *       "executions": [
      *         {
      *           "timestamp": "2018-12-19T19:58:53.926000Z",
      *           "price": "3.77000000",
      *           "settlement_date": "2018-12-21",
      *           "id": "fb646d63-f6bd-42db-b4f6-b3b5be76a5a8",
      *           "quantity": "1.00000"
      *         },
      *         {
      *           "timestamp": "2018-12-19T19:58:53.930000Z",
      *           "price": "3.77000000",
      *           "settlement_date": "2018-12-21",
      *           "id": "2a308feb-e887-4992-a12f-eeef3127eded",
      *           "quantity": "11.00000"
      *         }
      *       ],
      *       "extended_hours": false,
      *       "account": "https://api.robinhood.com/accounts/5RY82436/",
      *       "url": "https://api.robinhood.com/orders/6fae5051-5fad-4215-9091-4c60d6916a4a/",
      *       "created_at": "2018-12-19T19:41:10.253195Z",
      *       "side": "buy",
      *       "override_day_trade_checks": false,
      *       "position": "https://api.robinhood.com/positions/5RY82436/3d9a6798-df9e-4e29-90e3-3184f2379471/",
      *       "average_price": "3.77000000",
      *       "quantity": "12.00000"
      *     },
      *     {
      *       "updated_at": "2018-12-19T19:21:54.731406Z",
      *       "ref_id": null,
      *       "time_in_force": "gfd",
      *       "fees": "0.04",
      *       "cancel": null,
      *       "response_category": "unknown",
      *       "id": "3b1ca207-26f1-4c08-91df-7064d051d157",
      *       "cumulative_quantity": "3.00000",
      *       "stop_price": null,
      *       "reject_reason": null,
      *       "instrument": "https://api.robinhood.com/instruments/ebab2398-028d-4939-9f1d-13bf38f81c50/",
      *       "state": "filled",
      *       "trigger": "immediate",
      *       "override_dtbp_checks": false,
      *       "type": "limit",
      *       "last_transaction_at": "2018-12-19T19:21:54.493000Z",
      *       "price": "136.17000000",
      *       "executions": [
      *         {
      *           "timestamp": "2018-12-19T19:21:54.483000Z",
      *           "price": "136.17000000",
      *           "settlement_date": "2018-12-21",
      *           "id": "5644059d-097d-4140-8145-d16a84d10c18",
      *           "quantity": "1.00000"
      *         },
      *         {
      *           "timestamp": "2018-12-19T19:21:54.493000Z",
      *           "price": "136.17000000",
      *           "settlement_date": "2018-12-21",
      *           "id": "149d72ca-1b67-468a-bd23-ce01c911ada7",
      *           "quantity": "2.00000"
      *         }
      *       ],
      *       "extended_hours": false,
      *       "account": "https://api.robinhood.com/accounts/5RY82436/",
      *       "url": "https://api.robinhood.com/orders/3b1ca207-26f1-4c08-91df-7064d051d157/",
      *       "created_at": "2018-12-19T19:21:54.194476Z",
      *       "side": "sell",
      *       "override_day_trade_checks": false,
      *       "position": "https://api.robinhood.com/positions/5RY82436/ebab2398-028d-4939-9f1d-13bf38f81c50/",
      *       "average_price": "136.17000000",
      *       "quantity": "3.00000"
      *     },
      *     {
      *       "updated_at": "2018-12-19T20:04:03.583948Z",
      *       "ref_id": null,
      *       "time_in_force": "gfd",
      *       "fees": "0.00",
      *       "cancel": null,
      *       "response_category": "unknown",
      *       "id": "e73ee4a5-3917-4739-88bd-e314641d2800",
      *       "cumulative_quantity": "0.00000",
      *       "stop_price": null,
      *       "reject_reason": null,
      *       "instrument": "https://api.robinhood.com/instruments/dad8fa2c-1e8d-4cb9-b354-1f0b91a4193e/",
      *       "state": "cancelled",
      *       "trigger": "immediate",
      *       "override_dtbp_checks": false,
      *       "type": "limit",
      *       "last_transaction_at": "2018-12-19T20:04:03.368000Z",
      *       "price": "17.25000000",
      *       "executions": [],
      *       "extended_hours": false,
      *       "account": "https://api.robinhood.com/accounts/5RY82436/",
      *       "url": "https://api.robinhood.com/orders/e73ee4a5-3917-4739-88bd-e314641d2800/",
      *       "created_at": "2018-12-19T19:32:58.272936Z",
      *       "side": "sell",
      *       "override_day_trade_checks": false,
      *       "position": "https://api.robinhood.com/positions/5RY82436/dad8fa2c-1e8d-4cb9-b354-1f0b91a4193e/",
      *       "average_price": null,
      *       "quantity": "15.00000"
      *     },
      *     {
      *         "account": "https://api.robinhood.com/accounts/5SF67761/",
      *         "average_price": "16.57010000",
      *         "cancel": null,
      *         "created_at": "2018-10-02T18:08:54.285510Z",
      *         "cumulative_quantity": "30.00000",
      *         "executions": [
      *             {
      *                 "id": "a2cbac65-5796-4da1-89b1-f923ca4911e4",
      *                 "price": "16.57010000",
      *                 "quantity": "30.00000",
      *                 "settlement_date": "2018-10-04",
      *                 "timestamp": "2018-10-02T18:08:54.711000Z"
      *             }
      *         ],
      *         "extended_hours": false,
      *         "fees": "0.02",
      *         "id": "ba5c7f20-b9ba-4356-9f0e-c9009c037e87",
      *         "instrument": "https://api.robinhood.com/instruments/8e08c691-869f-482c-8bed-39d026215a85/",
      *         "last_transaction_at": "2018-10-02T18:08:54.711000Z",
      *         "override_day_trade_checks": false,
      *         "override_dtbp_checks": false,
      *         "position": "https://api.robinhood.com/positions/5SF67761/8e08c691-869f-482c-8bed-39d026215a85/",
      *         "price": null,                                                                       NOTE: price is null
      *         "quantity": "30.00000",
      *         "ref_id": "f212cdf3-11a2-4444-9de6-6c1fb8340faf",
      *         "reject_reason": null,
      *         "response_category": "unknown",
      *         "side": "sell",
      *         "state": "filled",
      *         "stop_price": null,
      *         "time_in_force": "gfd",
      *         "trigger": "immediate",
      *         "type": "market",
      *         "updated_at": "2018-10-02T18:08:54.934685Z",
      *         "url": "https://api.robinhood.com/orders/ba5c7f20-b9ba-4356-9f0e-c9009c037e87/"
      *     }
      *   ],
      *   "next": "https://api.robinhood.com/orders/?cursor=cD0yMDE4LTEyLTE4KzE5JTNBMjUlM0ExNy41MTgzNzQlMkIwMCUzQTAw"
      * }
      */
    private def extractNextAndOrders(s: String): (Option[String] /* nextUrl */, List[Order]) = {
        val jValue = parse(s)
        val orders: List[Order] = jValue \ "results" match {
            case JArray(arr) => arr
                    .map(jv =>
                        for {
                            instrument         <- fromJValueToOption[String](jv \ "instrument")
                            createdAt          <- fromJValueToOption[String](jv \ "created_at")
                            cumulativeQuantity <- fromJValueToOption[Double](jv \ "cumulative_quantity")
                            id                 <- fromJValueToOption[String](jv \ "id")
                            price              <- fromJValueToOption[Double](jv \ "price") // price can be null (see the ex above)
                            quantity           <- fromJValueToOption[Double](jv \ "quantity")
                            side               <- fromJValueToOption[String](jv \ "side")
                            state              <- fromJValueToOption[String](jv \ "state")
                            updatedAt          <- fromJValueToOption[String](jv \ "updated_at")
                        } yield Order(
                            createdAt,
                            id,
                            side,
                            extractInstrument(instrument),
                            if (price.isNaN) calculatePriceFromExecutions(jv \ "executions") else price,
                            state,
                            cumulativeQuantity,
                            quantity,
                            updatedAt
                        )
                    )
                    .collect { case Some(tuple) => tuple }
            case _ => Nil
        }
        val next: Option[String] = fromJValueToOption[String](jValue \ "next")
        (next, orders)
    }

    /**
      * @param executions is like
      *         [
      *           {
      *             "id": "a2cbac65-5796-4da1-89b1-f923ca4911e4",
      *             "price": "16.57010000",
      *             "quantity": "30.00000",
      *             "settlement_date": "2018-10-04",
      *             "timestamp": "2018-10-02T18:08:54.711000Z"
      *           }
      *         ]
      */
    private def calculatePriceFromExecutions(executions: JValue): Double = executions match {
        case JArray(arr) =>
            val x: (Double, Double) = arr
                .map(jv => {
                    for {
                        price    <- fromJValueToOption[Double](jv \ "price")
                        quantity <- fromJValueToOption[Double](jv \ "quantity")
                    } yield (price, quantity)
                })
                .collect { case Some(t) => t }
                .foldLeft((0D, 0D))((b, c) => (b._1 + c._1 * c._2, b._2 + c._2))
            if (x._2 == 0) Double.NaN else x._1 / x._2
        case _ => Double.NaN
    }
}
