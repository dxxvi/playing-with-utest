package home

import java.security.MessageDigest

import akka.actor.{Actor, ActorRef, Props}
import akka.event.{Logging, LoggingAdapter}
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.softwaremill.sttp.{Id, SttpBackend}
import home.message.{Debug, M1, MakeOrder, MakeOrderDone, StockInfo}
import home.model.{LastTradePrice, Order, Stats, StatsCurrent}
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

    private val F: Array[Int] = Array(1, 2, 3, 5, 8, 13, 21, 34, 55, 89)
    def smallest(n: Double): Int = F find (_ > n) getOrElse 1 // returns the smallest in F > n or 1
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
    import StockActor._
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
            val statsCurrent = stats.toStatsCurrent(ltp.price)
            val jObject = JObject(
                "symbol"     -> JString(symbol),
                "ltp"        -> JDouble(f"${ltp.price}%.2f".toDouble),
                "quantity"   -> JInt(quantityAccount._1.toInt),
                "orders"     -> Order.toJArray(effectiveOrders),
                "stats"      -> statsCurrent.toJObject,
                "shouldBuy"  -> JBool(shouldBuy(statsCurrent)),
                "shouldSell" -> JBool(shouldSell(statsCurrent))
            )
            val m1 = Serialization.write(jObject)(DefaultFormats)
            val _m1Hash = new String(md5Digest.digest(m1.getBytes))
            if (_m1Hash != m1Hash) {
                m1Hash = _m1Hash
                webSocketActorRef ! M1(jObject)
                fx(effectiveOrders, statsCurrent, ltp)
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

    private def fx(effectiveOrders: List[Order], statsCurrent: StatsCurrent, ltp: LastTradePrice): Unit = {
        val _shouldBuy = shouldBuy(statsCurrent)
        val _shouldSell = shouldSell(statsCurrent)
        if (!_shouldBuy && !_shouldSell) return

        val (isBuying, isSelling) = effectiveOrders.foldLeft((false, false))((b, order) => (
                b._1 || (order.side == "buy"  && order.state != "filled"),
                b._2 || (order.side == "sell" && order.state != "filled")
        ))
        effectiveOrders.find(_.state == "filled") match {
            case None =>
                if (!isBuying && shouldBuy(statsCurrent)) self ! MakeOrder("buy", smallest(10 / ltp.price), ltp.price)

            case Some(lOrder) if lOrder.matchId.isEmpty && lOrder.side == "buy" => // buy again or sell
                if (!isBuying && _shouldBuy && ltp.price < lOrder.price - math.max(.05, stats.delta/4))
                    MakeOrder("buy", smallest(10 / ltp.price), ltp.price)
                if (!isSelling && _shouldSell && ltp.price > lOrder.price + math.max(.05, stats.delta/5))
                    MakeOrder("sell", lOrder.quantity.toInt, ltp.price)

            case Some(lOrder) if lOrder.matchId.isEmpty && lOrder.side == "sell" => // TODO buy back or sell again
                if (!isBuying && shouldBuyBack(statsCurrent) && ltp.price < lOrder.price - math.max(.05, stats.delta/5))
                    MakeOrder("buy", lOrder.quantity.toInt, ltp.price)

            case Some(lOrder) if lOrder.side == "buy" => // matchId is not empty; this is a buy back order
            case Some(lOrder) if lOrder.side == "sell" => // matchId is not empty; this order is done. Don't look at it.
        }
    }

    private def shouldBuy(statsCurrent: StatsCurrent): Boolean = {
        val cond1 = statsCurrent.l1m < 15 || statsCurrent.l3m < 15
        val cond2 = statsCurrent.ocurr1m > 80 || statsCurrent.ocurr3m > 80
        val cond3 = statsCurrent.pccurr1m > 80 || statsCurrent.pccurr3m > 80
        val cond4 = statsCurrent.hcurr1m > 80 || statsCurrent.hcurr3m > 80
        cond1 && (cond2 || cond3 || cond4)
    }

    private def shouldSell(statsCurrent: StatsCurrent): Boolean = {
        val cond1 = statsCurrent.h1m > 80 || statsCurrent.h3m > 80
        val cond2 = statsCurrent.currl1m > 80 || statsCurrent.currl3m > 80
        val cond3 = statsCurrent.curro1m > 80 || statsCurrent.curro3m > 80
        val cond4 = statsCurrent.currpc1m > 80 || statsCurrent.currpc3m > 80
        cond1 && (cond2 || cond3 || cond4)
    }

    private def shouldBuyBack(statsCurrent: StatsCurrent): Boolean = {
        val cond1 = statsCurrent.l1m < 80 || statsCurrent.l3m < 80
        val cond2 = statsCurrent.ocurr1m > 50 || statsCurrent.ocurr3m > 50
        val cond3 = statsCurrent.pccurr1m > 50 || statsCurrent.pccurr3m > 50
        val cond4 = statsCurrent.hcurr1m > 50 || statsCurrent.hcurr3m > 50
        cond1 && (cond2 || cond3 || cond4)
    }
}
