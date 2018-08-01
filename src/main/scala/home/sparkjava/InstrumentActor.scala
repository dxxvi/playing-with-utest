package home.sparkjava

import akka.actor.{Actor, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse, StatusCodes, Uri}
import akka.http.scaladsl.settings.ConnectionPoolSettings
import akka.stream.{ActorMaterializer, ActorMaterializerSettings}
import akka.util.ByteString
import com.typesafe.config.Config
import org.apache.logging.log4j.ThreadContext
import org.apache.logging.log4j.scala.Logging

object InstrumentActor {
    val NAME = "instrumentActor"
    def props(config: Config): Props = Props(new InstrumentActor(config))
}

class InstrumentActor(config: Config) extends Actor with Logging with Util {
    import akka.pattern.pipe
    import context.dispatcher
    import spray.json._

    implicit val materializer: ActorMaterializer = ActorMaterializer(ActorMaterializerSettings(context.system))
    val connectionPoolSettings: ConnectionPoolSettings = getConnectionPoolSettings(config, context.system)
    val http = Http(context.system)

    var debug = false

    val _receive: Receive = {
        case instrument: String if !Main.instrument2Symbol.contains(instrument) =>
            if (Main.requestCount.get < 19) {
                Main.requestCount.incrementAndGet()
                http.singleRequest(HttpRequest(uri = Uri(instrument)), settings = connectionPoolSettings) pipeTo self
            }
            else self ! instrument
        case _: String => // ignored because Main.instrument2Symbol contains instrument
        case HttpResponse(StatusCodes.OK, _, entity, _) =>
            Main.requestCount.decrementAndGet()
            entity.dataBytes.runFold(ByteString(""))(_ ++ _).foreach { body =>
                val fields = body.utf8String.parseJson.asJsObject.fields
                val ot: Option[(String, String)] = for {
                    instrument <- fields.get("url").collect { case x: JsString => x.value }
                    symbol <- fields.get("symbol").collect { case x: JsString => x.value }
                } yield (instrument, symbol)
                ot foreach { t =>
                    Main.instrument2Symbol += t
                    context.actorSelection(s"../${MainActor.NAME}") ! AddSymbol(t._2)
                }
            }
        case HttpResponse(statusCode, _, entity, _) =>
            logger.debug("got bad response")
            Main.requestCount.decrementAndGet()
            entity.dataBytes.runFold(ByteString(""))(_ ++ _).foreach { body =>
                logger.error(s"Error in getting instrument: $statusCode, body: ${body.utf8String}")
            }
        case "DEBUG_ON" => debug = true
        case "DEBUG_OFF" => debug = false
        case x => logger.debug(s"Don't know what to do with $x yet")
    }

    val sideEffect: PartialFunction[Any, Any] = {
        case x =>
            ThreadContext.clearMap()
            x
    }

    override def receive: Receive = sideEffect andThen _receive
}
