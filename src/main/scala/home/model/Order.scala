package home.model

import org.json4s._
import org.json4s.JsonDSL._

object Order {
    def toJObject(o: Order): JObject =
            ("createdAt" -> o.createdAt.substring(5).replace("T", " ").replaceAll("\\.?[0-9]{0,6}Z$", "")) ~
            ("id" -> o.id) ~
            ("side" -> o.side) ~
            ("instrument" -> o.instrument) ~
            ("price" -> o.price) ~
            ("state" -> o.state) ~
            ("cumulativeQuantity" -> (if (o.cumulativeQuantity.isNaN) 0 else o.cumulativeQuantity.toInt)) ~
            ("quantity" -> (if (o.quantity.isNaN) 0 else o.quantity.toInt)) ~
            ("updatedAt" -> o.updatedAt.replace("T", " ").replaceAll("\\.?[0-9]{0,6}Z$", "")) ~
            ("matchId" -> o.matchId)

    def toJArray(orders: List[Order]): JArray = JArray(orders.map(toJObject))
}

case class Order(
                createdAt: String,
                id: String,
                side: String,
                instrument: String,
                var price: Double,
                var state: String,
                var cumulativeQuantity: Double,
                var quantity: Double,
                var updatedAt: String,
                var matchId: Option[String] = None
                ) {
    def brief: String =
        s"""
           |  created_at: $createdAt,
           |  side:       $side,
           |  cumulative: $cumulativeQuantity,
           |  quantity:   $quantity,
           |  state:      $state
         """.stripMargin
}
