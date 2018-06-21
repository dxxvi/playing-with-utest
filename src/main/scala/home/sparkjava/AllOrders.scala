package home.sparkjava

import model.Order

object AllOrders {
    case class Get(symbol: String)

    case class Here(orders: Vector[Order])
}

