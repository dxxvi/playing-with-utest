package home

import akka.actor.{Actor, Props, Timers}
import akka.event.{Logging, LoggingAdapter}
import akka.pattern.pipe
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.softwaremill.sttp._
import home.message.{Debug, M1, StockInfo, Tick}
import home.model.{LastTradePrice, Order, Quote}
import org.json4s._

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._

object HeartbeatActor {
    val NAME = "Heartbeat"

    val UNWANTED_LTP_FIELDS: Set[String] = Set("previousClose", "instrument", "ema")

    def props(accessTokenProvider: () => Option[String])
             (implicit be1: SttpBackend[Future, Source[ByteString, Any]],
                       be2: SttpBackend[Id, Nothing]): Props =
            Props(new HeartbeatActor(accessTokenProvider, be1, be2))

    case object OpenPrice
    case object Skip
    case class HeartbeatTuple(ltps: List[LastTradePrice],
                              instrument2RecentStandardizedOrders: Map[String /* instrument */, List[Order]],
                              instrument2Position: Map[String /*instrument*/, (Double /*quantity*/, String /*account*/)])
    case class FiveMinQuotes(list: List[(String, String, String, List[Quote])])
    case class AllSymbols(symbols: List[String])
}

class HeartbeatActor(accessTokenProvider: () => Option[String],
                     implicit val be1: SttpBackend[Future, Source[ByteString, Any]],
                                  be2: SttpBackend[Id, Nothing]) extends Actor with Timers with LastTradePriceUtil
        with OrderUtil with QuoteUtil with PositionUtil {
    import HeartbeatActor._
    implicit val log: LoggingAdapter = Logging(context.system, this)(_ => HeartbeatActor.NAME)
    implicit val ec: ExecutionContext = context.dispatcher

    var symbols: List[String] = Nil
    var _ltps: List[LastTradePrice] = Nil
    var _instrument2Orders: Map[String, List[Order]] = Map.empty
    var _instrument2Position: Map[String, (Double, String)] = Map.empty
    var skip: Boolean = false

    timers.startPeriodicTimer(Tick, Tick, 4567.millis)

    override def receive: Receive = {
        case Tick =>
            if (skip) {
                skip = false
            }
            else if (accessTokenProvider().isDefined && symbols.nonEmpty) {
                val accessToken = accessTokenProvider().get
                val ltpsFuture = getLastTradePrices(accessToken, symbols)(be1, ec, log)
                val recentStandardizedOrdersFuture = getRecentStandardizedOrders(accessToken)(be1, ec, log)
                val positionsFuture = getAllPositions(accessToken)(be1, ec, log)
                val heartbeatTupleFuture: Future[HeartbeatTuple] = for {
                    ltps <- ltpsFuture
                    recentStandardizedOrders <- recentStandardizedOrdersFuture
                    positions <- positionsFuture
                } yield HeartbeatTuple(ltps, toOrderMap(recentStandardizedOrders), positions)
                heartbeatTupleFuture pipeTo self
            }

        case HeartbeatTuple(ltps, instrument2Orders, instrument2PositionAccount) =>
            this._ltps = ltps
            this._instrument2Orders = instrument2Orders
            this._instrument2Position = instrument2PositionAccount
            ltps foreach { case ltp @ LastTradePrice(_, _, symbol, instrument, _, _) =>
                    val orders = instrument2Orders.getOrElse(instrument, Nil)
                    if (instrument2PositionAccount contains instrument) {
                        val stockActor = context.actorSelection(s"/user/$symbol")
                        stockActor ! StockInfo(ltp, instrument2PositionAccount(instrument), orders)
                    }
            }

        case Debug => sender() ! JObject(
            "ltps" -> JArray(_ltps.map(_.toJObject.removeField(t => UNWANTED_LTP_FIELDS contains t._1))),
            "instrument2Orders" -> JObject(_instrument2Orders.mapValues(Order.toJArray).toList),
            "instrument2Position" -> JObject(_instrument2Position.mapValues(t => JInt(t._1.toInt)).toList)
        )

        case Skip => skip = true

        case OpenPrice =>
            if (accessTokenProvider().isDefined && symbols.nonEmpty)
                get5minQuotes(accessTokenProvider().get, symbols)(be1, ec, log) map FiveMinQuotes pipeTo self

        case FiveMinQuotes(list) => list foreach {
            case (symbol, _, _, quotes) => context.actorSelection(s"/user/$symbol") ! StockActor.FiveMinQuotes(quotes)
        }

        case AllSymbols(_symbols) => symbols = _symbols

        case M1.ClearHash => // ignored
    }

    private def toOrderMap(orders: List[Order]): Map[String /* instrument */, List[Order]] = orders.groupBy(_.instrument)
}
