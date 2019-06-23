package home.model

import org.json4s._
import org.json4s.JsonDSL._

object LastTradePrice {
    val alpha = .2

    object Ordering extends Ordering[LastTradePrice] {
        def compare(a:LastTradePrice, b:LastTradePrice): Int = {
            val as = a.updatedAt.split("[:TZ\\.-]").toStream
            val bs = b.updatedAt.split("[:TZ\\.-]").toStream
            (as zip bs)
                    .map(t => t._1.toInt - t._2.toInt)
                    .find(_ != 0)
                    .getOrElse(0)
        }
    }
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

    override def toString: String = s"""{"price":$price,"updatedAt":"$updatedAt"}"""
}
