package home.sparkjava.model

import home.sparkjava.Util
import org.json4s.DefaultFormats
import org.json4s.native.Serialization

object Quote extends Util {
    /**
      * @param s like in quotes.json
      */
    def deserialize(s: String): List[Quote] = {
        import org.json4s._
        import org.json4s.native.JsonMethods._
        parse(s) \ "results" match {
            case JArray(jValues) => jValues map { jValue => Quote(
                fromStringToOption[Double](jValue, "adjusted_previous_close"),
                fromStringToOption[Double](jValue, "ask_price"),
                fromToOption[Int](jValue, "ask_size"),
                fromStringToOption[Double](jValue, "bid_price"),
                fromToOption[Int](jValue, "bid_size"),
                fromToOption[Boolean](jValue, "has_traded"),
                fromToOption[String](jValue, "instrument"),
                fromStringToOption[Double](jValue, "last_extended_hours_trade_price"),
                fromStringToOption[Double](jValue, "last_trade_price"),
                fromToOption[String](jValue, "last_trade_price_source"),
                fromStringToOption[Double](jValue, "previous_close"),
                fromToOption[String](jValue, "previous_close_date"),
                fromToOption[String](jValue, "symbol"),
                fromToOption[Boolean](jValue, "trading_halted"),
                fromToOption[String](jValue, "updated_at")
            )}
            case _ => List[Quote]()
        }
    }

    def serialize(q: Quote): String = Serialization.write[Quote](q)(DefaultFormats)
}

case class Quote(
                        adjusted_previous_close: Option[Double],
                        ask_price: Option[Double],
                        ask_size: Option[Int],
                        bid_price: Option[Double],
                        bid_size: Option[Int],
                        has_traded: Option[Boolean],
                        instrument: Option[String],
                        last_extended_hours_trade_price: Option[Double],
                        last_trade_price: Option[Double],
                        last_trade_price_source: Option[String],
                        previous_close: Option[Double],
                        previous_close_date: Option[String],
                        symbol: Option[String],
                        trading_halted: Option[Boolean],
                        updated_at: Option[String]
                )
