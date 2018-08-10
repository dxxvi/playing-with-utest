package home.sparkjava

import akka.actor.{Actor, Props}
import message.{HistoricalOrders, Tick}
import org.apache.logging.log4j.ThreadContext
import model.{Fundamental, OrderElement, Position, Quote}

object StockActor {
    def props(symbol: String): Props = Props(new StockActor(symbol))
}

class StockActor(symbol: String) extends Actor with Util {
    val isDow: Boolean = isDow(symbol)
    var fu = new Fundamental(
        Some(-.1), Some(-.1), Some(""), Some(-.1), Some(-.1), Some(-.1), Some(-.1), Some(-.1), Some(-.1), Some(-.1), ""
    )
    var p = new Position(
        Some(-.1), Some(-.1), Some(-.1), Some(-.1), Some(-.1), None, None, Some(-.1), Some(-.1), None, Some(-.1),
        Some(-.1), Some(-.1), Some(-.1)
    )
    var q = new Quote(
        None, None, None, None, None, None, None, None, None, None, None, None, None, None, None
    )
    var instrument = ""
    var lastTimeHistoricalOrdersRequested: Long = 0 // in seconds
    val orders: collection.mutable.SortedSet[OrderElement] =
        collection.mutable.SortedSet[OrderElement]()(Ordering.by[OrderElement, String](_.created_at.get)(Main.timestampOrdering.reverse))

    val _receive: Receive = {
        case _fu: Fundamental => if ((_fu.low.isDefined && _fu.high.isDefined) || _fu.open.isDefined) {
            fu = _fu
            sendFundamental
            instrument = fu.instrument
        }
        case _p: Position => if (_p.quantity.isDefined) {
            p = _p
            sendPosition
            if (p.instrument.nonEmpty) instrument = p.instrument.get
        }
        case _q: Quote => // the QuoteActor is sure that symbol, last_trade_price and instrument are there
            q = _q
            sendQuote
            instrument = q.instrument.get
            val now = System.currentTimeMillis / 1000
            if (p.quantity.exists(_ > 0) && orders.isEmpty && (now - lastTimeHistoricalOrdersRequested > 15)) {
                // we should wait for the OrderActor a bit because we receive quote every 4 seconds
                lastTimeHistoricalOrdersRequested = now
                context.actorSelection(s"../../${OrderActor.NAME}") !
                        HistoricalOrders(symbol, instrument, 3, Seq[OrderElement](), None)
            }
        case HistoricalOrders(_, _, _, _orders, _) =>
            orders ++= _orders.filter(oe => oe.state.isDefined && (oe.state.get == "filled" || oe.state.get.contains("confirmed")))
            println(s"... check $symbol.log ...")
            logger.debug(s"Got HistoricalOrders:\n${orders.toList.map(_.toString).mkString("\n")}")
        case Tick => // purpose: send the symbol to the browser
            if (p.quantity.get >= 0) sendPosition else sendFundamental
    }
    override def receive: Receive = sideEffect andThen _receive
    private def sideEffect: PartialFunction[Any, Any] = { case x => ThreadContext.put("symbol", symbol); x }

    private def sendFundamental {
        val message = s"$symbol: FUNDAMENTAL: ${Fundamental.serialize(fu)}"
        context.actorSelection(s"../../${WebSocketActor.NAME}") ! message
    }

    private def sendPosition {
        val message = s"$symbol: POSITION: ${Position.serialize(p)}"
        context.actorSelection(s"../../${WebSocketActor.NAME}") ! message
    }

    private def sendQuote {
        if (q.last_trade_price.isDefined && q.last_trade_price.get > 0) {
            val message = s"$symbol: QUOTE: ${Quote.serialize(q)}"
            context.actorSelection(s"../../${WebSocketActor.NAME}") ! message
        }
    }
}
