package home.sparkjava

import model.HistoricalQuote

object AllQuotes {
    case class Get(symbol: String)

    case class Here(orders: Vector[HistoricalQuote])
}

