package home

import akka.event.LoggingAdapter
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.softwaremill.sttp._
import com.typesafe.config.ConfigFactory
import home.util.{LoggingAdapterImpl, SttpBackendUtil}
import org.junit.jupiter.api.{Assertions, Disabled, Test}

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._

class LastTradePriceUtilTests extends LastTradePriceUtil with SttpBackendUtil {
    @Test
    def testGetLastTradePrices(): Unit = {
        val accessToken = sys.env.getOrElse("accessToken", sys.env.getOrElse("ACCESSTOKEN", "..."))
        implicit val backend: SttpBackend[Future, Source[ByteString, Any]] =
            configureAkkaHttpBackend(ConfigFactory.load("credentials.conf"))
        implicit val ec: ExecutionContext = ExecutionContext.global
        implicit val log: LoggingAdapter = LoggingAdapterImpl

        val symbols = List("AMD", "TSLA", "ON", "CY")
        val lastTradePrices = Await.result(getLastTradePrices(accessToken, symbols), 5.seconds)
        Assertions.assertEquals(symbols.length, lastTradePrices.length, "not get enough last trade prices")
        Assertions.assertTrue(lastTradePrices.forall(ltp => symbols.contains(ltp.symbol)), "got strange symbol")
    }
}
