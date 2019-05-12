package home

import akka.event.LoggingAdapter
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.typesafe.config.ConfigFactory
import com.softwaremill.sttp._
import home.util.{LoggingAdapterImpl, SttpBackendUtil}
import org.junit.jupiter.api.{Assertions, Disabled, Test}

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

class WatchedListUtilTests extends WatchedListUtil with SttpBackendUtil {
    @Disabled("Use a valid accessToken in the code (or in the system environment) to run this test")
    @Test
    def testRetrieveWatchedInstruments(): Unit = {
        val accessToken = sys.env.getOrElse("accessToken", sys.env.getOrElse("ACCESSTOKEN", "..."))
        implicit val be: SttpBackend[Future, Source[ByteString, Any]] =
            configureAkkaHttpBackend(ConfigFactory.load("credentials.conf"))
        implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.global
        implicit val log: LoggingAdapter = LoggingAdapterImpl

        val instruments = Await.result(retrieveWatchedInstruments(accessToken), 9.seconds)

        Assertions.assertTrue(instruments.length > 100, "Too few watched symbols.")

        val instrumentUuid = raw"([a-f0-9]{8})-([a-f0-9]{4})-([a-f0-9]{4})-([a-f0-9]{4})-([a-f0-9]{12})".r
        Assertions.assertTrue(instruments.forall {
            case instrumentUuid(_*) => true
            case instrument: String =>
                log.error("Error: {} is not a uuid", instrument)
                false
        }, "Some are not uuid")
    }
}
