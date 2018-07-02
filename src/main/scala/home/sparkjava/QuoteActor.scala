package home.sparkjava

import scala.concurrent.duration._
import akka.actor.{Actor, ActorLogging, ActorRef, Props, Timers}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.Uri.Query
import akka.http.scaladsl.model._
import akka.http.scaladsl.settings.ConnectionPoolSettings
import akka.stream.{ActorMaterializer, ActorMaterializerSettings}
import akka.util.ByteString
import com.typesafe.config.Config
import com.typesafe.scalalogging.Logger

object QuoteActor {
    val NAME = "quoteActor"
    def props(config: Config): Props = Props(new QuoteActor(config))
}

class QuoteActor(config: Config) extends Actor with Timers with ActorLogging with Util {
    import akka.pattern.pipe
    import context.dispatcher

    val logger: Logger = Logger[QuoteActor]

    implicit val materializer: ActorMaterializer = ActorMaterializer(ActorMaterializerSettings(context.system))

    val SERVER: String = config.getString("server")
    val connectionPoolSettings: ConnectionPoolSettings = getConnectionPoolSettings(config, context.system)

    val symbols: collection.mutable.Set[String] = collection.mutable.Set[String]()
    val http = Http(context.system)

    var debug = false
    timers.startPeriodicTimer(Tick, Tick, 4019.millis)

    override def receive: Receive = {
        case x: AddSymbol => symbols += x.symbol.toUpperCase
        case x: RemoveSymbol => symbols -= x.symbol.toUpperCase
        case Tick if symbols.nonEmpty =>
            val uri = Uri(SERVER + "quotes/").withQuery(Query("symbols" -> symbols.mkString(",")))
            http.singleRequest(HttpRequest(uri = uri), settings = connectionPoolSettings) pipeTo self
        case Tick =>  // do nothing

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
                log.error(s"Error in getting quotes: $statusCode, body: ${body.utf8String}")
            }
        case "DEBUG_ON" => debug = true
        case "DEBUG_OFF" => debug = false
        case x => logger.debug(s"Don't know what to do with $x yet")
    }
}
