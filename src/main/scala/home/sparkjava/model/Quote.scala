package home.sparkjava.model

import home.sparkjava.Util
import org.json4s._
import org.json4s.native.JsonMethods._
import org.json4s.native.Serialization

import scala.util.Try

object Quote extends Util {
    /**
      * @param s like in quotes.json
      */
    def deserialize(s: String): List[Quote] = {
        parse(s) \ "results" match {
            case JArray(jValues) => jValues
                    .map(jv => for {
                        instrument <- fromJValueToOption[String](jv \ "instrument")
                        ltp <- fromJValueToOption[Double](jv \ "last_trade_price")
                        if !ltp.isNaN
                        symbol <- fromJValueToOption[String](jv \ "symbol")
                        adjusted_previous_close = fromJValueToOption[Double](jv \ "adjusted_previous_close")
                        ask_price               = fromJValueToOption[Double](jv \ "ask_price")
                        ask_size                = fromJValueToOption[Int](jv \ "ask_size")
                        bid_price               = fromJValueToOption[Double](jv \ "bid_price")
                        bid_size                = fromJValueToOption[Int](jv \ "bid_size")
                        has_traded              = fromJValueToOption[Boolean](jv \ "has_traded")
                        last_extended_hours_trade_price = fromJValueToOption[Double](jv \ "last_extended_hours_trade_price")
                        last_trade_price_source = fromJValueToOption[String](jv \ "last_trade_price_source")
                        previous_close         <- fromJValueToOption[Double](jv \ "previous_close")
                        if !previous_close.isNaN
                        previous_close_date     = fromJValueToOption[String](jv \ "previous_close_date")
                        trading_halted          = fromJValueToOption[Boolean](jv \ "trading_halted")
                        updated_at              = fromJValueToOption[String](jv \ "updated_at")
                    } yield Quote(
                        adjusted_previous_close, ask_price, ask_size, bid_price, bid_size, has_traded, instrument,
                        last_extended_hours_trade_price, ltp, last_trade_price_source, previous_close,
                        previous_close_date, symbol, trading_halted, updated_at
                    ))
                    .collect { case Some(q) => q }
            case _ => Nil
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
                        instrument: String,
                        last_extended_hours_trade_price: Option[Double],
                        last_trade_price: Double,
                        last_trade_price_source: Option[String],
                        previous_close: Double,
                        previous_close_date: Option[String],
                        symbol: String,
                        trading_halted: Option[Boolean],
                        updated_at: Option[String]
                )

object DailyQuote extends Util {
    /**
      * @param s like in quotes-daily.json
      */
    def deserialize(s: String): Map[String /* symbol */, List[DailyQuote]] =
        (parse(s) \ "results").asInstanceOf[JArray].arr
                .map(jv => for {
                    symbol <- fromJValueToOption[String](jv \ "symbol")
                    dailyQuoteList = getIntervalQuotesFromJArray((jv \ "historicals").asInstanceOf[JArray])
                } yield (symbol, dailyQuoteList)
                )
                .collect {
                    case Some(tuple) => tuple
                }
                .toMap

    /**
      * @param ja looks like this:
      * [
      *   {
      *     "begins_at": "2019-01-28T14:30:00Z",
      *     "close_price": "19.505000",
      *     "high_price": "19.560000",
      *     "interpolated": false,
      *     "low_price": "19.200000",
      *     "open_price": "19.200000",
      *     "session": "reg",
      *     "volume": 228090
      *   },
      *   {
      *     "begins_at": "2019-01-28T16:10:00Z",
      *     "close_price": "19.835000",
      *     "high_price": "19.865000",
      *     "interpolated": false,
      *     "low_price": "19.800000",
      *     "open_price": "19.820000",
      *     "session": "reg",
      *     "volume": 22993
      *   }
      * ]
      */
    private def getIntervalQuotesFromJArray(ja: JArray): List[DailyQuote] = ja.arr
            .map(jv =>
                for {
                    beginsAt   <- fromJValueToOption[String](jv \ "begins_at")
                    openPrice  <- fromJValueToOption[Double](jv \ "open_price")
                    closePrice <- fromJValueToOption[Double](jv \ "close_price")
                    highPrice  <- fromJValueToOption[Double](jv \ "high_price")
                    lowPrice   <- fromJValueToOption[Double](jv \ "low_price")
                    volume     <- fromJValueToOption[Int](jv \ "volume")
                } yield DailyQuote(beginsAt, openPrice, closePrice, highPrice, lowPrice, volume)
            )
            .collect { case Some(dailyQuote) => dailyQuote }
}

case class DailyQuote(
    begins_at: String,
    open_price: Double,
    close_price: Double,
    high_price: Double,
    low_price: Double,
    volume: Int
)