package home.sparkjava

import scala.concurrent.duration._
import akka.actor.{Actor, ActorLogging, Props, Timers}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.model.{HttpRequest, HttpResponse, StatusCodes, Uri}
import akka.http.scaladsl.settings.ConnectionPoolSettings
import akka.stream.{ActorMaterializer, ActorMaterializerSettings}
import akka.util.ByteString
import com.typesafe.config.Config
import com.typesafe.scalalogging.Logger

import scala.util.Failure

object DefaultWatchListActor {
    val NAME = "defaultWatchListActor"
    def props(config: Config): Props = Props(new DefaultWatchListActor(config))
}

class DefaultWatchListActor(config: Config) extends Actor with Timers with ActorLogging with Util {
    import spray.json._
    import akka.pattern.pipe
    import context.dispatcher

    val logger: Logger = Logger[DefaultWatchListActor]

    implicit val materializer: ActorMaterializer = ActorMaterializer(ActorMaterializerSettings(context.system))

    val SERVER: String = config.getString("server")
    val authorization: String = if (config.hasPath("Authorization")) config.getString("Authorization") else "No token"
    val connectionPoolSettings: ConnectionPoolSettings = getConnectionPoolSettings(config, context.system)

    val symbols: collection.mutable.Set[String] = collection.mutable.Set[String]()
    val http = Http(context.system)

    timers.startPeriodicTimer(Tick, Tick, 19482.millis)

    override def receive: Receive = {
        case Tick =>
            val httpRequest = HttpRequest(uri = Uri(SERVER + "watchlists/Default/"))
                    .withHeaders(RawHeader("Authorization", authorization))
            http.singleRequest(httpRequest, settings = connectionPoolSettings).pipeTo(self)
        case HttpResponse(StatusCodes.OK, _, entity, _) =>
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
                log.error(s"Error in getting default watchlist: $statusCode, body: ${body.utf8String}")
            }
        case x => logger.debug(s"Don't know what to do with $x yet")
    }
}
