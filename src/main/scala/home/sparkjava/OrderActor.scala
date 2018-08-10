package home.sparkjava

import akka.actor.{Actor, Props, Timers}
import akka.pattern.pipe
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.softwaremill.sttp._
import com.typesafe.config.Config

import home.sparkjava.message.{HistoricalOrders, Tick}
import model.{Orders, OrderElement}

import scala.concurrent.Future
import scala.concurrent.duration._

object OrderActor {
    val NAME = "orderActor"

    case class Buy(symbol: String, quantity: Int, price: Double)
    case class Sell(symbol: String, quantity: Int, price: Double)
    case class Cancel(orderId: String)

    case class HistoricalOrdersResponse(r: Response[Orders], ho: HistoricalOrders)

    def props(config: Config): Props = Props(new OrderActor(config))
}

class OrderActor(config: Config) extends Actor with Timers with Util {
    import OrderActor._
    import context.dispatcher

    val SERVER: String = config.getString("server")
    val authorization: String = if (config.hasPath("Authorization")) config.getString("Authorization") else "No token"
    implicit val httpBackend: SttpBackend[Future, Source[ByteString, Any]] = configureAkkaHttpBackend(config)
    var historicalOrdersCount = 0

    timers.startPeriodicTimer(Tick, Tick, 4.seconds)

    val _receive: Receive = {
        case Tick =>
        case ho @ HistoricalOrders(symbol, instrument, times, orders, next) =>
            if (historicalOrdersCount < 5) {
                val u = next.getOrElse(s"${SERVER}orders/?instrument=" + encodeUrl(instrument))
                logger.debug(s"HistoricalOrder uri: $u")
                sttp.header("Authorization", authorization)
                        .get(uri"$u")
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
            )
    }

    override def receive: Receive = Main.clearThreadContextMap andThen _receive
}
