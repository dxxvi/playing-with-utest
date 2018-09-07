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
                    fromStringToOption[Int](jv, "cumulative_quantity"),
                    fromStringToOption[String](jv, "reject_reason"),
                    fromStringToOption[String](jv, "instrument"),
                    fromStringToOption[String](jv, "state"),
                    fromStringToOption[Double](jv, "price"),
                    fromStringToOption[Double](jv, "average_price"),
                    fromStringToOption[String](jv, "side"),
                    fromStringToOption[Int](jv, "quantity")
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
    cumulative_quantity: Option[Int],
    reject_reason: Option[String],
    instrument: Option[String],
    state: Option[String],
    price: Option[Double],
    average_price: Option[Double],
    side: Option[String],
    quantity: Option[Int],
    matchId: Option[String] = None
) {
    def this(_created_at: String, _id: String, _cumulative_quantity: Int, _state: String, _price: Double, _side: String,
             matchId: Option[String]) = this(None, Some(_created_at), None, Some(_id),
        Some(_cumulative_quantity), None, None, Some(_state), Some(_price), None, Some(_side), None, matchId)

    override def toString: String =
        "(" +
        s"${if (updated_at.isEmpty) None else updated_at.get}," +
        s"${if (created_at.isEmpty) None else created_at.get}," +
        s"${if (fees.isEmpty) None else fees.get}," +
        s"${if (id.isEmpty) None else id.get}," +
        s"${if (cumulative_quantity.isEmpty) None else cumulative_quantity.get}," +
        s"${if (reject_reason.isEmpty) None else reject_reason.get}," +
        s"${if (instrument.isEmpty) None else instrument.get}," +
        s"${if (state.isEmpty) None else state.get}," +
        s"${if (price.isEmpty) None else price.get}," +
        s"${if (average_price.isEmpty) None else average_price.get}," +
        s"${if (side.isEmpty) None else side.get}," +
        s"${if (quantity.isEmpty) None else quantity.get}," +
        s"${if (matchId.isEmpty) None else matchId.get}" +
        ")"
}

object BuySellOrderError {
    def deserialize(s: String): BuySellOrderError = parse(s) \ "non_field_errors" match {
        case JArray(jValues) => BuySellOrderError(jValues map {
            case JString(x) => x
            case _ => s
        })
        case _ => BuySellOrderError(Nil)
    }
}

/*
 * When we make an order, if it's successful, Robinhood will return a json which doesn't have the field named
 * `non_field_errors` and we don't care about that json; if it fails, Robinhood returns a json with the field
 * `non_field_errors` which we want to check.
 */
case class BuySellOrderError(non_field_errors: List[String])