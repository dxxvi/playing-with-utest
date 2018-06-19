package home.sparkjava

import com.typesafe.scalalogging.Logger
import home.sparkjava.model.Fundamental
import spray.json._
import utest._

object FundamentalTests extends TestSuite with home.Util with Util {
    val tests = Tests {
        "test Fundamental" - {
            import model.FundamentalProtocol._
            val logger: Logger = Logger[Fundamental]

            val fundamental =
                readTextFileFromTestResource("robinhood", "fundamental.json").parseJson.convertTo[Fundamental]
            assert(fundamental.open == 347.5)
            assert(fundamental.dividendYield == 0)
        }
    }
}
