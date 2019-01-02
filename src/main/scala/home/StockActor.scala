package home

import akka.actor.{Actor, Timers}

object StockActor {
    import akka.actor.Props

    sealed trait StockSealedTrait                // TODO what's the purpose of this sealed trait?
    case object Tick extends StockSealedTrait
    case class Quote(lastTradePrice: Double, updatedAt: String) extends StockSealedTrait
    case class DailyQuoteListWrapper(list: List[QuoteActor.DailyQuote]) extends StockSealedTrait
    case class Order(
                    averagePrice: Double,
                    createdAt: String,
                    cumulativeQuantity: Double,
                    id: String,
                    price: Double,
                    quantity: Double,
                    side: String,
                    state: String,
                    updatedAt: String
                    ) extends StockSealedTrait
    case class Position(quantity: Double) extends StockSealedTrait

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

    var dailyQuoteRequestTime: Long = System.currentTimeMillis - 20000

    var position: Double = Double.NaN            // also called quantity

    timers.startPeriodicTimer(Tick, Tick, 4019.millis)

    override def receive: Receive = {
        case Quote(lastTradePrice, _) =>
            ltp = lastTradePrice
            todayHigh = if (todayHigh.isNaN) ltp else math.max(ltp, todayHigh)
            todayLow  = if (todayLow.isNaN)  ltp else math.min(ltp, todayLow)

        case DailyQuoteListWrapper(_list) =>
            val N: Int = 62
            setStats(_list.reverse take N)

        case Tick if HL.isEmpty && System.currentTimeMillis - dailyQuoteRequestTime > 20000 =>
            context.actorSelection(s"../${QuoteActor.NAME}") ! QuoteActor.DailyQuoteRequest(symbol)

        case o: Order =>                         // TODO

        case Position(quantity) => position = quantity
    }

    /**
      * @param list the last N days
      */
    private def setStats(list: List[QuoteActor.DailyQuote]): Unit = {
        HL = list.sortWith((dq1, dq2) => dq1.highPrice - dq1.lowPrice < dq2.highPrice - dq2.lowPrice)
        HL49 = HL(49).highPrice - HL(49).lowPrice
        HL31 = HL(31).highPrice - HL(31).lowPrice
        HL10 = HL(10).highPrice - HL(10).lowPrice

        HO = list.sortWith((dq1, dq2) => dq1.highPrice - dq1.openPrice < dq2.highPrice - dq2.openPrice)
        HO49 = HO(49).highPrice - HO(49).openPrice
        HO10 = HO(10).highPrice - HO(10).openPrice

        OL = list.sortWith((dq1, dq2) => dq1.openPrice - dq1.lowPrice < dq2.openPrice - dq2.lowPrice)
        OL49 = OL(49).openPrice - OL(49).lowPrice
        OL10 = OL(10).openPrice - OL(10).lowPrice

        val CL = list.sortWith((dq1, dq2) => dq1.closePrice - dq1.lowPrice < dq2.closePrice - dq2.lowPrice)
        CL49 = CL(49).closePrice - CL(49).lowPrice

        L = list.take(10).sortWith((dq1, dq2) => dq1.lowPrice < dq2.lowPrice)
        L3 = L(2).lowPrice
    }
}
