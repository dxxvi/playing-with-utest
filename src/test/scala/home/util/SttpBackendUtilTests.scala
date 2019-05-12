package home.util

import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.softwaremill.sttp._
import com.typesafe.config.ConfigFactory
import org.junit.jupiter.api.{Assertions, Disabled, Test}

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._

class SttpBackendUtilTests extends SttpBackendUtil {
    val teslaUri = uri"https://api.robinhood.com/instruments/e39ed23a-7bd1-4587-b060-71988d9ef483/"

    @Disabled("Use a valid accessToken in the code (or in the system environment) to run this test")
    @Test
    def testConfigureAkkaHttpBackend(): Unit = {
        val config = ConfigFactory.load("credentials.conf")
        implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.global
        implicit val httpBackend: SttpBackend[Future, Source[ByteString, Any]] = configureAkkaHttpBackend(config)

        val future = sttp
                .get(teslaUri)
                .response(asString)
                .send()
                .map {
                    case Response(Left(_), _, statusText, _, _) =>
                        Assertions.fail(s"Failed with http status $statusText")
                    case Response(Right(jsonString), _, _, _, _) =>
                        Assertions.assertTrue(jsonString.startsWith("{") && jsonString.endsWith("}"))
                        println(s"\n$jsonString\n")
                }
        Await.result(future, 5.seconds)
    }

    @Disabled("Use a valid accessToken in the code (or in the system environment) to run this test")
    @Test
    def testConfigureCoreJavaHttpBackend(): Unit = {
        val config = ConfigFactory.load("credentials.conf")
        implicit val httpBackend: SttpBackend[Id, Nothing] = configureCoreJavaHttpBackend(config)

        sttp
                .get(teslaUri)
                .send()
                .body match {
            case Left(error) => Assertions.fail(s"Fail: $error")
            case Right(jsonString) =>
                Assertions.assertTrue(jsonString.startsWith("{") && jsonString.endsWith("}"))
                println(s"\n$jsonString\n")
        }
    }
}
