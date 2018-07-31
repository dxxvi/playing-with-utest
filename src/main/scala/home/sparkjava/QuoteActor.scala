package home.sparkjava

import scala.concurrent.duration._
import akka.actor.{Actor, Props, Timers}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.Uri.Query
import akka.http.scaladsl.model._
import akka.http.scaladsl.settings.ConnectionPoolSettings
import akka.stream.{ActorMaterializer, ActorMaterializerSettings}
import akka.util.ByteString
import com.typesafe.config.Config
import home.sparkjava.QuoteActor.DailyQuotesResponse
import org.apache.logging.log4j.scala.Logging

object QuoteActor {
    val NAME = "quoteActor"
    def props(config: Config): Props = Props(new QuoteActor(config))

    case class DailyQuotesResponse(httpResponse: HttpResponse, symbol: String)
}

class QuoteActor(config: Config) extends Actor with Timers with Logging with Util {
    import akka.pattern.pipe
    import context.dispatcher

    implicit val materializer: ActorMaterializer = ActorMaterializer(ActorMaterializerSettings(context.system))

    val SERVER: String = config.getString("server")
    val connectionPoolSettings: ConnectionPoolSettings = getConnectionPoolSettings(config, context.system)

    val symbols: collection.mutable.Set[String] = collection.mutable.Set[String]()
    val http = Http(context.system)

    var debug = false
    var allQuotesRequestCount = 0
    timers.startPeriodicTimer(Tick, Tick, 4019.millis)

    override def receive: Receive = {
        case x: AddSymbol => symbols += x.symbol.toUpperCase
        case x: RemoveSymbol => symbols -= x.symbol.toUpperCase
        case Tick if symbols.nonEmpty =>
            val uri = Uri(SERVER + "quotes/").withQuery(Query("symbols" -> symbols.mkString(",")))
            http.singleRequest(HttpRequest(uri = uri), settings = connectionPoolSettings) pipeTo self
        case Tick =>  // do nothing
        case AllQuotes.Get(symbol) =>
            if (allQuotesRequestCount < 9) {
                val uri = Uri(SERVER + s"quotes/historicals/$symbol/?span=year&interval=day")
                http.singleRequest(HttpRequest(uri = uri), settings = connectionPoolSettings)
                        .map {
                            DailyQuotesResponse(_, symbol)
                        }
                        .pipeTo(self)
                allQuotesRequestCount += 1
            }
            else {
                self ! AllQuotes.Get(symbol)
            }
        case DailyQuotesResponse(HttpResponse(StatusCodes.OK, _, entity, _), symbol) =>
            allQuotesRequestCount -= 1
            entity.dataBytes.runFold(ByteString(""))(_ ++ _) foreach { body =>
                context.actorSelection(s"../${MainActor.NAME}/symbol-$symbol") !
                        AllQuotes.Here(getHistoricalQuotes(body.utf8String))
            }
        case DailyQuotesResponse(HttpResponse(statusCode, _, entity, _), symbol) =>
            allQuotesRequestCount -= 1
            entity.dataBytes.runFold(ByteString(""))(_ ++ _) foreach { body =>
                logger.error(s"Error in getting daily quotes for $symbol: $statusCode, body: ${body.utf8String}")
            }
        case HttpResponse(StatusCodes.OK, _, entity, _) =>
            entity.dataBytes.runFold(ByteString(""))(_ ++ _).foreach { body =>
                getQuotes(body.utf8String) foreach { quote =>
                    context.actorSelection(s"../${MainActor.NAME}/symbol-${quote.symbol}") ! quote
                }
            }
        /*
         * if the response entity is not needed, we can use
         * case response @ HttpResponse(statusCode, _, _, _) => response.discardEntityBytes()
         */
        case HttpResponse(statusCode, _, entity, _) =>
            entity.dataBytes.runFold(ByteString(""))(_ ++ _).foreach { body =>
                logger.error(s"Error in getting quotes: $statusCode, body: ${body.utf8String}")
            }
        case "DEBUG_ON" => debug = true
        case "DEBUG_OFF" => debug = false
        case x => logger.debug(s"Don't know what to do with $x yet")
    }
}
