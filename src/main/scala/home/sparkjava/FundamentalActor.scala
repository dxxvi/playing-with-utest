package home.sparkjava

import concurrent.duration._
import akka.actor.{Actor, ActorLogging, Props, Timers}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse, StatusCodes, Uri}
import akka.http.scaladsl.settings.ConnectionPoolSettings
import akka.stream.{ActorMaterializer, ActorMaterializerSettings}
import akka.util.ByteString
import com.typesafe.config.Config
import com.typesafe.scalalogging.Logger

object FundamentalActor {
    val NAME = "fundamentalActor"
    def props(config: Config): Props = Props(new FundamentalActor(config))
}

class FundamentalActor(config: Config) extends Actor with Timers with ActorLogging with Util {
    import spray.json._
    import model.FundamentalProtocol._
    import akka.pattern.pipe
    import context.dispatcher

    implicit val materializer: ActorMaterializer = ActorMaterializer(ActorMaterializerSettings(context.system))

    val logger: Logger = Logger[FundamentalActor]

    val SERVER: String = config.getString("server")
    val connectionPoolSettings: ConnectionPoolSettings = getConnectionPoolSettings(config, context.system)
    val http = Http(context.system)

    timers.startPeriodicTimer(Tick, Tick, 19824.millis)

    override def receive: Receive = {
        case Tick if Main.instrument2Symbol.nonEmpty =>
            Main.instrument2Symbol.values.grouped(10).foreach { symbols =>
                val uri = Uri(SERVER + s"fundamentals/?symbols=${symbols.mkString(",")}")
                http.singleRequest(HttpRequest(uri = uri), settings = connectionPoolSettings).pipeTo(self)
            }
        case Tick =>  // do nothing
        case HttpResponse(StatusCodes.OK, _, entity, _) =>
            entity.dataBytes.runFold(ByteString(""))(_ ++ _).foreach { body =>
                getFundamentals(body.utf8String).foreach { fundamental =>
                    Main.instrument2Symbol.get(fundamental.instrument).foreach { symbol =>
                        context.actorSelection(s"../${MainActor.NAME}/symbol-$symbol") ! fundamental
                    }
                }
            }
        case HttpResponse(statusCode, _, entity, _) =>
            entity.dataBytes.runFold(ByteString(""))(_ ++ _).foreach { body =>
                log.error(s"Error in getting fundamentals: $statusCode, body: ${body.utf8String}")
            }
        case x => logger.debug(s"Don't know what to do with $x yet")
    }
}
