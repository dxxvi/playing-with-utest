package home

import java.time.format.DateTimeFormatter

import akka.actor.Actor
import home.util.TimersX

object StockActor {
    import akka.actor.Props

    sealed trait StockSealedTrait                // TODO what's the purpose of this sealed trait?
    case object Tick extends StockSealedTrait
    case class LastTradePrice5minQuoteList(_ltp: Double, quoteList: List[QuoteActor.DailyQuote])
            extends StockSealedTrait
    case class PositionAndOrderList(position: Double, orderList: List[Order]) extends StockSealedTrait
    case class DebugCommandWrapper(debugCommand: DebugCommand.Value) extends StockSealedTrait
    case object Debug extends StockSealedTrait

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
                    ) {
        override def hashCode(): Int = id.hashCode           // the hashCode

        override def equals(obj: Any): Boolean = obj match { // and equals methods seem not used
            case o: Order => this.id == o.id
            case _ => false
        }

        def brief: String = s"created_at:$createdAt, side:$side, cumulative:$cumulativeQuantity, quantity:$quantity, state:$state"
    }

    object DebugCommand extends Enumeration {
        val CLEAR_ORDERS = Value
    }

    object OrderState extends Enumeration {
        val CONFIRM /* including confirmed and unconfirmed */, CANCEL, QUEUE, PARTIAL, FILL, FAIL, REJECT = Value
    }

    object CreatedAtOrderingForOrders extends Ordering[Order] {
        import java.time.LocalDateTime

        private val regex = raw"Z$$".r
        private def utcDateTimeToLocalDateTime(utcDateTime: String): LocalDateTime =
            LocalDateTime.parse(regex.replaceAllIn(utcDateTime, ""), DateTimeFormatter.ISO_LOCAL_DATE_TIME)

        override def compare(x: Order, y: Order): Int =
            utcDateTimeToLocalDateTime(y.createdAt) compareTo utcDateTimeToLocalDateTime(x.createdAt)
    }

    def props(
                 symbol: String,
                 orderHistory: List[Order],
                 recentOrders: List[Order],
                 position: Double,
                 ltp: Double,
                 dailyQuotes: List[QuoteActor.DailyQuote]
             ): Props = Props(new StockActor(symbol, orderHistory, recentOrders, position, ltp, dailyQuotes))
}

class StockActor(
                    symbol: String,
                    orderHistory: List[StockActor.Order], // confirmed and filled orders only
                    recentOrders: List[StockActor.Order], // any order state can be here
                    var position: Double,
                    var ltp: Double,                      // last_trade_price
                    dailyQuotes: List[QuoteActor.DailyQuote]
                ) extends Actor with TimersX {
    import StockActor._
    import scala.concurrent.duration._
    import akka.event._

    implicit val logSource: LogSource[AnyRef] = (_: AnyRef) => symbol
    val log: LoggingAdapter = Logging(context.system, this)

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

    // HPC and PCL sorted from lowest - highest
    var HPC: List[(String /* beginsAt */, Double /* today_highest - previous_close */)] = Nil
    var PCL: List[(String /* beginsAt */, Double /* previous_close - today_lowest */)]  = Nil

    setStats()

    var inOrders: List[Order] = Nil // all orders that make up the current position
    val orders: collection.mutable.SortedSet[Order] = collection.mutable.SortedSet[Order]()(CreatedAtOrderingForOrders)
    orders ++= orderHistory
    recentOrders foreach { o => updateOrders(o) }
    createInOrdersAndMatchId()

    timersx.startPeriodicTimer(Tick, Tick, 4019.millis)

    override def receive: Receive = {
        case LastTradePrice5minQuoteList(_ltp, quoteList) =>
            var anyChange = false
            if (!_ltp.isNaN && (ltp.isNaN || ltp != _ltp)) {
                ltp = _ltp
                anyChange = true
            }
            val openLowHigh = quoteList.foldLeft((Double.NaN, Double.NaN, Double.NaN))((t, dq) => (
                    if (t._1.isNaN) dq.openPrice else t._1,
                    if (t._2.isNaN) dq.lowPrice else math.min(t._1, dq.lowPrice),
                    if (t._3.isNaN) dq.highPrice else math.max(t._2, dq.highPrice)
            ))
            if (openPrice.isNaN) openPrice = openLowHigh._1
            if (!openLowHigh._2.isNaN && (todayLow.isNaN || todayLow != openLowHigh._2)) {
                todayLow = openLowHigh._2
                anyChange = true
            }
            if (!openLowHigh._3.isNaN && (todayHigh.isNaN || todayHigh != openLowHigh._3)) {
                todayHigh = openLowHigh._3
                anyChange = true
            }
            // TODO if anyChange & ltp, open, high, low are not NaN, run some logics to buy/sell

        case PositionAndOrderList(_position, orderList) =>
            var anyChange = false
            if (!_position.isNaN && (position.isNaN || _position != position)) {
                position = _position
                anyChange = true
            }
            orderList.foreach(o => updateOrders(o))
            createInOrdersAndMatchId()

        case Debug =>
            val map = debug()
            sender() ! map

        case DebugCommandWrapper(DebugCommand.CLEAR_ORDERS) => orders.clear()
    }

    private def createInOrdersAndMatchId() {
        import OrderState._

        if (position.isNaN || position == 0) {
            inOrders = Nil
            return
        }

        var _position: Int = 0
        inOrders = orders.toList.takeWhile(o => {
            _position = _position + (state(o) match {
                case FILL => if (o.side == "buy") o.quantity.toInt else -o.quantity.toInt
                case PARTIAL => if (o.side == "buy") o.cumulativeQuantity.toInt else -o.cumulativeQuantity.toInt
                case _ => 0
            })
            _position != position.toInt
        })


    }

    private def debug(): Map[String, String] = {
        val s = s"""
                   |$symbol debug information:
                   |  ltp (last trade price): $ltp
                   |  openPrice: $openPrice - todayHigh: $todayHigh - todayLow: $todayLow
                   |  HL49: $HL49 - HL31: $HL31 - HL10: $HL10
                   |  H049: $HO49 - H010: $HO10
                   |  OL49: $OL49 - OL10: $OL10 - CL49: $CL49
                   |  Lowests of last 20 days: ${L.map(_.lowPrice)}
                   |  Position: $position
                   |  orders: ${orders.size}
                   |${orders.toList.map(_.brief).mkString("\n")}
            """.stripMargin
/*
        HPC: ${HPC.map(t => t._1 + " " + t._2).mkString("\n")}
        PCL: ${PCL.map(t => t._1 + " " + t._2).mkString("\n")}
*/
        log.info(s)
        Map(
            "ltp" -> (if (ltp.isNaN) "NaN" else ltp.toString),
            "openPrice" -> (if (openPrice.isNaN) "NaN" else openPrice.toString),
            "todayHigh" -> (if (todayHigh.isNaN) "NaN" else todayHigh.toString),
            "todayLow"  -> (if (todayLow.isNaN) "NaN" else todayLow.toString),
            "CL49"      -> (if (CL49.isNaN) "NaN" else CL49.toString),
            "HL49"      -> (if (HL49.isNaN) "NaN" else HL49.toString),
            "OL49"      -> (if (OL49.isNaN) "NaN" else OL49.toString),
            "L3"        -> (if (L3.isNaN) "NaN" else L3.toString),
            "position"  -> (if (position.isNaN) "NaN" else position.toString),
            "HL" -> HL.mkString(","),
            "HO" -> HO.mkString(","),
            "OL" -> OL.mkString(","),
            "L"  -> L.mkString(","),
            // we need to convert orders into list before mapping so that we can keep the order of elements
            "orders" -> orders.toList.map(_.toString).mkString("\n"),
            "HPC" -> HPC.map(t => s"${t._1} ${t._2}").mkString("\n"),
            "PLC" -> PCL.map(t => s"${t._1} ${t._2}").mkString("\n")
        )
    }

    private def setStats() {
        val x = dailyQuotes.reverse
        val last62Days: List[QuoteActor.DailyQuote] = x take 62
        val last90Days: List[QuoteActor.DailyQuote] = x take 90

        HL = last62Days.sortWith((dq1, dq2) => dq1.highPrice - dq1.lowPrice < dq2.highPrice - dq2.lowPrice)
        HL49 = f"${HL(49).highPrice - HL(49).lowPrice}%4.4f".toDouble
        HL31 = f"${HL(31).highPrice - HL(31).lowPrice}%4.4f".toDouble
        HL10 = f"${HL(10).highPrice - HL(10).lowPrice}%4.4f".toDouble

        HO = last62Days.sortWith((dq1, dq2) => dq1.highPrice - dq1.openPrice < dq2.highPrice - dq2.openPrice)
        HO49 = f"${HO(49).highPrice - HO(49).openPrice}%4.4f".toDouble
        HO10 = f"${HO(10).highPrice - HO(10).openPrice}%4.4f".toDouble

        OL = last62Days.sortWith((dq1, dq2) => dq1.openPrice - dq1.lowPrice < dq2.openPrice - dq2.lowPrice)
        OL49 = f"${OL(49).openPrice - OL(49).lowPrice}%4.4f".toDouble
        OL10 = f"${OL(10).openPrice - OL(10).lowPrice}%4.4f".toDouble

        val CL = last62Days.sortWith((dq1, dq2) => dq1.closePrice - dq1.lowPrice < dq2.closePrice - dq2.lowPrice)
        CL49 = f"${CL(49).closePrice - CL(49).lowPrice}%4.4f".toDouble

        L = x.take(20).sortWith((dq1, dq2) => dq1.lowPrice < dq2.lowPrice)
        L3 = f"${L(2).lowPrice}%4.4f".toDouble

        var temp: Double = 0
        HPC = dailyQuotes
                .map(dq => {
                    val previousClose = temp
                    temp = dq.closePrice
                    (dq.beginsAt, f"${dq.highPrice - previousClose}%4.4f".toDouble)
                })
                .reverse
                .take(90)
                .sortWith(_._2 < _._2)
        PCL = dailyQuotes
                .map(dq => {
                    val previousClose = temp
                    temp = dq.closePrice
                    (dq.beginsAt, f"${previousClose - dq.lowPrice}%4.4f".toDouble)
                })
                .reverse
                .take(90)
                .sortWith(_._2 < _._2)
    }

    private def state(o: Order): OrderState.Value =
        if (o.state.contains("confirmed")) OrderState.CONFIRM
        else if (o.state.contains("cancel")) OrderState.CANCEL
        else if (o.state.contains("queue")) OrderState.QUEUE
        else if (o.state.contains("partial")) OrderState.PARTIAL
        else if (o.state.contains("fill")) OrderState.FILL
        else if (o.state.contains("fail")) OrderState.FAIL
        else if (o.state.contains("reject")) OrderState.REJECT
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

        val orderOption: Option[Order] = orders.find(_.id == o.id) // existing order in orders
        val _state = state(o)               // _state: the state of the new order

        if (orderOption.isDefined) {
            if (_state == FAIL || _state == REJECT) {
                orders -= orderOption.get
            }
            else if (_state == CONFIRM || _state == QUEUE) {
                copy(o, orderOption.get)
            }
            else if (_state == PARTIAL) {
                copy(o, orderOption.get)
            }
            else if (_state == CANCEL) {
                if (o.cumulativeQuantity > 0) {
                    val previousState = state(orderOption.get)
                    if (previousState != FILL) {
                        copy(o, orderOption.get)
                        orderOption.get.state = "filled"
                    }
                }
                else {
                    orders -= orderOption.get
                }
            }
            else /* _state is FILL */ {
                copy(o, orderOption.get)
            }
        }
        else if (_state != FAIL && _state != CANCEL && _state != REJECT) {
            orders += o
        }
    }
}
