package home.util

import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.softwaremill.sttp._
import com.typesafe.config.ConfigFactory
import org.json4s._
import org.json4s.native.JsonMethods._
import org.json4s.native.Serialization.writePretty
import org.junit.jupiter.api.{Assertions, Test}

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._

// This test doesn't run without proxy.host/port/username/password
class SttpBackendUtilTests extends SttpBackendUtil {
    @Test
    def testAkkaBackend(): Unit = {
        val uri = uri"https://api.robinhood.com/instruments/4c799e5b-628a-484c-a218-45a1086d0ad0/"
        val config = ConfigFactory.load()
        implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.global
        implicit val backend: SttpBackend[Future, Source[ByteString, Any]] = configureAkkaHttpBackend(config)

        val future = sttp
                .get(uri)
                .response(asString)
                .send()
                .map {
                    case Response(Left(_), _, statusText, _, _) =>
                        Assertions.fail(s"Failed with http status $statusText")
                    case Response(Right(jsonString), _, _, _, _) =>
                        println(writePretty(parse(jsonString))(DefaultFormats))
                }
        Await.result(future, 5.seconds)
    }

    @Test
    def testJavaBackend(): Unit = {
        val teslaUri = uri"https://api.robinhood.com/instruments/e39ed23a-7bd1-4587-b060-71988d9ef483/"
        val config = ConfigFactory.load()
        implicit val backend: SttpBackend[Id, Nothing] = configureCoreJavaHttpBackend(config)

        sttp
                .get(teslaUri)
                .send()
                .body match {
            case Left(error) => Assertions.fail(s"Fail: $error")
            case Right(jsonString) => println(writePretty(parse(jsonString))(DefaultFormats))
        }
    }
}
