package home

import akka.event.LoggingAdapter
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.softwaremill.sttp._
import com.typesafe.config.ConfigFactory
import home.util.{LoggingAdapterImpl, SttpBackendUtil}
import org.json4s._
import org.json4s.JsonAST.{JInt, JNull, JObject, JString}
import org.json4s.native.JsonMethods._
import org.json4s.native.Serialization
import org.junit.jupiter.api.{Assertions, Disabled, Test}

import scala.concurrent.{ExecutionContext, Future}

class StockDatabaseTests extends SttpBackendUtil {
//    @Disabled("Use a valid accessToken in the code (or in the system environment) to run this test")
    @Test
    def testCreate(): Unit = {
        val config = ConfigFactory.load("credentials.conf")
        val accessToken = sys.env.getOrElse("accessToken", sys.env.getOrElse("ACCESSTOKEN", "already set in idea.bat"))
        implicit val be1: SttpBackend[Future, Source[ByteString, Any]] = configureAkkaHttpBackend(config)
        implicit val be2: SttpBackend[Id, Nothing] = configureCoreJavaHttpBackend(config)
        implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.global
        implicit val log: LoggingAdapter = LoggingAdapterImpl

        val stockDatabase = StockDatabase.create(accessToken)
        Assertions.assertTrue(stockDatabase.nonEmpty, "stockDatabase is empty")
    }
}
