package home.model

import java.time.LocalDate
import java.time.format.DateTimeFormatter

import org.json4s._
import org.json4s.JsonDSL._

object Order {
    val TODAY: String = LocalDate.now.format(DateTimeFormatter.ISO_DATE)

    // s is in this format yyyy-MM-ddTHH:mm:ss.SSSZ
    private def trimDate(s: String): String =
        s.substring(5).replace("T", " ").replaceAll("\\.?[0-9]{0,6}Z$", "")

    def toJObject(o: Order): JObject =
        ("createdAt" -> trimDate(o.createdAt)) ~
        ("id" -> o.id) ~
        ("side" -> o.side) ~
        ("instrument" -> o.instrument) ~
        ("price" -> o.price) ~
        ("state" -> o.state) ~
        ("cumulativeQuantity" -> (if (o.cumulativeQuantity.isNaN) 0 else o.cumulativeQuantity.toInt)) ~
        ("quantity" -> (if (o.quantity.isNaN) 0 else o.quantity.toInt)) ~
        ("updatedAt" -> trimDate(o.updatedAt)) ~
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

    def isToday: Boolean = createdAt startsWith Order.TODAY
}
