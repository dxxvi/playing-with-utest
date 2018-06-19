package home.sparkjava

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse, StatusCodes, Uri}
import akka.http.scaladsl.model.Uri.Query
import akka.stream.ActorMaterializer
import akka.util.ByteString
import com.typesafe.scalalogging.Logger
import home.sparkjava.model.Quote
import utest._

import scala.concurrent.{ExecutionContextExecutor, Future}

object QuoteTests extends TestSuite with home.Util with home.sparkjava.Util {
    val tests = Tests {
        "test Quote" - {
            val logger = Logger[Quote]
            val json = readTextFileFromTestResource("robinhood", "quotes.json")
            val quotes = getQuotes(json)
            assert(quotes.length > 2)
        }

        "test getting quotes" - {
            val logger = Logger[Quote]
            implicit val system: ActorSystem = ActorSystem("R")
            implicit val materializer: ActorMaterializer = ActorMaterializer()
            // needed for the flatMap / onComplete
            implicit val executionContext: ExecutionContextExecutor = system.dispatcher
            val http = Http()

            val uri = Uri("https://api.robinhood.com/quotes/")
                    .withQuery(Query(("symbols", "AMD,HTZ,CY,ON")))
            val responseFuture: Future[HttpResponse] = http.singleRequest(HttpRequest(uri = uri))
            responseFuture
                    .map {
                        case response @ HttpResponse(StatusCodes.OK, _, entity, _) =>
                            entity.dataBytes.runFold(ByteString(""))(_ ++ _).foreach { body =>
                                logger.debug("Got response, body: " + body.utf8String)
                            }
                        case response: HttpResponse =>
                            logger.debug(s"Http Status Code: ${response.status}")
                            response.discardEntityBytes()
                    }
/*
                    .onComplete(_ => {
                        Thread.sleep(5904)
                        system.terminate()
                    })
*/
        }
    }
}
