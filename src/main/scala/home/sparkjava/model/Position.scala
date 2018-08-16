package home.sparkjava.model

import home.sparkjava.Util
import org.json4s._
import org.json4s.native.JsonMethods._
import org.json4s.native.Serialization

object Position extends Util {
    def deserialize(s: String): List[Position] = {
        parse(s) \ "results" match {
            case JArray(jValues) => jValues map { jValue => Position(
                fromStringToOption[Double](jValue, "shares_held_for_stock_grants") ,
                fromStringToOption[Double](jValue, "pending_average_buy_price"),
                fromStringToOption[Double](jValue, "shares_held_for_options_events"),
                fromStringToOption[Double](jValue, "intraday_average_buy_price"),
                fromStringToOption[Double](jValue, "shares_held_for_options_collateral"),
                fromStringToOption[String](jValue, "created_at"),
                fromStringToOption[String](jValue, "updated_at"),
                fromStringToOption[Double](jValue, "shares_held_for_buys"),
                fromStringToOption[Double](jValue, "average_buy_price"),
                fromStringToOption[String](jValue, "instrument"),
                fromStringToOption[Double](jValue, "intraday_quantity"),
                fromStringToOption[Double](jValue, "shares_held_for_sells"),
                fromStringToOption[Double](jValue, "shares_pending_from_options_events"),
                fromStringToOption[Int](jValue, "quantity")
            )}
            case _ => List[Position]()
        }
    }

    def serialize(p: Position): String = Serialization.write[Position](p)(DefaultFormats)
}
case class Position(
                           shares_held_for_stock_grants: Option[Double],
                           pending_average_buy_price: Option[Double],
                           shares_held_for_options_events: Option[Double],
                           intraday_average_buy_price: Option[Double],
                           shares_held_for_options_collateral: Option[Double],
                           created_at: Option[String],
                           updated_at: Option[String],
                           shares_held_for_buys: Option[Double],
                           average_buy_price: Option[Double],
                           instrument: Option[String],
                           intraday_quantity: Option[Double],
                           shares_held_for_sells: Option[Double],
                           shares_pending_from_options_events: Option[Double],
                           quantity: Option[Int]
                   )
