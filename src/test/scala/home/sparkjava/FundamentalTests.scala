package home.sparkjava

import home.sparkjava.model.Fundamental
import org.apache.logging.log4j.scala.Logger
import spray.json._
import utest._

object FundamentalTests extends TestSuite with home.Util with Util {
    val tests = Tests {
        "test Fundamental" - {
            import model.FundamentalProtocol._
            val logger: Logger = Logger(classOf[Fundamental])

            val fundamental =
                readTextFileFromTestResource("robinhood", "fundamental.json").parseJson.convertTo[Fundamental]
            assert(fundamental.open == 347.5)
            assert(fundamental.dividendYield == 0)
        }
    }
}
