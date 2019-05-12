package home.message

case class MakeOrder(side: String, quantity: Int, price: Double) {
    if (side != "buy" && side != "sell") {
        println(s"Value $side for side is invalid")
        System.exit(-1)
    }
}
