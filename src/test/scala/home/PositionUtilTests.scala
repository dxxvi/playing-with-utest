package home

import akka.event.LoggingAdapter
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.softwaremill.sttp._
import com.typesafe.config.ConfigFactory
import home.util.{LoggingAdapterImpl, SttpBackendUtil}
import org.junit.jupiter.api.{Assertions, Disabled, Test}

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

class PositionUtilTests extends PositionUtil with SttpBackendUtil {
    @Test
    def testGetAllPositions(): Unit = {
        val config = ConfigFactory.load("credentials.conf")
        val accessToken = sys.env.getOrElse("accessToken", sys.env.getOrElse("ACCESSTOKEN", "already set in idea.bat"))
        implicit val log: LoggingAdapter = LoggingAdapterImpl
        implicit val backend: SttpBackend[Future, Source[ByteString, Any]] = configureAkkaHttpBackend(config)
        implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.global

        val positions: Map[String, (Double, String /*account*/)] = Await.result(getAllPositions(accessToken), 5.seconds)

        Assertions.assertTrue(positions.nonEmpty)
        Assertions.assertEquals(1, positions.values.map(_._2).toList.distinct.length)
    }

}
