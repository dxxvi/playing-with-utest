package home

import akka.event.LoggingAdapter
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.softwaremill.sttp._
import com.typesafe.config.ConfigFactory
import home.model.{Order, Quote}
import home.util.{LoggingAdapterImpl, SttpBackendUtil}
import org.junit.jupiter.api.{Assertions, Disabled, Test}

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

class OrderUtilTests extends OrderUtil with WatchedListUtil with PositionUtil with SttpBackendUtil {
    @Test
    def testGetRecentStandardizedOrders(): Unit = {
        val accessToken = sys.env.getOrElse("accessToken", sys.env.getOrElse("ACCESSTOKEN", "already set in idea.bat"))
        implicit val log: LoggingAdapter = LoggingAdapterImpl
        implicit val backend: SttpBackend[Future, Source[ByteString, Any]] =
            configureAkkaHttpBackend(ConfigFactory.load("credentials.conf"))
        implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.global

        val x: Future[List[Order]] = getRecentStandardizedOrders(accessToken)
        val y: List[Order] = Await.result(x, 5.seconds)
        Assertions.assertTrue(y.length > 82, "not enough recent orders")
    }

    @Test
    def testGetAllStandardizedOrdersForInstrument(): Unit = {
        val accessToken = sys.env.getOrElse("accessToken", sys.env.getOrElse("ACCESSTOKEN", "already set in idea.bat"))
        implicit val log: LoggingAdapter = LoggingAdapterImpl
        implicit val backend: SttpBackend[Id, Nothing] =
            configureCoreJavaHttpBackend(ConfigFactory.load("credentials.conf"))
        implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.global

        val x = getAllStandardizedOrdersForInstrument(accessToken, "c7741741-989b-46f8-ab84-a4ba934e7601")

        Assertions.assertTrue(x.length > 10, "check")
    }

    @Test
    def testWriteOrderHistoryJsonToFile(): Unit = {
        val config = ConfigFactory.load("credentials.conf")
        val accessToken = sys.env.getOrElse("accessToken", sys.env.getOrElse("ACCESSTOKEN", "already set in idea.bat"))
        implicit val log: LoggingAdapter = LoggingAdapterImpl
        implicit val backend: SttpBackend[Future, Source[ByteString, Any]] = configureAkkaHttpBackend(config)
        implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.global

        val watchedInstruments: Set[String] = Await.result(retrieveWatchedInstruments(accessToken), 5.seconds).toSet
        val positions: Map[String, (Double, String)] = Await.result(getAllPositions(accessToken), 5.seconds)
        val instruments = positions.toStream
                .collect {
                    case (instrument, (quantity, _)) if (watchedInstruments contains instrument) && quantity > 0 =>
                        instrument
                }
                .toList
        writeOrderHistoryJsonToFile(accessToken, instruments)(configureCoreJavaHttpBackend(config), ec, log)
    }

    @Test
    def testGetEffectiveOrders(): Unit = {
        val config = ConfigFactory.load("credentials.conf")
        val accessToken = sys.env.getOrElse("accessToken", sys.env.getOrElse("ACCESSTOKEN", "already set in idea.bat"))
        implicit val log: LoggingAdapter = LoggingAdapterImpl
        implicit val backend: SttpBackend[Id, Nothing] = configureCoreJavaHttpBackend(config)
        implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.global

        val orders = getAllStandardizedOrdersForInstrument(accessToken, "940fc3f5-1db5-4fed-b452-f3a2e4562b5f")
        val effectiveOrders = getEffectiveOrders(4, orders, log)

        Assertions.assertTrue(effectiveOrders.length > 1)
    }

    @Test
    def testAssignMatchId(): Unit = {

    }
}
