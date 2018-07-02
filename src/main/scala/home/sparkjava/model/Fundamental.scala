package home.sparkjava.model

import com.typesafe.scalalogging.Logger
import home.sparkjava.Util
import spray.json._

case class Fundamental(
    open: Double,
    high: Double,
    low: Double,
    high52Weeks: Double,
    low52Weeks: Double,
    dividendYield: Double,
    instrument: String,
    peRatio: Option[Double],
    description: String,
    yearFounded: Int
)

object FundamentalProtocol extends DefaultJsonProtocol {
    implicit object FundamentalJsonFormat extends RootJsonFormat[Fundamental] with Util {
        override def write(f: Fundamental): JsValue = JsObject(
            "open" -> JsNumber(f.open),
            "low" -> JsNumber(f.low),
            "high" -> JsNumber(f.high),
            "high52weeks" -> JsNumber(f.high52Weeks),
            "low52weeks" -> JsNumber(f.low52Weeks),
            "dividendYield" -> JsNumber(f.dividendYield),
            "description" -> JsString(f.description),
            "yearFounded" -> JsNumber(f.yearFounded)
        )

        override def read(json: JsValue): Fundamental = {
            implicit val fields: Map[String, JsValue] = json.asJsObject().fields
            implicit val prettyJson: String = json.prettyPrint
            implicit val logger: Logger = Logger[Fundamental]

            Fundamental(
                getFieldValue[Double]("open"),
                getFieldValue[Double]("high"),
                getFieldValue[Double]("low"),
                getFieldValue[Double]("high_52_weeks"),
                getFieldValue[Double]("low_52_weeks"),
                getFieldValue[Double]("dividend_yield"),
                getFieldValue[String]("instrument"),
                None,
                getFieldValue[String]("description"),
                getFieldValue[Int]("year_founded")
            )
        }
    }
}