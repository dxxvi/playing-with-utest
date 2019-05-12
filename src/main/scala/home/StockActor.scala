package home

import java.security.MessageDigest

import akka.actor.{Actor, ActorRef, Props}
import akka.event.{Logging, LoggingAdapter}
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.softwaremill.sttp.{Id, SttpBackend}
import home.message.{Debug, M1, MakeOrder, MakeOrderDone, StockInfo}
import home.model.{LastTradePrice, Order, Stats}
import home.util.OrderOrdering
import org.json4s._
import org.json4s.native.Serialization

import scala.collection.mutable.ListBuffer
import scala.collection.{LinearSeq, mutable}
import scala.concurrent.{ExecutionContext, Future}

object StockActor {
    def props(accessToken: String,
              symbol: String,
              stats: Stats,
              standardizedOrders: List[Order],
              webSocketActorRef: ActorRef,
              ltps: LinearSeq[LastTradePrice])
             (implicit be1: SttpBackend[Future, Source[ByteString, Any]],
                       be2: SttpBackend[Id, Nothing]): Props =
        Props(new StockActor(accessToken, symbol, stats, standardizedOrders, webSocketActorRef, ltps, be1, be2))
}

class StockActor(accessToken: String,
                 symbol: String,
                 stats: Stats,
                 _standardizedOrders: List[Order],
                 webSocketActorRef: ActorRef,
                 _ltps: LinearSeq[LastTradePrice],
                 implicit val be1: SttpBackend[Future, Source[ByteString, Any]],
                              be2: SttpBackend[Id, Nothing])
        extends Actor with OrderUtil with StockActorUtil {
    implicit val log: LoggingAdapter = Logging(context.system, this)(_ => symbol)
    implicit val ec: ExecutionContext = context.dispatcher
    val md5Digest: MessageDigest = MessageDigest.getInstance("MD5") // needs to be here coz not thread-safe

    var m1Hash = ""
    var account = ""

    val ltps: ListBuffer[LastTradePrice] = ListBuffer.empty ++ _ltps
    if (ltps.nonEmpty) {
        var previousEMA = ltps.head.price
        ltps.foreach(ltp => {
            ltp.ema = LastTradePrice.alpha * ltp.price + (1 - LastTradePrice.alpha)*previousEMA
            previousEMA = ltp.ema
        })
    }

    // contains (un)confirmed, queued, filled, partially_filled only
    val standardizedOrders: mutable.TreeSet[Order] = mutable.TreeSet.empty[Order](OrderOrdering) ++= _standardizedOrders

    override def receive: Receive = {
        case StockInfo(ltp: LastTradePrice, quantityAccount: (Double, String), recentOrders: List[Order]) =>
            account = quantityAccount._2
            // check if we need to add ltp to ltps, calculate the ema for the new ltp
            if (ltps.isEmpty || ltps.last.updatedAt != ltp.updatedAt) {
                if (ltps.isEmpty)
                    ltp.ema = ltp.price
                else
                    ltp.ema = LastTradePrice.alpha * ltp.price + (1 - LastTradePrice.alpha) * ltps.last.ema
                ltps append ltp
            }
            stats.high = math.max(stats.high, ltp.price)
            stats.low  = math.min(stats.low, ltp.price)

            recentOrders.foreach(update(_, standardizedOrders))
            val effectiveOrders = getEffectiveOrders(quantityAccount._1, standardizedOrders.toList, log)
            val jObject = JObject(
                "symbol"   -> JString(symbol),
                "ltp"      -> JDouble(f"${ltp.price}%.2f".toDouble),
                "quantity" -> JInt(quantityAccount._1.toInt),
                "orders"   -> Order.toJArray(effectiveOrders),
                "stats"    -> stats.toJObject(ltp.price)
            )
            val m1 = Serialization.write(jObject)(DefaultFormats)
            val _m1Hash = new String(md5Digest.digest(m1.getBytes))
            if (_m1Hash != m1Hash) {
                m1Hash = _m1Hash
                webSocketActorRef ! M1(jObject)
            }

        case mo @ MakeOrder(side, quantity, price) =>
            if (ltps.isEmpty) {
                log.warning("Unable to make an order because of no instrument {}", mo)
            }
            else {
                val instrument = ltps.last.instrument
                makeOrder(accessToken, symbol.toUpperCase, instrument, side, quantity, account, price)
            }
            sender() ! MakeOrderDone

        case MakeOrderDone =>

        case M1.ClearHash => m1Hash = ""

        case Debug =>
            sender() ! JObject(
                "ltps" -> JArray(ltps.map(_.toJObject.removeField(t => t._1 == "symbol" || t._1 == "instrument")).toList),
                "stats" -> JString(stats.toString),
                "standardized-orders" -> Order.toJArray(standardizedOrders.toList)
            )
    }
}
