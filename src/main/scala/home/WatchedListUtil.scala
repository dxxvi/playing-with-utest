package home

import akka.event.LoggingAdapter
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.softwaremill.sttp._
import home.util.JsonUtil
import org.json4s.JsonAST._
import org.json4s.native.JsonMethods._

import scala.concurrent.{ExecutionContext, Future}

trait WatchedListUtil extends JsonUtil with AppUtil {
    /**
      * @return a list of instrument uuid's, e.g. List("776d31c1-e278-4476-9d03-9e7125fe946c", "...", ...)
      */
    def retrieveWatchedInstruments(accessToken: String)
                                  (implicit be: SttpBackend[Future, Source[ByteString, Any]],
                                            ec: ExecutionContext,
                                            log: LoggingAdapter): Future[List[String]] = {
        sttp
                .auth.bearer(accessToken)
                .get(uri"https://api.robinhood.com/watchlists/Default/")
                .response(asString)
                .send()
                .collect {
                    case Response(Left(_), _, statusText, _, _) =>
                        log.error("Unable to get the default watch list: {}\nCheck the accessToken: {}",
                            statusText, accessToken)
                        System.exit(-1)
                        Nil
                    case Response(Right(jString), _, _, _, _) =>
                        val json = parse(jString)
                        json \ "next" match {
                            case JNull | JNothing =>
                            case JString(s) => log.warning("There's a next field in default watched list {}", s)
                            case _ =>
                                log.error("Error: Weird next field in {}", jString)
                                System.exit(-1)
                        }

                        json \ "results" match {
                            case JArray(arr) => arr
                                    .map(jv => fromJValueToOption[String](jv \ "instrument"))
                                    .collect { case Some(instrument) => extractInstrument(instrument) }
                            case _ =>
                                log.error("No results field in {}", jString)
                                Nil
                        }
                }
    }
}
