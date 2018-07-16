package home.sparkjava.model

import home.sparkjava.Util
import org.apache.logging.log4j.scala.Logger
import spray.json._

case class HistoricalQuote(
    beginsAt: String,
    closePrice: Double,
    highPrice: Double,
    interpolated: Boolean,
    lowPrice: Double,
    openPrice: Double,
    session: String,
    volume: Int
)

object HistoricalQuoteProtocol extends DefaultJsonProtocol {
    implicit object HistoricalQuoteJsonFormat extends RootJsonFormat[HistoricalQuote] with Util {
        override def write(hq: HistoricalQuote): JsValue = JsObject(
            "beginsAt" -> JsString(hq.beginsAt),
            "closePrice" -> JsNumber(hq.closePrice),
            "highPrice" -> JsNumber(hq.highPrice),
            "interpolated" -> (if (hq.interpolated) JsTrue else JsFalse),
            "lowPrice" -> JsNumber(hq.lowPrice),
            "openPrice" -> JsNumber(hq.openPrice),
            "session" -> JsString(hq.session),
            "volume" -> JsNumber(hq.volume)
        )

        override def read(json: JsValue): HistoricalQuote = {
            implicit val fields: Map[String, JsValue] = json.asJsObject().fields
            implicit val prettyJson: String = json.prettyPrint
            implicit val logger: Logger = Logger(classOf[HistoricalQuote])

            HistoricalQuote(
                getFieldValue[String]("begins_at"),
                getFieldValue[Double]("close_price"),
                getFieldValue[Double]("high_price"),
                getFieldValue[Boolean]("interpolated"),
                getFieldValue[Double]("low_price"),
                getFieldValue[Double]("open_price"),
                getFieldValue[String]("session"),
                getFieldValue[Int]("volume")
            )
        }
    }
}