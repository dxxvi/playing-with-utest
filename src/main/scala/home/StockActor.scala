package home

import java.nio.file.{Files, Path}
import java.nio.file.StandardOpenOption._
import java.security.MessageDigest

import akka.actor.{Actor, ActorRef, Props}
import akka.event.{Logging, LoggingAdapter}
import akka.stream.scaladsl.Source
import akka.util.ByteString
import home.message.{Debug, M1, MakeOrder, MakeOrderDone, StockInfo}
import home.model.{LastTradePrice, Order, Quote, Stats, StatsCurrent}
import home.util.{OrderOrdering, SttpBackendUtil}
import com.softwaremill.sttp.{Id, SttpBackend}
import org.json4s._
import org.json4s.native.Serialization

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
    def smallest(n: Double): Int = F find (_ > n) getOrElse 1 // returns the smallest # in F which is > n or 1

    case class FiveMinQuotes(quotes: List[Quote])
}

class StockActor(accessToken: String,
                 symbol: String,
                 stats: Stats,
                 _standardizedOrders: List[Order],
                 webSocketActorRef: ActorRef,
                 _ltps: LinearSeq[LastTradePrice],
                 implicit val be1: SttpBackend[Future, Source[ByteString, Any]],
                 be2: SttpBackend[Id, Nothing])
        extends Actor with OrderUtil with StockActorUtil with SttpBackendUtil {
    import StockActor._
    implicit val log: LoggingAdapter = Logging(context.system, this)(_ => symbol)
    implicit val ec: ExecutionContext = context.dispatcher
    val md5Digest: MessageDigest = MessageDigest.getInstance("MD5") // needs to be here coz not thread-safe

    var m1Hash = ""
    var account = ""

    val ltps: mutable.SortedSet[LastTradePrice] = mutable.SortedSet.empty[LastTradePrice](LastTradePrice.Ordering) ++= _ltps
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
                ltps add ltp
                Files.write(Path.of(symbol), (ltp.toString + "\n").getBytes, CREATE, APPEND)
            }
            stats.high = math.max(stats.high, ltp.price)
            stats.low  = math.min(stats.low, ltp.price)

            recentOrders.foreach(update(_, standardizedOrders))
            val effectiveOrders = getEffectiveOrders(quantityAccount._1, standardizedOrders.toList, log) // TODO this has confirmed orders as well
            val statsCurrent:StatsCurrent = stats.toStatsCurrent(ltp.price)
            val jObject = JObject(
                "symbol"        -> JString(symbol),
                "ltp"           -> JDouble(f"${ltp.price}%.2f".toDouble),
                "quantity"      -> JInt(quantityAccount._1.toInt),
                "orders"        -> Order.toJArray(effectiveOrders),
                "stats"         -> statsCurrent.toJObject,
                "shouldBuy"     -> JBool(shouldBuy(statsCurrent)._1),
                "shouldSell"    -> JBool(shouldSell(statsCurrent)._1)
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
                makeOrder(accessToken, symbol.toUpperCase, instrument, side, quantity, account, price)(be1, ec, log)
            }
            sender() ! MakeOrderDone

        case MakeOrderDone =>

        case M1.ClearHash => m1Hash = ""

        case FiveMinQuotes(quotes) =>
            quotes.headOption foreach (q => stats.open = q.open)

        case Debug =>
            val fieldsToRemove = Set("symbol", "instrument")
            sender() ! JObject(
                "ltps" -> JArray(ltps.toList.map(_.toJObject.removeField(fieldsToRemove contains _._1))),
                "stats" -> JString(stats.toString),
                "standardized-orders" -> Order.toJArray(standardizedOrders.toList)
            )
    }

    private def fx(effectiveOrders: List[Order], statsCurrent: StatsCurrent, ltp: LastTradePrice): Unit = {
        val _shouldBuy = shouldBuy(statsCurrent)
        val _shouldSell = shouldSell(statsCurrent)
        if (!_shouldBuy._1 && !_shouldSell._1) return

        val (isBuying, isSelling) = effectiveOrders.foldLeft(Tuple2(false, false))((b, order) => (
                b._1 || (order.side == "buy"  && order.state != "filled"),
                b._2 || (order.side == "sell" && order.state != "filled")
        ))
        findLastOrder(effectiveOrders) match {
            case None =>
                if (!isBuying && shouldBuy(statsCurrent)._1) {
                    log.warning(_shouldBuy._2)
                    self ! MakeOrder("buy", smallest(10 / ltp.price), ltp.price)
                }

            case Some(lOrder) if lOrder.matchId.isEmpty && lOrder.side == "buy" => // buy again or sell
                val statsPrev = stats.toStatsCurrent(lOrder.price)
                if (!isBuying && _shouldBuy._1 && ltp.price < lOrder.price - math.max(.05, stats.delta/4)) {
                    log.warning(_shouldBuy._2)
                    self ! MakeOrder("buy", smallest(10 / ltp.price), ltp.price)
                }
                if (!isSelling && _shouldSell._1 && ltp.price > lOrder.price + math.max(.05, stats.delta/5)) {
                    log.warning(_shouldSell._2)
                    self ! MakeOrder("sell", lOrder.quantity.toInt, ltp.price)
                }

            case Some(lOrder) if lOrder.matchId.isEmpty && lOrder.side == "sell" => // TODO buy back or sell again
                val _shouldBuyBack = shouldBuyBack(statsCurrent)
                if (!isBuying && _shouldBuyBack._1 && ltp.price < lOrder.price - math.max(.05, stats.delta/5)) {
                    log.warning(_shouldBuyBack._2)
                    self ! MakeOrder("buy", lOrder.quantity.toInt, ltp.price)
                }

            case Some(lOrder) if lOrder.side == "buy" => // matchId is not empty; this is a buy back order
            case Some(lOrder) if lOrder.side == "sell" => // matchId is not empty; this order is done. Don't look at it.
        }
    }

    private def shouldBuy(statsCurrent: StatsCurrent): (Boolean, String /* reason */) = {
        val cond1 = statsCurrent.l1m < 115 || statsCurrent.l3m < 115
        val cond2 = statsCurrent.ocurr1m > 80 || statsCurrent.ocurr3m > 80
        val cond3 = statsCurrent.pccurr1m > 80 || statsCurrent.pccurr3m > 80
        val cond4 = statsCurrent.hcurr1m > 80 || statsCurrent.hcurr3m > 80
        val result = cond1 && (cond2 || cond3 || cond4)
        if (result) {
            val s1 = s"last month < ${200 - statsCurrent.l1m}%, last 3 months < ${200 - statsCurrent.l3m}%"
            val s2 = if (cond2) s" last month open-current ${statsCurrent.ocurr1m}%, 3 months ${statsCurrent.ocurr3m}%" else ""
            val s3 = if (cond3) s" last month previous close - current ${statsCurrent.pccurr1m}%, 3 months ${statsCurrent.pccurr3m}%" else ""
            val s4 = if (cond4) s" last month high - current ${statsCurrent.hcurr1m}%, 3 months ${statsCurrent.hcurr3m}%" else ""
            (true, s1 + s2 + s3 + s4)
        }
        else (false, "")
    }

    private def shouldSell(statsCurrent: StatsCurrent): (Boolean, String /* reason */) = {
        val cond1 = statsCurrent.h1m > 80 || statsCurrent.h3m > 80
        val cond2 = statsCurrent.currl1m > 80 || statsCurrent.currl3m > 80
        val cond3 = statsCurrent.curro1m > 80 || statsCurrent.curro3m > 80
        val cond4 = statsCurrent.currpc1m > 80 || statsCurrent.currpc3m > 80
        val result = cond1 && (cond2 || cond3 || cond4)
        if (result) {
            val s1 = s"last month > ${statsCurrent.h1m}%, last 3 months > ${statsCurrent.h3m}%"
            val s2 = if (cond2) s" current - low-of-today ${statsCurrent.currl1m}%, 3 months ${statsCurrent.currl3m}%" else ""
            val s3 = if (cond3) s" current-open ${statsCurrent.curro1m}%, 3 months ${statsCurrent.curro3m}%" else ""
            val s4 = if (cond3) s" current - previous-close ${statsCurrent.currpc1m}%, 3 months ${statsCurrent.currpc3m}%" else ""
            (true, s1 + s2 + s3 + s4)
        }
        else (false, "")
    }

    private def shouldBuyBack(statsCurrent: StatsCurrent): (Boolean, String /* reason */) = {
        val cond1 = statsCurrent.l1m < 150 || statsCurrent.l3m < 150
        val cond2 = statsCurrent.ocurr1m > 50 || statsCurrent.ocurr3m > 50
        val cond3 = statsCurrent.pccurr1m > 50 || statsCurrent.pccurr3m > 50
        val cond4 = statsCurrent.hcurr1m > 50 || statsCurrent.hcurr3m > 50
        val result = cond1 && (cond2 || cond3 || cond4)
        if (result) {
            val s1 = s"buy back: last month < ${200 - statsCurrent.l1m}%, last 3 months < ${200 - statsCurrent.l3m}%"
            val s2 = if (cond2) s" open-current ${statsCurrent.ocurr1m}%, 3 months ${statsCurrent.ocurr3m}%"
            val s3 = if (cond3) s" previous-close - current ${statsCurrent.pccurr1m}%, 3 months ${statsCurrent.pccurr3m}%"
            val s4 = if (cond4) s" high-current ${statsCurrent.hcurr1m}%, 3 months ${statsCurrent.hcurr3m}%"
            (true, s1 + s2 + s3 + s4)
        }
        else (false, "")
    }

    private def findLastOrder(effectiveOrders: List[Order]): Option[Order] = effectiveOrders.find(_.state == "filled")
}