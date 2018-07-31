package home.sparkjava

import scala.concurrent.duration._
import akka.actor.{Actor, Props, Timers}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.model.{ContentType, HttpEntity, HttpMethods, HttpRequest, HttpResponse, MediaTypes, RequestEntity, StatusCodes, Uri}
import akka.http.scaladsl.settings.ConnectionPoolSettings
import akka.stream.{ActorMaterializer, ActorMaterializerSettings}
import akka.util.ByteString
import com.typesafe.config.Config
import org.apache.logging.log4j.scala.Logging

import scala.util.{Failure, Success}

object DefaultWatchListActor {
    val NAME = "defaultWatchListActor"
    def props(config: Config): Props = Props(new DefaultWatchListActor(config))
}

class DefaultWatchListActor(config: Config) extends Actor with Timers with Logging with Util {
    import spray.json._
    import akka.pattern.pipe
    import context.dispatcher

    implicit val materializer: ActorMaterializer = ActorMaterializer(ActorMaterializerSettings(context.system))

    val SERVER: String = config.getString("server")
    val authorization: String = if (config.hasPath("Authorization")) config.getString("Authorization") else "No token"
    val connectionPoolSettings: ConnectionPoolSettings = getConnectionPoolSettings(config, context.system)

    val http = Http(context.system)
    var debug = false

    timers.startSingleTimer(Tick, Tick, 419.millis)
    timers.startPeriodicTimer(Tick, Tick, 19482.millis)

    override def receive: Receive = {
        case Tick =>
            logger.debug("Getting default watch list")
            val httpRequest = HttpRequest(uri = Uri(SERVER + "watchlists/Default/"))
                    .withHeaders(RawHeader("Authorization", authorization))
            http.singleRequest(httpRequest, settings = connectionPoolSettings) pipeTo self
        case HttpResponse(StatusCodes.OK, _, entity, _) =>
            logger.debug("Got default watch list")
            entity.dataBytes.runFold(ByteString(""))(_ ++ _).foreach { body =>
                body.utf8String.parseJson.asJsObject.fields.get("results") match {
                    case Some(jsValue) => jsValue match {
                        case x: JsArray =>
                            x.elements
                                    .map(_.asJsObject.fields.get("instrument"))
                                    .collect {
                                        case x: Some[JsValue] => x.get match {
                                            case y: JsString =>
                                                if (!Main.instrument2Symbol.contains(y.value))
                                                    context.actorSelection(s"../${InstrumentActor.NAME}") ! y.value
                                            case _ => logger.error(s"Field instrument is not a string in ${body.utf8String}")
                                        }
                                    }
                        case _ => throw new RuntimeException(s"Fields results is not an array in ${body.utf8String}")
                    }
                    case _ => throw new RuntimeException(s"No results field in the json ${body.utf8String}")
                }
            }
        case HttpResponse(statusCode, _, entity, _) =>
            entity.dataBytes.runFold(ByteString(""))(_ ++ _).foreach { body =>
                logger.error(s"Error in getting default watchlist: $statusCode, body: ${body.utf8String}")
            }
        case x @ AddSymbol(symbol) =>
            if (debug) logger.debug(s"Received $x")
            val entity: RequestEntity = HttpEntity(ContentType(MediaTypes.`application/json`),
                JsObject("symbols" -> JsString(symbol)).compactPrint.getBytes)
            val httpRequest = HttpRequest(
                HttpMethods.POST,
                Uri(SERVER + "watchlists/Default/bulk_add/"),
                entity = entity
            ).withHeaders(RawHeader("Authorization", authorization))
            http.singleRequest(httpRequest, settings = connectionPoolSettings).onComplete {
                case Success(HttpResponse(statusCode, _, ent, _)) =>
                    ent.dataBytes.runFold(ByteString(""))(_ ++ _).foreach { body =>
                        if (debug) logger.debug(s"Add $symbol to the Default watch list: $statusCode - ${body.utf8String}")
                    }
                case Failure(exception) =>
                    logger.error(s"Unable to add $symbol to the Default watch list: ${exception.getMessage}")
            }
        case "DEBUG_ON" => debug = true
        case "DEBUG_OFF" => debug = false
        case x => logger.debug(s"Don't know what to do with $x yet")
    }
}
