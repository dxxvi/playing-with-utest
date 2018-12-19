package home.sparkjava.message

import home.sparkjava.model.OrderElement

case class HistoricalOrders(
    symbol: String,
    instrument: String,
    times: Int,         // # of requests to robinhood we have to make
    orders: Seq[OrderElement],
    next: Option[String]
)
