package home

import java.time.format.DateTimeFormatter

import akka.actor.{Actor, Timers}

object StockActor {
    import akka.actor.Props

    sealed trait StockSealedTrait                // TODO what's the purpose of this sealed trait?
    case object Tick extends StockSealedTrait
    case class Quote(lastTradePrice: Double, updatedAt: String) extends StockSealedTrait
    case class DailyQuoteListWrapper(list: List[QuoteActor.DailyQuote]) extends StockSealedTrait
    case class Order(
                    var averagePrice: Double,
                    createdAt: String,
                    var cumulativeQuantity: Double,
                    id: String,
                    var price: Double,
                    var quantity: Double,
                    side: String,
                    var state: String,
                    var updatedAt: String,
                    var matchId: Option[String] = None
                    ) extends StockSealedTrait {
        override def hashCode(): Int = id.hashCode

        override def equals(obj: Any): Boolean = obj match {
            case o: Order => this.id == o.id
            case _ => false
        }
    }
    case class Position(quantity: Double) extends StockSealedTrait
    case object Debug extends StockSealedTrait

    object OrderState extends Enumeration {
        val CONFIRM /* including confirmed and unconfirmed */, CANCEL, QUEUE, PARTIAL, FILL = Value
    }

    object CreatedAtOrderingForOrders extends Ordering[Order] {
        import java.time.LocalDateTime

        private val regex = raw"Z$$".r
        private def utcDateTimeToLocalDateTime(utcDateTime: String): LocalDateTime =
            LocalDateTime.parse(regex.replaceAllIn(utcDateTime, ""), DateTimeFormatter.ISO_LOCAL_DATE_TIME)

        override def compare(x: Order, y: Order): Int =
            utcDateTimeToLocalDateTime(x.createdAt) compareTo utcDateTimeToLocalDateTime(y.createdAt)
    }

    def props(symbol: String): Props = Props(new StockActor(symbol))
}

class StockActor(symbol: String) extends Actor with Timers {
    import StockActor._
    import scala.concurrent.duration._
    import akka.event._

    implicit val logSource: LogSource[AnyRef] = (_: AnyRef) => symbol
    val log: LoggingAdapter = Logging(context.system, this)

    var ltp: Double = Double.NaN                 // last trade price
    var openPrice: Double = Double.NaN
    var todayHigh: Double = Double.NaN
    var todayLow:  Double = Double.NaN

    var HL: List[QuoteActor.DailyQuote] = Nil
    var HL49: Double = Double.NaN
    var HL31: Double = Double.NaN
    var HL10: Double = Double.NaN

    var HO: List[QuoteActor.DailyQuote] = Nil
    var HO49: Double = Double.NaN
    var HO10: Double = Double.NaN

    var OL: List[QuoteActor.DailyQuote] = Nil
    var OL49: Double = Double.NaN
    var OL10: Double = Double.NaN

    var CL49: Double = Double.NaN

    var L:  List[QuoteActor.DailyQuote] = Nil
    var L3: Double = Double.NaN                  // the 3rd lowest

    var dailyQuoteRequestTime:   Long = System.currentTimeMillis - 20000
    var orderHistoryRequestTime: Long = System.currentTimeMillis - 20000

    var position: Double = Double.NaN            // also called quantity

    var sentOrderHistoryRequest: Boolean = false

    val orders: collection.mutable.SortedSet[Order] = collection.mutable.SortedSet[Order]()(CreatedAtOrderingForOrders)

    // timers.startPeriodicTimer(Tick, Tick, 4019.millis)

    override def receive: Receive = {
        case Quote(lastTradePrice, _) =>
            ltp = lastTradePrice
            todayHigh = if (todayHigh.isNaN) ltp else math.max(ltp, todayHigh)
            todayLow  = if (todayLow.isNaN)  ltp else math.min(ltp, todayLow)

        case DailyQuoteListWrapper(_list) =>
            val N: Int = 62
            setStats(_list.reverse take N)

        case Tick =>
            if (shouldRequestDailyQuote)
                context.actorSelection(s"../../${QuoteActor.NAME}") ! QuoteActor.DailyQuoteRequest(symbol)
            if (shouldRequestOrderHistory) {
                log.info("{} sends OrderActor a OrderHistoryRequest", symbol)
                orderHistoryRequestTime = System.currentTimeMillis
                context.actorSelection(s"../../${OrderActor.NAME}") ! OrderActor.OrderHistoryRequest(symbol)
                sentOrderHistoryRequest = true
            }

        case o: Order => updateOrders(o)

        case Position(quantity) => position = quantity

        case Debug => debug()
    }

    private def debug() {
        val s = s"""
                   |$symbol debug information:
                   |  ltp (last trade price): $ltp
                   |  openPrice: $openPrice - todayHigh: $todayHigh - todayLow: $todayLow
                   |  HL49: $HL49 - HL31: $HL31 - HL10: $HL10
                   |  H049: $HO49 - H010: $HO10
                   |  OL49: $OL49 - OL10: $OL10 - CL49: $CL49
                   |  Lowest of last 10 days: ${L.map(_.lowPrice)}
                   |  Position: $position - sentOrderHistoryRequest: $sentOrderHistoryRequest
            """.stripMargin
        log.info(s)
    }

    /**
      * @param list the last N days
      */
    private def setStats(list: List[QuoteActor.DailyQuote]) {
        HL = list.sortWith((dq1, dq2) => dq1.highPrice - dq1.lowPrice < dq2.highPrice - dq2.lowPrice)
        HL49 = f"${HL(49).highPrice - HL(49).lowPrice}%4.4f".toDouble
        HL31 = f"${HL(31).highPrice - HL(31).lowPrice}%4.4f".toDouble
        HL10 = f"${HL(10).highPrice - HL(10).lowPrice}%4.4f".toDouble

        HO = list.sortWith((dq1, dq2) => dq1.highPrice - dq1.openPrice < dq2.highPrice - dq2.openPrice)
        HO49 = f"${HO(49).highPrice - HO(49).openPrice}%4.4f".toDouble
        HO10 = f"${HO(10).highPrice - HO(10).openPrice}%4.4f".toDouble

        OL = list.sortWith((dq1, dq2) => dq1.openPrice - dq1.lowPrice < dq2.openPrice - dq2.lowPrice)
        OL49 = f"${OL(49).openPrice - OL(49).lowPrice}%4.4f".toDouble
        OL10 = f"${OL(10).openPrice - OL(10).lowPrice}%4.4f".toDouble

        val CL = list.sortWith((dq1, dq2) => dq1.closePrice - dq1.lowPrice < dq2.closePrice - dq2.lowPrice)
        CL49 = f"${CL(49).closePrice - CL(49).lowPrice}%4.4f".toDouble

        L = list.take(10).sortWith((dq1, dq2) => dq1.lowPrice < dq2.lowPrice)
        L3 = f"${L(2).lowPrice}%4.4f".toDouble
    }

    private def shouldRequestDailyQuote: Boolean = HL.isEmpty &&
            System.currentTimeMillis - dailyQuoteRequestTime > 20000

    private def shouldRequestOrderHistory: Boolean = !sentOrderHistoryRequest && !position.isNaN && position != 0 &&
            System.currentTimeMillis - orderHistoryRequestTime > 20000

    private def state(o: Order): OrderState.Value =
        if (o.state.contains("confirmed")) OrderState.CONFIRM
        else if (o.state.contains("cancel")) OrderState.CANCEL
        else if (o.state.contains("queue")) OrderState.QUEUE
        else if (o.state.contains("partial")) OrderState.PARTIAL
        else if (o.state.contains("fill")) OrderState.FILL
        else {
            log.error("This {} has an unknown state.", o)
            OrderState.CONFIRM
        }

    private def updateOrders(o: Order) {
        import OrderState._

        def copy(from: Order, to: Order) {
            to.averagePrice = from.averagePrice
            to.cumulativeQuantity = from.cumulativeQuantity
            to.price = from.price
            to.quantity = from.quantity
            to.state = from.state
            to.updatedAt = from.updatedAt
        }

        val orderO: Option[Order] = orders.find(_.id == o.id)
        val _state = state(o)

        if (_state == CANCEL) orderO.foreach(orders -= _)
        else if (orderO.isEmpty) orders += o
        else if (Array(CONFIRM, QUEUE, PARTIAL) contains _state) copy(o, orderO.get)
        else /* _state is FILL */ if (_state != FILL) {
            copy(o, orderO.get)
            // TODO re-calculate the matchId
        }
    }
}
