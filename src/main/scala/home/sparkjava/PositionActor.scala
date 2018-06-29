package home.sparkjava

import scala.concurrent.duration._
import akka.actor.{Actor, ActorLogging, ActorRef, Props, Timers}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.model.{HttpRequest, HttpResponse, StatusCodes, Uri}
import akka.http.scaladsl.settings.ConnectionPoolSettings
import akka.stream.{ActorMaterializer, ActorMaterializerSettings}
import akka.util.ByteString
import com.typesafe.config.Config
import com.typesafe.scalalogging.Logger
import home.sparkjava.model.Position

object PositionActor {
    val NAME = "positionActor"
    def props(config: Config): Props = Props(new PositionActor(config))
}

class PositionActor(config: Config) extends Actor with Timers with ActorLogging with Util {
    import spray.json._
    import model.PositionProtocol._
    import akka.pattern.pipe
    import context.dispatcher

    implicit val materializer: ActorMaterializer = ActorMaterializer(ActorMaterializerSettings(context.system))

    val logger: Logger = Logger[PositionActor]

    val SERVER: String = config.getString("server")
    val authorization: String = if (config.hasPath("Authorization")) config.getString("Authorization") else "No token"
    val accountNumber: String = if (config.hasPath("AccountNumber")) config.getString("AccountNumber") else "NO_ACCOUNT"
    val connectionPoolSettings: ConnectionPoolSettings = getConnectionPoolSettings(config, context.system)
    val http = Http(context.system)

    timers.startPeriodicTimer(Tick, Tick, 19824.millis)

    override def receive: Receive = {
        case Tick if Main.instrument2Symbol.nonEmpty =>
            val httpRequest = HttpRequest(uri = Uri(SERVER + s"accounts/$accountNumber/positions/"))
                    .withHeaders(RawHeader("Authorization", authorization))
            http.singleRequest(httpRequest, settings = connectionPoolSettings).pipeTo(self)
        case Tick =>  // do nothing
        case HttpResponse(StatusCodes.OK, _, entity, _) =>
            entity.dataBytes.runFold(ByteString(""))(_ ++ _).foreach { body =>
                getPositions(body.utf8String).foreach { position =>
                    Main.instrument2Symbol.get(position.instrument).foreach { symbol =>
                        context.actorSelection(s"../${WebSocketActor.NAME}") ! s"$symbol: POSITION: ${position.toJson.compactPrint}"
                        context.actorSelection(s"../${MainActor.NAME}/symbol-$symbol") ! position
                    }
                }
            }
        case HttpResponse(statusCode, _, entity, _) =>
            entity.dataBytes.runFold(ByteString(""))(_ ++ _).foreach { body =>
                log.error(s"Error in getting positions: $statusCode, body: ${body.utf8String}")
            }
        case x => logger.debug(s"Don't know what to do with $x yet")
    }
}
