package home

import akka.event.LoggingAdapter
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.softwaremill.sttp._
import com.typesafe.config.ConfigFactory
import home.model.Quote
import home.util.{LoggingAdapterImpl, SttpBackendUtil}
import org.junit.jupiter.api.{Assertions, Disabled, Test}

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._

class QuoteUtilTests extends QuoteUtil with SttpBackendUtil {
    @Disabled("Use a valid accessToken in the code (or in the system environment) to run this test")
    @Test
    def testGet5minQuotes(): Unit = {
        val accessToken = sys.env.getOrElse("accessToken", sys.env.getOrElse("ACCESSTOKEN", "already set in idea.bat"))
        implicit val log: LoggingAdapter = LoggingAdapterImpl
        implicit val backend: SttpBackend[Future, Source[ByteString, Any]] =
            configureAkkaHttpBackend(ConfigFactory.load("credentials.conf"))
        implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.global

        val symbols = List("AMD", "CY", "ON", "TSLA")
        val x: Future[List[(String, String, String, List[Quote])]] =
            get5minQuotes(accessToken, symbols)
        val y: List[(String, String, String, List[Quote])] = Await.result(x, 10.seconds)

        Assertions.assertEquals(symbols.length, y.length, "missing some symbols")
    }

    @Disabled("Use a valid accessToken in the code (or in the system environment) to run this test")
    @Test
    def testGetStats(): Unit = {
        val accessToken = sys.env.getOrElse("accessToken", sys.env.getOrElse("ACCESSTOKEN", "already set in idea.bat"))
        implicit val log: LoggingAdapter = LoggingAdapterImpl
        implicit val backend: SttpBackend[Future, Source[ByteString, Any]] =
            configureAkkaHttpBackend(ConfigFactory.load("credentials.conf"))
        implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.global

        val symbols = List("AMD", "CY", "ON", "TSLA")
        val map = Await.result(getStats(accessToken, symbols), 999.seconds)
        println(map)
    }

    @Test
    def testGetDate2PreviousClose(): Unit = {
        val list = List(
            Quote("2019-03-30T00:00:00.0Z", 0, 1, 0, 0),
            Quote("2019-03-30T01:00:00.0Z", 0, 3, 0, 0),
            Quote("2019-03-30T07:00:00.0Z", 0, 7, 0, 0),
            Quote("2019-04-04T07:00:00.0Z", 0, 2, 0, 0),
        )
        val map = getDate2PreviousClose(list)
        Assertions.assertEquals(1, map.toList.length)
        Assertions.assertEquals(7, map("2019-04-04"))
    }
}
