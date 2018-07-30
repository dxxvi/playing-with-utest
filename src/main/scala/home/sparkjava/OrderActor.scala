package home.sparkjava

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

import scala.concurrent.duration._
import akka.actor.{Actor, ActorRef, Props, Timers}
import akka.http.javadsl.model.ContentTypes
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ContentType, HttpEntity, HttpMethods, HttpRequest, HttpResponse, MediaType, MediaTypes, RequestEntity, StatusCodes, Uri}
import akka.http.scaladsl.model.Uri.Query
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.settings.ConnectionPoolSettings
import akka.stream.{ActorMaterializer, ActorMaterializerSettings}
import akka.util.ByteString
import com.typesafe.config.Config
import home.sparkjava.model.Order
import org.apache.logging.log4j.ThreadContext
import org.apache.logging.log4j.scala.Logging

import scala.util.{Failure, Success}

object OrderActor {
    val NAME = "orderActor"
    def props(config: Config): Props = Props(new OrderActor(config))

    case class AllOrdersResponse(httpResponse: HttpResponse, symbol: String)
}

class OrderActor(config: Config) extends Actor with Timers with Logging with Util {
    import OrderActor._
    import spray.json._
    import akka.pattern.pipe
    import context.dispatcher

    implicit val materializer: ActorMaterializer = ActorMaterializer(ActorMaterializerSettings(context.system))

    val SERVER: String = config.getString("server")
    val authorization: String = if (config.hasPath("Authorization")) config.getString("Authorization") else "No token"
    val account: String =
        if (config.hasPath("AccountNumber")) s"${SERVER}accounts/${config.getString("AccountNumber")}/"
        else "No_account_number"
    val connectionPoolSettings: ConnectionPoolSettings = getConnectionPoolSettings(config, context.system)

    val symbols: collection.mutable.Set[String] = collection.mutable.Set[String]()
    val http = Http(context.system)

    val today: String = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
    var debug = false
    timers.startPeriodicTimer(Tick, Tick, 4019.millis)

    val _receive: Receive = {
        case x: AddSymbol => symbols += x.symbol.toUpperCase
        case x: RemoveSymbol => symbols -= x.symbol.toUpperCase
        case AllOrders.Get(symbol) =>
            Main.instrument2Symbol.collectFirst {
                case (instrument, _symbol) if _symbol == symbol => instrument
            } foreach { instrument =>
                val uri = Uri(SERVER + "orders/") withQuery Query(("instrument", instrument))
                val httpRequest = HttpRequest(uri = uri) withHeaders RawHeader("Authorization", authorization)
                http.singleRequest(httpRequest, settings = connectionPoolSettings)
                        .map {
                            AllOrdersResponse(_, symbol)
                        }
                        .pipeTo(self)
            }
        case AllOrdersResponse(httpResonse, symbol) =>
            val orderFilter: Order => Boolean = o => o.state == "filled" || o.state == "confirmed" || o.state == "unconfirmed"
            httpResonse match {
                case HttpResponse(StatusCodes.OK, _, entity, _) =>
                    entity.dataBytes.runFold(ByteString(""))(_ ++ _).foreach { body =>
                        context.actorSelection(s"../${MainActor.NAME}/symbol-$symbol") !
                          AllOrders.Here(getOrders(body.utf8String).filter(orderFilter))
                    }
                case HttpResponse(statusCode, _, entity, _) =>
                    entity.dataBytes.runFold(ByteString(""))(_ ++ _) foreach { body =>
                        logger.error(s"Error in getting orders for $symbol: $statusCode, body: ${body.utf8String}")
                    }
            }
        case Tick if symbols.nonEmpty =>
            val httpRequest = HttpRequest(uri = Uri(SERVER + "orders/")) withHeaders RawHeader("Authorization", authorization)
            http.singleRequest(httpRequest, settings = connectionPoolSettings) pipeTo self
        case HttpResponse(StatusCodes.OK, _, entity, _) =>
            entity.dataBytes.runFold(ByteString(""))(_ ++ _).foreach { body =>
                val orders: Vector[Order] = getOrders(body.utf8String)
                orders.foreach { order =>
                    Main.instrument2Symbol.get(order.instrument).foreach { symbol =>
                        context.actorSelection(s"../${MainActor.NAME}/symbol-$symbol") ! order
                    }
                }
                if (orders.nonEmpty && orders.last.createdAt.startsWith(today)) {
                    logger.debug("Need to follow the next url")
                }
            }
        case HttpResponse(statusCode, _, entity, _) =>
            entity.dataBytes.runFold(ByteString(""))(_ ++ _).foreach { body =>
                logger.error(s"Error in getting orders with updated_at: $statusCode, body: ${body.utf8String}")
            }
        case Tick =>  // do nothing
        case CancelOrder(orderId) =>
            val uri = Uri(SERVER + s"orders/$orderId/cancel/")
            val httpRequest = HttpRequest(HttpMethods.POST, uri) withHeaders RawHeader("Authorization", authorization)
            http.singleRequest(httpRequest, settings = connectionPoolSettings).onComplete {
                case Success(httpResponse) => httpResponse.entity.discardBytes()
                case Failure(exception) => logger.error(s"Unable to cancel order $orderId: ${exception.getMessage}")
            }
        case Buy(symbol, quantity, price) => sendBuySellRequest("buy", symbol, quantity, price)
        case Sell(symbol, quantity, price) => sendBuySellRequest("sell", symbol, quantity, price)
        case "DEBUG_ON" => debug = true
        case "DEBUG_OFF" => debug = false
        case x => logger.debug(s"Don't know what to do with $x yet")
    }

    val sideEffect: PartialFunction[Any, Any] = {
        case x =>
            ThreadContext.clearMap()
            x
    }

    override def receive: Receive = sideEffect andThen _receive

    private def sendBuySellRequest(action: String, symbol: String, quantity: Int, price: Double) {
        def createJsonString(action: String, instrument: String, symbol: String, quantity: Int, price: Double): String = {
            JsObject(
                "account" -> JsString(account),
                "instrument" -> JsString(instrument),
                "symbol" -> JsString(symbol),
                "type" -> JsString("limit"),
                "time_in_force" -> JsString("gfd"),
                "trigger" -> JsString("immediate"),
                "price" -> JsNumber(price),
                "quantity" -> JsNumber(quantity),
                "side" -> JsString(action)
            ).compactPrint
        }

        Main.instrument2Symbol.find(_._2 == symbol).foreach { tuple2 =>
            val byteArray: Array[Byte] = createJsonString(action, tuple2._1, tuple2._2, quantity, price).getBytes
            val entity: RequestEntity = HttpEntity(ContentType(MediaTypes.`application/json`), byteArray)
            val httpRequest = HttpRequest(HttpMethods.POST, Uri(SERVER + "orders/"), entity = entity)
                    .withHeaders(RawHeader("Authorization", authorization))
            http.singleRequest(httpRequest, settings = connectionPoolSettings).onComplete {
                case Success(httpResponse) =>
                    httpResponse.entity.dataBytes.runFold(ByteString(""))(_ ++ _).foreach { body =>
                        if (debug) logger.debug(s"Response of ${action}ing $quantity shares of $symbol at " +
                          s"$$$price/share ${body.utf8String}")
                    }
                case Failure(exception) => logger.error(s"$exception")
            }
            if (debug) logger.debug(s"Just $action $quantity shares of $symbol at $$$price/share")
        }
    }
}
