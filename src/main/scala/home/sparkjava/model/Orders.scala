package home.sparkjava.model

import org.json4s._
import org.json4s.native.JsonMethods._
import org.json4s.native.Serialization

object Orders extends home.sparkjava.Util {
    def deserialize(s: String): Orders = {
        val jValue = parse(s)
        val next: Option[String] = fromJValueToOption[String](jValue \ "next")
        val results: List[OrderElement] = jValue \ "results" match {
            case JArray(jValues) => jValues
                    .map(toOrderElement)
                    .collect { case Some(orderElement) => orderElement }

            case _ =>
                println(s"Error: check the results field in $s")
                System.exit(-1)
                Nil
        }
        Orders(next, results)
    }

    def toOrderElement(jv: JValue): Option[OrderElement] = for {
        updated_at <- fromJValueToOption[String](jv \ "updated_at")
        created_at <- fromJValueToOption[String](jv \ "created_at")
        instrument <- fromJValueToOption[String](jv \ "instrument")
        id         <- fromJValueToOption[String](jv \ "id")
        state      <- fromJValueToOption[String](jv \ "state")
        side       <- fromJValueToOption[String](jv \ "side")
        fees                = fromJValueToOption[Double](jv \ "fees")
        cumulative_quantity = fromJValueToOption[Int](jv \ "cumulative_quantity")
        quantity            = fromJValueToOption[Int](jv \ "quantity")
        reject_reason       = fromJValueToOption[String](jv \ "reject_reason")
        price               = fromJValueToOption[Double](jv \ "price")
        average_price       = fromJValueToOption[Double](jv \ "average_price")
        if quantity.isDefined || cumulative_quantity.isDefined
    } yield OrderElement(updated_at, created_at, fees, id, cumulative_quantity, reject_reason,
        instrument, state, price, average_price, side, quantity)

    def serialize(o: OrderElement): String = Serialization.write[OrderElement](o)(DefaultFormats)
}

case class Orders(next: Option[String], results: List[OrderElement])

case class OrderElement(
    updated_at: String,
    created_at: String,
    fees: Option[Double],
    id: String,
    cumulative_quantity: Option[Int],
    reject_reason: Option[String],
    instrument: String,
    state: String,
    price: Option[Double],
    average_price: Option[Double],
    side: String,
    quantity: Option[Int],
    matchId: Option[String] = None
) {
    override def toString: String =
        "(" +
        s"$updated_at," +
        s"$created_at," +
        s"${if (fees.isEmpty) None else fees.get}," +
        s"$id," +
        s"${if (cumulative_quantity.isEmpty) None else cumulative_quantity.get}," +
        s"${if (reject_reason.isEmpty) None else reject_reason.get}," +
        s"$instrument," +
        s"$state," +
        s"${if (price.isEmpty) None else price.get}," +
        s"${if (average_price.isEmpty) None else average_price.get}," +
        s"$side," +
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