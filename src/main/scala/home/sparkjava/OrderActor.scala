package home.sparkjava

object OrderActor {
    val NAME = "orderActor"

    case class Buy(symbol: String, quantity: Int, price: Double)
    case class Sell(symbol: String, quantity: Int, price: Double)
    case class Cancel(orderId: String)
}

class OrderActor {

}
