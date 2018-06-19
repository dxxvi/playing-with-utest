package home.sparkjava.model

import com.typesafe.scalalogging.Logger
import home.sparkjava.Util
import spray.json._

case class Quote(
//        adjustedPreviousClose: Double,
        askPrice: Double,
        askSize: Int,
        bidPrice: Double,
        bidSize: Int,
//        hasTraded: Boolean,
        instrument: String,
//        lastExtendedHoursTradePrice: Double,
        lastTradePrice: Double,
//        lastTradePriceSource: String,
        previousClose: Double,
        previousCloseDate: String,                         // yyyy-MM-dd
        symbol: String,
//        tradingHalted: Boolean,
        updatedAt: String                                  // yyyy-MM-dd'T'HH-mm-ss'Z'
)

object QuoteProtocol extends DefaultJsonProtocol {
    implicit object QuoteJsonFormat extends RootJsonFormat[Quote] with Util {
        override def read(json: JsValue): Quote = {
            implicit val fields: Map[String, JsValue] = json.asJsObject.fields
            implicit val prettyJson: String = json.prettyPrint
            implicit val logger: Logger = Logger[Quote]

            Quote(
                getFieldValue[Double]("ask_price"),
                getFieldValue[Int]("ask_size"),
                getFieldValue[Double]("bid_price"),
                getFieldValue[Int]("bid_size"),
                getFieldValue[String]("instrument"),
                getFieldValue[Double]("last_trade_price"),
                getFieldValue[Double]("previous_close"),
                getFieldValue[String]("previous_close_date"),
                getFieldValue[String]("symbol"),
                getFieldValue[String]("updated_at")
            )
        }

        override def write(q: Quote): JsValue = JsObject(Map[String, JsValue](
            "updatedAt" -> JsString(q.updatedAt),
            "lastTradePrice" -> JsNumber(q.lastTradePrice)
        ))
    }
}