package home.sparkjava

import akka.actor.{Actor, Props, Timers}
import akka.pattern.pipe
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.softwaremill.sttp.Uri.{QueryFragment, QueryFragmentEncoding}
import com.softwaremill.sttp._
import com.typesafe.config.Config
import message.{HistoricalOrders, Tick}
import model.{BuySellOrderError, OrderElement, Orders}
import org.apache.logging.log4j.ThreadContext
import org.json4s._
import org.json4s.JsonAST.{JDouble, JInt, JObject, JString}
import org.json4s.native.Serialization

import scala.concurrent.Future
import scala.concurrent.duration._

object OrderActor {
    val NAME = "orderActor"
    val BUY  = "buy"
    val SELL = "sell"

    case class BuySell(action: String, symbol: String, instrument: String, quantity: Int, price: Double)
    case class Cancel(orderId: String)

    case class HistoricalOrdersResponse(r: Response[Orders], ho: HistoricalOrders)
    case class OrdersResponse(r: Response[Orders])
    case class BuySellOrderErrorResponse(r: Response[BuySellOrderError])

    def props(config: Config): Props = Props(new OrderActor(config))
}

class OrderActor(config: Config) extends Actor with Timers with Util {
    import OrderActor._
    import context.dispatcher

    val SERVER: String = config.getString("server")
    val authorization: String = if (config.hasPath("Authorization")) config.getString("Authorization") else "No token"
    val account: String = if (config.hasPath("AccountNumber")) config.getString("AccountNumber") else "No account"
    implicit val httpBackend: SttpBackend[Future, Source[ByteString, Any]] = configureAkkaHttpBackend(config)
    var historicalOrdersCount = 0

    timers.startPeriodicTimer(Tick, Tick, 4.seconds)

    val _receive: Receive = {
        case Tick =>
            sttp.header("Authorization", authorization)
                    .get(uri"${SERVER}orders/")
                    .response(asString.map(Orders.deserialize))
                    .send()
                    .map(OrdersResponse) pipeTo self
        case OrdersResponse(Response(rawErrorBody, code, statusText, _, _)) => rawErrorBody fold (
                _ => logger.error(s"Error in getting recent orders $code $statusText"),
                a => a.results foreach { _.foreach(orderElement => {
                    orderElement.instrument.flatMap(Main.instrument2Symbol.get).foreach(symbol => {
                        context.actorSelection(s"../${MainActor.NAME}/symbol-$symbol") ! orderElement
                    })
                })}
        )

        case ho @ HistoricalOrders(_, instrument, _, _, next) =>
            if (historicalOrdersCount < 5) {
                val uri = if (next.isDefined) uri"${next.get}"
                    else uri"${SERVER}orders/".queryFragment(QueryFragment.KeyValue("instrument", instrument, valueEncoding = QueryFragmentEncoding.All))
                sttp.header("Authorization", authorization)
                        .get(uri)
                        .response(asString.map(Orders.deserialize))
                        .send()
                        .map(r => HistoricalOrdersResponse(r, ho)) pipeTo self
                historicalOrdersCount += 1
            }
        case HistoricalOrdersResponse(Response(rawErrorBody, code, statusText, _, _), HistoricalOrders(symbol, instrument, times, _orders, next)) =>
            historicalOrdersCount -= 1
            rawErrorBody.fold(
                a => logger.error(s"Error in getting historical orders $symbol ($code, $statusText $historicalOrdersCount) next $next"),
                a => {
                    if (a.results.isDefined && a.results.get.exists(_.cumulative_quantity.isEmpty))
                        logger.error(s"$symbol has some orders with empty cummulative quantity\n${a.results.get.map(_.toString).mkString("\n")}")
                    else {
                        val orders = _orders ++ a.results.getOrElse(List[OrderElement]())
                        val _times = times - 1
                        if (_times == 0 || a.next.isEmpty) {
                            context.actorSelection(s"../${MainActor.NAME}/symbol-$symbol") !
                                    HistoricalOrders(symbol, instrument, _times, orders, a.next)
                            logger.debug(s"Send back to StockActor HistoricalOrders($symbol, _, ${_times}, _, ${a.next})")
                        }
                        else {
                            self ! HistoricalOrders(symbol, instrument, _times, orders, a.next)
                            logger.debug(s"Send to self HistoricalOrders($symbol, _, ${_times}, _, ${a.next})")
                        }
                    }
                }
            )
        case BuySell(action, symbol, instrument, quantity, price) =>
            val body = Serialization.write(JObject(
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
                    .headers(("Content-Type", "application/json"), ("Authorization", authorization))
                    .body(body)
                    .post(uri"${SERVER}orders/")
                    .response(asString.map(BuySellOrderError.deserialize))
                    .send()
                    .map(BuySellOrderErrorResponse) pipeTo self
        case BuySellOrderErrorResponse(Response(rawErrorBody, code, statusText, _, _)) =>
            rawErrorBody fold (
                _ => logger.error(s"Error in buy/sell-ing $code $statusText"),
                a => a.non_field_errors foreach { error => {
                    logger.error(error)
                    context.actorSelection(s"../../${WebSocketActor.NAME}") ! s"NOTICE: INFO: $error"
                }}
            )
    }

    override def receive: Receive = sideEffect andThen _receive
    private def sideEffect: PartialFunction[Any, Any] = { case x => ThreadContext.put("symbol", OrderActor.NAME); x }
}
