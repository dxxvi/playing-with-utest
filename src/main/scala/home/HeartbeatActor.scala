package home

import akka.actor.{Actor, Props, Timers}
import akka.event.{Logging, LoggingAdapter}
import akka.pattern.pipe
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.softwaremill.sttp._
import home.message.{Debug, M1, StockInfo, Tick}
import home.model.{LastTradePrice, Order}
import org.json4s._

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._

object HeartbeatActor {
    val NAME = "Heartbeat"

    def props(accessToken: String, symbols: List[String], stockDatabase: StockDatabase)
             (implicit be1: SttpBackend[Future, Source[ByteString, Any]],
                       be2: SttpBackend[Id, Nothing]): Props =
            Props(new HeartbeatActor(accessToken, symbols, be1, be2))

    case class HeartbeatTuple(ltps: List[LastTradePrice],
                              instrument2RecentStandardizedOrders: Map[String /* instrument */, List[Order]],
                              instrument2Position: Map[String /*instrument*/, (Double /*quantity*/, String /*account*/)])
}

class HeartbeatActor(accessToken: String,
                     symbols: List[String],
                     implicit val be1: SttpBackend[Future, Source[ByteString, Any]],
                                  be2: SttpBackend[Id, Nothing]) extends Actor with Timers with LastTradePriceUtil
        with OrderUtil with PositionUtil {
    import HeartbeatActor._
    implicit val log: LoggingAdapter = Logging(context.system, this)(_ => HeartbeatActor.NAME)
    implicit val ec: ExecutionContext = context.dispatcher

    timers.startPeriodicTimer(Tick, Tick, 4567.millis)

    override def receive: Receive = {
        case Tick =>
            val ltpsFuture = getLastTradePrices(accessToken, symbols)
            val recentStandardizedOrdersFuture = getRecentStandardizedOrders(accessToken)
            val positionsFuture = getAllPositions(accessToken)
            val heartbeatTupleFuture: Future[HeartbeatTuple] = for {
                ltps <- ltpsFuture
                recentStandardizedOrders <- recentStandardizedOrdersFuture
                positions <- positionsFuture
            } yield HeartbeatTuple(ltps, toOrderMap(recentStandardizedOrders), positions)
            heartbeatTupleFuture pipeTo self

        case HeartbeatTuple(ltps, instrument2Orders, instrument2PositionAccount) =>
            log.debug("got HeartbeatTuple, ltps: {}; instrument2Orders: {}, instrument2Position: {}",
                ltps, instrument2Orders, instrument2PositionAccount)
            ltps foreach { case ltp @ LastTradePrice(_, _, symbol, instrument, _, _) =>
                    val orders = instrument2Orders.getOrElse(instrument, Nil)
                    if (instrument2PositionAccount contains instrument) {
                        val stockActor = context.actorSelection(s"/user/$symbol")
                        stockActor ! StockInfo(ltp, instrument2PositionAccount(instrument), orders)
                    }
            }

        case Debug => sender() ! JObject()

        case M1.ClearHash => // ignored
    }

    private def toOrderMap(orders: List[Order]): Map[String /* instrument */, List[Order]] = orders.groupBy(_.instrument)
}
