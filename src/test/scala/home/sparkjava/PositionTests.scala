package home.sparkjava

import model.Position
import org.apache.logging.log4j.scala.Logger
import spray.json._
import utest._

object PositionTests extends TestSuite with home.Util with Util {
    val tests = Tests {
        "test Position" - {
            import model.PositionProtocol._
            val logger: Logger = Logger(classOf[Position])

            var json =
                """
                  |{
                  |  "shares_held_for_stock_grants": "0.0000",
                  |  "account": "https://api.robinhood.com/accounts/5RY82436/",
                  |  "pending_average_buy_price": "0.0000",
                  |  "shares_held_for_options_events": "0.0000",
                  |  "intraday_average_buy_price": "0.0000",
                  |  "url": "https://api.robinhood.com/positions/5RY82436/19520986-989b-4332-9743-33da5cc3db20/",
                  |  "shares_held_for_options_collateral": "0.0000",
                  |  "created_at": "2018-05-22T17:55:18.368702Z",
                  |  "updated_at": "2018-06-06T18:20:56.059832Z",
                  |  "shares_held_for_buys": "0.0000",
                  |  "average_buy_price": "16.7300",
                  |  "instrument": "https://api.robinhood.com/instruments/19520986-989b-4332-9743-33da5cc3db20/",
                  |  "intraday_quantity": "0.0000",
                  |  "shares_held_for_sells": "0.0000",
                  |  "shares_pending_from_options_events": "0.0000",
                  |  "quantity": "0.0000"
                  |}
                """.stripMargin
            val position = json.parseJson.convertTo[Position]
            assert(position.averageBuyPrice == 16.73)

            val positions = getPositions(readTextFileFromTestResource("robinhood", "positions.json"))
            assert(positions.length > 19)
        }
    }
}
