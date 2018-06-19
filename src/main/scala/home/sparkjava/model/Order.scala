package home.sparkjava.model

import com.typesafe.scalalogging.Logger
import home.sparkjava.Util
import spray.json._

case class OrderExecution(
        timestamp: String,                                 // "2018-06-04T19:36:43.251000Z"
        price: Double,
        settlementDate: String,                            // yyyy-MM-dd
        id: String,
        quantity: Double
)

object OrderExecutionProtocol extends DefaultJsonProtocol {
    implicit object OrderExecutionJsonFormat extends RootJsonFormat[OrderExecution] with Util {
        override def write(obj: OrderExecution): JsValue = ???

        override def read(json: JsValue): OrderExecution = {
            implicit val fields: Map[String, JsValue] = json.asJsObject("Unable to convert to JsObject").fields
            implicit val prettyJson: String = json.prettyPrint
            implicit val logger: Logger = Logger[OrderExecution]

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

case class Order(
        updatedAt: String,                                 // "2018-06-04T19:36:43.517428Z"
//        refId: String,
//        timeInForce: String,
        fees: Double,
//        cancel: Option[String],
//        responseCategory: String,
        id: String,
        cummulativeQuantity: Double,
//        stopPrice: Option[Double],
//        rejectReason: Option[String],
        instrument: String,
        state: String,
//        trigger: String,
//        overrideDtbpChecks: Boolean,
//        orderType: String,
        lastTransactionAt: String,
        price: Double,
        executions: Array[OrderExecution],
//        extendedHours: Boolean,
//        account: String,
//        url: String,
        createdAt: String,                                 // "2018-06-04T19:10:50.440368Z"
        side: String,
//        overrideDayTradeChecks: Boolean,
        position: String,
//        averagePrice: Double,
        quantity: Double
)

object OrderProtocol extends DefaultJsonProtocol {
    implicit object OrderJsonFormat extends RootJsonFormat[Order] with Util {
        override def read(json: JsValue): Order = {
            import OrderExecutionProtocol._
            implicit val fields: Map[String, JsValue] = json.asJsObject("Unable to convert to JsObject").fields
            implicit val prettyJson: String = json.prettyPrint
            implicit val logger: Logger = Logger[Order]

            Order(
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
                getFieldValue[Double]("quantity")

            )
        }

        override def write(order: Order): JsValue = ???
    }
}