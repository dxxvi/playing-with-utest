package home.sparkjava.model

import home.sparkjava.Util
import org.json4s._
import org.json4s.native.JsonMethods._
import org.json4s.native.Serialization

object Quote extends Util {
    /**
      * @param s like in quotes.json
      */
    def deserialize(s: String): List[Quote] = {
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
                /*
                 * we have to round the last_trade_price or we'll send a lot of messages to the browser because the
                 * last_trade_price changes only $.0001.
                 */
                fromStringToOption[Double](jValue, "last_trade_price").map(ltp => (ltp * 100).round.toDouble / 100),
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

object DailyQuote extends Util {
    /**
      * @param s like in quotes-daily.json
      */
    def deserialize(s: String): Map[String, List[DailyQuote]] = {
        parse(s) \ "results" match {
            case JArray(jValues) =>
                val x = jValues map { jValue => {
                    val historicals: List[DailyQuote] = historicalsJValueToDailyQuoteList(jValue \ "historicals")
                    fromToOption[String](jValue, "symbol") match {
                        case Some(symbol) => (symbol, historicals)
                        case None => ("", historicals)
                    }
                }}
                x.toMap
            case _ => Map[String, List[DailyQuote]]()
        }
    }

    /**
      * @param s the result of https://api.robinhood.com/quotes/historicals/AMD/?interval=day&span=year
      */
    def deserialize2(s: String): List[DailyQuote] = historicalsJValueToDailyQuoteList(parse(s) \ "historicals")

    private def historicalsJValueToDailyQuoteList(jValue: JValue): List[DailyQuote] = jValue match {
        case JArray(_historicals) => _historicals map {_h => DailyQuote(
            fromStringToOption[String](_h, "begins_at"),
            fromStringToOption[Double](_h, "open_price"),
            fromStringToOption[Double](_h, "close_price"),
            fromStringToOption[Double](_h, "high_price"),
            fromStringToOption[Double](_h, "low_price")
        )}
        case _ => List[DailyQuote]()
    }
}

case class DailyQuote(
    begins_at: Option[String],
    open_price: Option[Double],
    close_price: Option[Double],
    high_price: Option[Double],
    low_price: Option[Double]
)