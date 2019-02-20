package home.sparkjava.model

import home.sparkjava.Util
import org.json4s._
import org.json4s.native.JsonMethods._
import org.json4s.native.Serialization

object Position extends Util {
    def deserialize(s: String): List[Position] = {
        parse(s) \ "results" match {
            case JArray(jValues) => jValues
                    .map(jv => for {
                        instrument <- fromJValueToOption[String](jv \ "instrument")
                        quantity <- fromJValueToOption[Int](jv \ "quantity")
                        shares_held_for_stock_grants   = fromJValueToOption[Double](jv \ "shares_held_for_stock_grants")
                        pending_average_buy_price      = fromJValueToOption[Double](jv \ "pending_average_buy_price")
                        shares_held_for_options_events = fromJValueToOption[Double](jv \ "shares_held_for_options_events")
                        intraday_average_buy_price     = fromJValueToOption[Double](jv \ "intraday_average_buy_price")
                        created_at                     = fromJValueToOption[String](jv \ "created_at")
                        updated_at                     = fromJValueToOption[String](jv \ "updated_at")
                        shares_held_for_buys           = fromJValueToOption[Double](jv \ "shares_held_for_buys")
                        average_buy_price              = fromJValueToOption[Double](jv \ "average_buy_price")
                        intraday_quantity              = fromJValueToOption[Double](jv \ "intraday_quantity")
                        shares_held_for_sells          = fromJValueToOption[Double](jv \ "shares_held_for_sells")
                        shares_held_for_options_collateral = fromJValueToOption[Double](jv \ "shares_held_for_options_collateral")
                        shares_pending_from_options_events = fromJValueToOption[Double](jv \ "shares_pending_from_options_events")
                    } yield Position(
                        shares_held_for_stock_grants,
                        pending_average_buy_price,
                        shares_held_for_options_events,
                        intraday_average_buy_price,
                        shares_held_for_options_collateral,
                        created_at,
                        updated_at,
                        shares_held_for_buys,
                        average_buy_price,
                        instrument,
                        intraday_quantity,
                        shares_held_for_sells,
                        shares_pending_from_options_events,
                        quantity
                    ))
                    .collect { case Some(position) => position }
            case _ =>
                println(s"Error: this doesn't look like a position response: $s")
                Nil
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
                           instrument: String,
                           intraday_quantity: Option[Double],
                           shares_held_for_sells: Option[Double],
                           shares_pending_from_options_events: Option[Double],
                           quantity: Int
                   )
