package home.sparkjava.model

import home.sparkjava.Util
import org.json4s._
import org.json4s.native.JsonMethods._
import org.json4s.native.Serialization

object Orders extends Util {
    def deserialize(s: String): Orders = {
        val jValue = parse(s)
        val next: Option[String] = jValue \ "next" match {
            case JString(nextUrl) => Some(nextUrl)
            case _ => None
        }
        val results: Option[List[OrderElement]] = jValue \ "results" match {
            case JArray(jValues) =>
                val orderElements = jValues map { jv => OrderElement(
                    fromStringToOption[String](jv, "updated_at"),
                    fromStringToOption[String](jv, "created_at"),
                    fromStringToOption[Double](jv, "fees"),
                    fromStringToOption[String](jv, "id"),
                    fromStringToOption[Double](jv, "cumulative_quantity"),
                    fromStringToOption[String](jv, "reject_reason"),
                    fromStringToOption[String](jv, "state"),
                    fromStringToOption[Double](jv, "price"),
                    fromStringToOption[Double](jv, "average_price"),
                    fromStringToOption[String](jv, "side"),
                    fromStringToOption[Double](jv, "quantity")
                )}
                Some(orderElements)
            case _ => None
        }
        Orders(next, results)
    }

    def serialize(o: OrderElement): String = Serialization.write[OrderElement](o)(DefaultFormats)
}

case class Orders(next: Option[String], results: Option[List[OrderElement]])

case class OrderElement(
    updated_at: Option[String],
    created_at: Option[String],
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
