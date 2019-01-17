package home.util

import com.typesafe.config.{Config, ConfigFactory}
import org.junit.jupiter.api.Assertions._
import org.junit.jupiter.api.{Disabled, Test}

import scala.util.{Failure, Success, Try}

/*
@Disabled("This test class can run in the IDE only where -Dauthorization.username=x " +
        "-Dauthorization.encryptedPassword=\"x\" is specified in the VM options and " +
        "key=x is specified in the Environment variables")
*/
class UtilTests {
    private def retrieveAccessToken(config: Config) {
        Util.retrieveAccessToken(config) match {
            case Right(x) => home.Main.accessToken = x
            case Left(errorMessage) => fail(errorMessage)
        }
    }

    @Test
    def testGetSymbolFromInstrumentHttpURLConnection() {
        val config = ConfigFactory.load()
        retrieveAccessToken(config)

        val goodInstrumentUrl = "https://api.robinhood.com/instruments/dad8fa2c-1e8d-4cb9-b354-1f0b91a4193e/"
        Util.getSymbolFromInstrumentHttpURLConnection(goodInstrumentUrl, config) match {
            case Success(symbol) => assertEquals("ON", symbol, s"$goodInstrumentUrl should be for ON")
            case Failure(ex) => fail(ex)
        }

        val badInstrumentUrl1 = "https://api.robinhood.com/instruments/dad8fa2c-1e8d-4cb9-b354-1f0b91a4193ex/"
        Util.getSymbolFromInstrumentHttpURLConnection(badInstrumentUrl1, config) match {
            case Success(s) => fail(s"Never happen $s")
            case Failure(ex) => println(s"This is expected: $ex")
        }

        val badInstrumentUrl2 = "https://a.r.c/instruments/dad8fa2c-1e8d-4cb9-b354-1f0b91a4193e/"
        Util.getSymbolFromInstrumentHttpURLConnection(badInstrumentUrl2, config) match {
            case Success(s) => fail(s"Never happen $s")
            case Failure(ex) => println(s"This is expected: $ex")
        }
    }

    @Test
    def testGetDailyQuoteHttpURLConnection() {
        import home.QuoteActor.DailyQuote

        val config = ConfigFactory.load()
        retrieveAccessToken(config)
        val list = Util.getDailyQuoteHttpURLConnection(Set("AMD", "TSLA"), config)

        assertEquals(2, list.size)

        val amdOption: Option[(String, List[DailyQuote])] = list.find(_._1 == "AMD")
        assertTrue(amdOption.nonEmpty)
        assertTrue(amdOption.get._2.size > 241, "Not enough AMD daily quotes")

        val tslaOption: Option[(String, List[DailyQuote])] = list.find(_._1 == "TSLA")
        assertTrue(tslaOption.nonEmpty)
        assertTrue(tslaOption.get._2.size > 241, "Not enough TSLA daily quotes")
    }

    @Test
    def testExtractSymbolAndOrder() {
        import com.softwaremill.sttp._

        val config = ConfigFactory.load()
        retrieveAccessToken(config)
        implicit val backend: SttpBackend[Id, Nothing] = Util.configureCoreJavaHttpBackend(config)
        val SERVER = config.getString("server")
        val AUTHORIZATION = "Authorization"
        val authorization: String = "Bearer " + home.Main.accessToken

        home.Main.addStocksToDatabase(config)

        sttp.header(AUTHORIZATION, authorization).get(uri"$SERVER/orders/").send().body match {
            case Right(js) =>
                val list: List[(String, home.StockActor.Order)] = Util.extractSymbolAndOrder(js)
                assertEquals(100, list.size, "Should get 100 orders")
                assertTrue(!list.exists(t => t._2.averagePrice.isNaN || t._2.price.isNaN),
                    "No order should have average_price or price of Double.NaN")
            case Left(s) => throw new RuntimeException(s)
        }
    }

    @Test
    def testRetrieveAccessToken() {
        val config = ConfigFactory.load()
        Util.retrieveAccessToken(config) match {
            case Right(accessToken) => println(accessToken)
            case Left(s) => fail(s)
        }
    }
}
