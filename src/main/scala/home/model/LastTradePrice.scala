package home.model

import org.json4s._
import org.json4s.JsonDSL._

object LastTradePrice {
    val alpha = .2
}

case class LastTradePrice(
    price: Double, previousClose: Double, symbol: String, instrument: String, updatedAt: String, var ema: Double = 0
) {
    def toJObject: JObject =
        ("price" -> price) ~
        ("previousClose" -> previousClose) ~
        ("symbol" -> symbol) ~
        ("instrument" -> instrument) ~
        ("updatedAt" -> updatedAt) ~
        ("ema" -> f"$ema%.2f".toDouble)
}
