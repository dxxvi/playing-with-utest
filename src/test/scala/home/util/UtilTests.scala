package home.util

import com.typesafe.config.ConfigFactory
import org.junit.Assert._
import org.junit.Test

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
}
