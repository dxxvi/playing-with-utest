package home.sparkjava.model

import home.sparkjava.Util
import org.json4s._
import org.json4s.native.JsonMethods._
import org.json4s.native.Serialization

object Order extends Util {
    def deserialize(s: String): List[Order] = {
        parse(s) \ "results" match {
            case JArray(jValues) => jValues map { jValue => Order(
                fromStringToOption[String](jValue, "updated_at"),
                fromStringToOption[Double](jValue, "fees"),
                fromStringToOption[String](jValue, "id"),
                fromStringToOption[Double](jValue, "cumulative_quantity"),
                fromStringToOption[String](jValue, "reject_reason"),
                fromStringToOption[String](jValue, "state"),
                fromStringToOption[Double](jValue, "price"),
                fromStringToOption[Double](jValue, "average_price"),
                fromStringToOption[String](jValue, "side"),
                fromStringToOption[Double](jValue, "quantity")
            )}
            case _ => List[Order]()
        }
    }

    def serialize(o: Order): String = Serialization.write[Order](o)(DefaultFormats)
}

case class Order(
                updated_at: Option[String],
                fees: Option[Double],
                id: Option[String],
                cumulative_quantity: Option[Double],
                reject_reason: Option[String],
                state: Option[String],
                price: Option[Double],
                average_price: Option[Double],
                side: Option[String],
                quantity: Option[Double]
                )
