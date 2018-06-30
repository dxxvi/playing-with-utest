package home.sparkjava.model

import com.typesafe.scalalogging.Logger
import home.sparkjava.Util
import spray.json._

case class Position(
    updatedAt: String,                                     // "2018-06-06T18:20:56.059832Z"
    averageBuyPrice: Double,
    instrument: String,
    sharesHeldForSell: Double,
    quantity: Double
)

object PositionProtocol extends DefaultJsonProtocol {
    implicit object PositionJsonFormat extends RootJsonFormat[Position] with Util {
        override def read(json: JsValue): Position = {
            implicit val fields: Map[String, JsValue] = json.asJsObject().fields
            implicit val prettyJson: String = json.prettyPrint
            implicit val logger: Logger = Logger[Position]

            Position(
                getFieldValue[String]("updated_at"),
                getFieldValue[Double]("average_buy_price"),
                getFieldValue[String]("instrument"),
                getFieldValue[Double]("shares_held_for_sells"),
                getFieldValue[Double]("quantity")
            )
        }

        override def write(p: Position): JsValue = JsObject(
            "updatedAt" -> JsString(p.updatedAt),
            "averageBuyPrice" -> JsNumber(p.averageBuyPrice),
            "instrument" -> JsString(p.instrument),
            "sharesHeldForSell" -> JsNumber(p.sharesHeldForSell),
            "quantity" -> JsNumber(p.quantity)
        )
    }
}
