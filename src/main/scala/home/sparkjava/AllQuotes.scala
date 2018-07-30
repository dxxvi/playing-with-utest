package home.sparkjava

import model.Quote

object AllQuotes {
    case class Get(symbol: String)

    case class Here(orders: Vector[Quote])
}

