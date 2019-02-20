package home.sparkjava

import java.time.LocalTime
import java.time.format.DateTimeFormatter

import akka.actor.{Actor, Props, Timers}
import akka.event.{LogSource, Logging, LoggingAdapter}
import akka.pattern.pipe
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.softwaremill.sttp.Uri.{QueryFragment, QueryFragmentEncoding}
import com.softwaremill.sttp._
import com.typesafe.config.Config
import message.Tick
import model.{BuySellOrderError, OrderElement, Orders}
import org.json4s._
import org.json4s.JsonAST.{JDouble, JInt, JObject, JString}
import org.json4s.native.Serialization

import scala.concurrent.Future

object OrderActor {
    val NAME = "orderActor"
    val BUY  = "buy"
    val SELL = "sell"

    case class BuySell(action: String, symbol: String, instrument: String, quantity: Int, price: Double)
    case class Cancel(orderId: String)
    case class OrderPositionStrings(orderString: String, positionString: String)
    case class BuySellOrderErrorResponse(bs: BuySell, requestBody: String, r: Response[BuySellOrderError])

    def props(config: Config): Props = Props(new OrderActor(config))
}

class OrderActor(config: Config) extends Actor with Timers with Util {
    import OrderActor._
    import context.dispatcher

    val SERVER: String = config.getString("server")
    implicit val logSource: LogSource[AnyRef] = (_: AnyRef) => NAME
    val log: LoggingAdapter = Logging(context.system, this)
    implicit val httpBackend: SttpBackend[Future, Source[ByteString, Any]] = configureAkkaHttpBackend(config)
    val account: String = if (config.hasPath("AccountNumber")) config.getString("AccountNumber") else "No account"
    var useAkkaHttp: Boolean = true

//    timers.startPeriodicTimer(Tick, Tick, Main.calculateShortDuration())

    override def receive: Receive = {
        case Tick =>
            val orderStringFuture: Future[String] = sttp
                    .auth.bearer(Main.accessToken)
                    .get(uri"${SERVER}orders/")
                    .response(asString)
                    .send()
                    .map {
                        case Response(Left(_), _, statusText, _, _) =>
                            log.error("Error in getting recent orders: {}", statusText)
                            """{"results":[]}"""
                        case Response(Right(s), _, _, _, _) => s
                    }
            val positionStringFuture: Future[String] = sttp
                    .auth.bearer(Main.accessToken)
                    .get(uri"${SERVER}positions/")
                    .response(asString)
                    .send()
                    .map {
                        case Response(Left(_), _, statusText, _, _) =>
                            log.error("Error in getting positions: {}", statusText)
                            """{"results":[]}"""
                        case Response(Right(s), _, _, _, _) => s
                    }
            val orderPositionResponses = for {
                orderString    <- orderStringFuture
                positionString <- positionStringFuture
            } yield OrderPositionStrings(orderString, positionString)
            orderPositionResponses pipeTo self

        case OrderPositionStrings(orderString, positionString) =>
            import org.json4s._
            import org.json4s.native.JsonMethods._

            val instrument2OrderList: Map[String, List[OrderElement]] =
                (parse(orderString) \ "results").asInstanceOf[JArray].arr
                        .map(Orders.toOrderElement)
                        .collect { case Some(orderElement) => orderElement }
                        .groupBy(_.instrument)

        case bs @ BuySell(action, symbol, instrument, quantity, price) =>
            val body: String = Serialization.write(JObject(
                "account" -> JString(s"${SERVER}accounts/$account/"),
                "instrument" -> JString(instrument),
                "symbol" -> JString(symbol),
                "type" -> JString("limit"),
                "time_in_force" -> JString("gfd"),
                "trigger" -> JString("immediate"),
                "price" -> JDouble(price),
                "quantity" -> JInt(quantity),
                "side" -> JString(action)
            ))(DefaultFormats)
            sttp
                    .auth.bearer(Main.accessToken)
                    .header("Content-Type", "application/json")
                    .body(body)
                    .post(uri"${SERVER}orders/")
                    .response(asString.map(BuySellOrderError.deserialize))
                    .send()
                    .map(r => BuySellOrderErrorResponse(bs, body, r)) pipeTo self
        case BuySellOrderErrorResponse(bs, requestBody, Response(rawErrorBody, code, statusText, _, _)) =>
            rawErrorBody fold (
                _ => {
                    val message = s"Error in ${bs.action}ing ${bs.quantity} ${bs.symbol} @ ${bs.price} $code $statusText"
                    context.actorSelection(s"../${WebSocketActor.NAME}") ! s"NOTICE: DANGER: $message"
                    log.error(message + s" request body: $requestBody")
                },
                a => a.non_field_errors foreach { error => {
                    val notice = s"NOTICE: DANGER: ${bs.action}ing ${bs.quantity} @ ${bs.price} $error"
                    log.error(notice)
                    context.actorSelection(s"../${WebSocketActor.NAME}") ! notice
                }}
            )
        case Cancel(orderId) =>
            sttp
                .auth.bearer(Main.accessToken)
                .post(uri"${SERVER}orders/$orderId/cancel/")
                .send()
    }
}
