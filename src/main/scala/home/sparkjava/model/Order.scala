package home.sparkjava.model

import home.sparkjava.Util
import org.apache.logging.log4j.scala.Logger
import spray.json._

import scala.collection.mutable

case class OrderExecution(
        timestamp: String,                                 // "2018-06-04T19:36:43.251000Z"
        price: Double,
        settlementDate: String,                            // yyyy-MM-dd
        id: String,
        quantity: Double
)

object OrderExecutionProtocol extends DefaultJsonProtocol {
    implicit object OrderExecutionJsonFormat extends RootJsonFormat[OrderExecution] with Util {
        override def write(oe: OrderExecution): JsValue = JsObject(
            "timestamp" -> JsString(oe.timestamp),
            "price" -> JsNumber(oe.price),
            "settlementDate" -> JsString(oe.settlementDate),
            "id" -> JsString(oe.id),
            "quantity" -> JsNumber(oe.quantity)
        )

        override def read(json: JsValue): OrderExecution = {
            implicit val fields: Map[String, JsValue] = json.asJsObject("Unable to convert to JsObject").fields
            implicit val prettyJson: String = json.prettyPrint
            implicit val logger: Logger = Logger(classOf[OrderExecution])

            OrderExecution(
                getFieldValue[String]("timestamp"),        // "2018-06-04T19:36:43.251000Z"
                getFieldValue[Double]("price"),            // "2018-06-06"
                getFieldValue[String]("settlement_date"),
                getFieldValue[String]("id"),
                getFieldValue[Double]("quantity")
            )
        }
    }
}

class Order(
        val updatedAt: String,                                 // "2018-06-04T19:36:43.517428Z"
//        refId: String,
//        timeInForce: String,
        val fees: Double,
//        cancel: Option[String],
//        responseCategory: String,
        val id: String,
        val cummulativeQuantity: Double,
//        stopPrice: Option[Double],
//        rejectReason: Option[String],
        val instrument: String,
        val state: String,
//        trigger: String,
//        overrideDtbpChecks: Boolean,
//        orderType: String,
        val lastTransactionAt: String,
        val price: Double,
        val executions: Array[OrderExecution],
//        extendedHours: Boolean,
//        account: String,
//        url: String,
        val createdAt: String,                                 // "2018-06-04T19:10:50.440368Z"
        val side: String,
//        overrideDayTradeChecks: Boolean,
        val position: String,                                  // this is the position url
        val averagePrice: Double,
        val quantity: Double,
        var matchId: String
) {
  override def toString: String = s"Order(updatedAt='$updatedAt', fees=$fees, id='$id', " +
    s"cummulativeQuantity=$cummulativeQuantity, instrument='$instrument', state='$state', " +
    s"lastTransactionAt='$lastTransactionAt', price=$price, createdAt='$createdAt', side='$side', position='$position', " +
    s"averagePrice=$averagePrice, quantity=$quantity, matchId='$matchId')"
}

object OrderProtocol extends DefaultJsonProtocol {
    implicit object OrderJsonFormat extends RootJsonFormat[Order] with Util {
        override def read(json: JsValue): Order = {
            import OrderExecutionProtocol._
            implicit val fields: Map[String, JsValue] = json.asJsObject("Unable to convert to JsObject").fields
            implicit val prettyJson: String = json.prettyPrint
            implicit val logger: Logger = Logger(classOf[Order])

            new Order(
                getFieldValue[String]("updated_at"),
                getFieldValue[Double]("fees"),
                getFieldValue[String]("id"),
                getFieldValue[Double]("cumulative_quantity"),
                getFieldValue[String]("instrument"),
                getFieldValue[String]("state"),
                getFieldValue[String]("last_transaction_at"),
                getFieldValue[Double]("price"),
                fields.get("executions") match {
                    case Some(x) => x match {
                        case y: JsArray => y.elements.map(_.convertTo[OrderExecution]).toArray
                        case _ =>
                            logger.error(s"field execution is not an array in $prettyJson")
                            Array[OrderExecution]()
                    }
                    case _ =>
                        logger.error(s"field execution doesn't exist in $prettyJson")
                        Array[OrderExecution]()
                },
                getFieldValue[String]("created_at"),
                getFieldValue[String]("side"),
                getFieldValue[String]("position"),
                getFieldValue[Double]("average_price"),
                getFieldValue[Double]("quantity"),
                ""
            )
        }

        override def write(o: Order): JsValue = JsObject(
            "updatedAt" -> JsString(o.updatedAt),
            "fees" -> JsNumber(o.fees),
            "id" -> JsString(o.id),
            "cummulativeQuantity" -> JsNumber(o.cummulativeQuantity),
            "instrument" -> JsString(o.instrument),
            "state" -> JsString(o.state),
            "lastTransactionAt" -> JsString(o.lastTransactionAt),
            "price" -> JsNumber(o.price),
            "averagePrice" -> JsNumber(o.averagePrice),
            "createdAt" -> JsString(o.createdAt),
            "side" -> JsString(o.side),
            "quantity" -> JsNumber(o.quantity),
            "matchId" -> (if (o.matchId.isEmpty) JsNull else JsString(o.matchId))
        )
    }

    implicit object OrderListJsonFormat extends RootJsonFormat[List[Order]] with Util {
        override def read(json: JsValue): List[Order] = ???

        override def write(orders: List[Order]): JsValue = JsArray(orders.map(_.toJson).toVector)
    }
}