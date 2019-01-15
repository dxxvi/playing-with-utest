package home.util

import com.typesafe.config.ConfigFactory
import org.junit.jupiter.api.Assertions._
import org.junit.jupiter.api.Test

import scala.util.{Failure, Success, Try}

class UtilTests {
    @Test
    def testGetSymbolFromInstrumentHttpURLConnection() {
        val config = ConfigFactory.load()

        val goodInstrumentUrl = "https://api.robinhood.com/instruments/dad8fa2c-1e8d-4cb9-b354-1f0b91a4193e/"
        assertEquals("ON", Util.getSymbolFromInstrumentHttpURLConnection(goodInstrumentUrl, config))

        val badInstrumentUrl1 = "https://api.robinhood.com/instruments/dad8fa2c-1e8d-4cb9-b354-1f0b91a4193ex/"
        Try(Util.getSymbolFromInstrumentHttpURLConnection(badInstrumentUrl1, config)) match {
            case Success(s) => fail(s"Never happen $s")
            case Failure(ex) => println(s"This is expected: $ex")
        }

        val badInstrumentUrl2 = "https://a.r.c/instruments/dad8fa2c-1e8d-4cb9-b354-1f0b91a4193e/"
        Try(Util.getSymbolFromInstrumentHttpURLConnection(badInstrumentUrl2, config)) match {
            case Success(s) => fail(s"Never happen $s")
            case Failure(ex) => println(s"This is expected: $ex")
        }
    }

    @Test
    def testGetDailyQuoteHttpURLConnection() {
        import home.QuoteActor.DailyQuote

        val config = ConfigFactory.load()
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
        implicit val backend: SttpBackend[Id, Nothing] = Util.configureCoreJavaHttpBackend(config)
        val SERVER = config.getString("server")
        val AUTHORIZATION = "Authorization"
        val authorization: String = if (config.hasPath(AUTHORIZATION)) config.getString(AUTHORIZATION) else "No-token"

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
}
