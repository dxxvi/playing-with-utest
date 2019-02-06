package home

import com.softwaremill.sttp._
import com.typesafe.config.ConfigFactory
import home.util.{SttpBackends, Util}
import org.junit.jupiter.api.Assertions._
import org.junit.jupiter.api.{Disabled, Test}

@Disabled("This test is used only when the application is running without `akkaTimers` in the system environment (so " +
        "that the `timersx` is not effective.")
class ApplicationTest extends SttpBackends {
    implicit val backend: SttpBackend[Id, Nothing] = configureCoreJavaHttpBackend(ConfigFactory.load())
    val APP: String = "http://localhost:4567"

    @Test
    def test() {
        import org.json4s._
        import org.json4s.native.JsonMethods._

        Set("AMD", "CY", "ON", "TSLA", "CVX", "TSLA").foreach(symbol =>
            sttp
                    .get(uri"$APP/$symbol/debug")
                    .send()
                    .body match {
                case Left(s) => fail(s)
                case Right(s) =>
                    val jv = parse(s)
                    val ltp = Util.fromJValueToOption[Double](jv \ "ltp")
                    assertTrue(ltp.isDefined && !ltp.get.isNaN, s"Error: no ltp for $symbol")
                    val position = Util.fromJValueToOption[Double](jv \ "position")
                    assertTrue(position.isDefined && !position.get.isNaN, s"Error: no position for $symbol")
                    if (symbol != "CVX") {
                        val ordersString = Util.fromJValueToOption[String](jv \ "orders")
                        assertTrue(ordersString.isDefined && ordersString.get.nonEmpty, s"Error: no order for $symbol")
                    }
                    val openPrice = Util.fromJValueToOption[Double](jv \ "openPrice")
                    assertTrue(openPrice.get.isNaN, s"openPrice should be empty in $s")
                    val todayLow = Util.fromJValueToOption[Double](jv \ "todayLow")
                    assertTrue(todayLow.get.isNaN, s"todayLow should be empty in $s")
                    val todayHigh = Util.fromJValueToOption[Double](jv \ "todayHigh")
                    assertTrue(todayHigh.get.isNaN, s"todayHigh should be empty in $s")
            }
        )

        sttp.get(uri"$APP/quote/tick").send().body match {
            case Left(s) => fail(s)
            case Right(s) =>
                Thread.sleep(5000) // wait for QuoteActor to get last trade prices and interval quotes for all stocks
                Set("AMD", "CY", "ON", "TSLA", "CVX", "TSLA").foreach(symbol =>
                    sttp.get(uri"$APP/$symbol/debug").send().body match {
                        case Left(x) => fail(x)
                        case Right(x) =>
                            val jv = parse(x)
                            val ltp = Util.fromJValueToOption[Double](jv \ "ltp")
                            assertTrue(ltp.isDefined && !ltp.get.isNaN, s"Error: no ltp for $symbol")
                            val openPrice = Util.fromJValueToOption[Double](jv \ "openPrice")
                            assertFalse(openPrice.get.isNaN, s"openPrice should be there in $x")
                            val todayLow = Util.fromJValueToOption[Double](jv \ "todayLow")
                            assertFalse(todayLow.get.isNaN, s"todayLow should be there in $x")
                            val todayHigh = Util.fromJValueToOption[Double](jv \ "todayHigh")
                            assertFalse(todayHigh.get.isNaN, s"todayHigh should be there in $x")
                    }
                )
        }
    }

    /**
      * Use this in robinhood website to count how many successful transactions there are for a stock:
      * window._var1 = Array.from(document.querySelectorAll('h2')).filter(h2 => h2.childElementCount === 0 && h2.textContent === 'Older')[0].nextElementSibling
      * Array.from(window._var1.querySelectorAll('h3')).filter(h3 => h3.childElementCount === 0 && h3.textContent.indexOf('$') >= 0)
      */
    @Test
    def test2() {
        import org.json4s._
        import org.json4s.native.JsonMethods._

        sttp.get(uri"$APP/CY/debug").send().body match {
            case Left(s) => fail(s)
            case Right(s) =>
        }
    }
}
