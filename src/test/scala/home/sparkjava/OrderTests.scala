package home.sparkjava

import com.typesafe.scalalogging.Logger
import home.sparkjava.model.{Order, OrderExecution}
import spray.json._
import utest._

object OrderTests extends TestSuite with home.Util with Util {
    val tests = Tests {
        "test OrderExecution" - {
            import model.OrderExecutionProtocol._
            val logger: Logger = Logger[OrderExecution]

            var json =
                """
                  |{
                  |  "timestamp": "2018-06-04T19:36:43.251000Z",
                  |  "price": "14.47000000",
                  |  "settlement_date": "2018-06-06",
                  |  "id": "d5819c26-26e9-47f2-930e-521a4d8f5401",
                  |  "quantity": "20.00000"
                  |}
                """.stripMargin
            val orderExecution = json.parseJson.convertTo[OrderExecution]
            assert(orderExecution.id == "d5819c26-26e9-47f2-930e-521a4d8f5401")

            json =
                """
                  |[
                  |  {
                  |    "timestamp": "2018-06-04T19:36:43.251000Z",
                  |    "price": "14.47000000",
                  |    "settlement_date": "2018-06-06",
                  |    "id": "d5819c26-26e9-47f2-930e-521a4d8f5401",
                  |    "quantity": "20.00000"
                  |  },
                  |  {
                  |    "timestamp": "2017-05-03T18:25:32.140999Z",
                  |    "price": "25.58000000",
                  |    "settlement_date": "2017-05-05",
                  |    "id": "c4708b15-15d8-36e1-829d-41093c7e4390",
                  |    "quantity": "19.00000"
                  |  }
                  |]
                """.stripMargin
            val orderExecutions: Array[OrderExecution] = json.parseJson.convertTo[Array[OrderExecution]]
            assert(orderExecutions.length == 2)
        }

        "test Order" - {
            val logger: Logger = Logger[Order]
            val orders = getOrders(readTextFileFromTestResource("robinhood", "orders.json"))
            assert(orders.length > 2)
        }
    }
}
