package home.sparkjava

import akka.actor.{Actor, Props}
import akka.pattern.pipe
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.softwaremill.sttp._
import com.softwaremill.sttp.json4s._
import com.typesafe.config.Config
import home.sparkjava.message.AddSymbol
import org.apache.logging.log4j.ThreadContext

import scala.concurrent.Future

object InstrumentActor {
    val NAME = "instrumentActor"

    case class Instrument(
             country: Option[String],
             state: Option[String],
             symbol: Option[String],
             tradeable: Option[Boolean]
     )
    case class InstrumentResponse(i: Response[Instrument], instrument: String)

    def props(config: Config): Props = Props(new InstrumentActor(config))
}

class InstrumentActor(config: Config) extends Actor with Util {
    import InstrumentActor._
    import context.dispatcher

    val SERVER: String = config.getString("server")
    val authorization: String = if (config.hasPath("Authorization")) config.getString("Authorization") else "No token"
    implicit val httpBackend: SttpBackend[Future, Source[ByteString, Any]] = configureAkkaHttpBackend(config)

    val _receive: Receive = {
        case instrument: String => sttp
                .get(uri"$instrument")
                .response(asJson[Instrument])
                .send()
                .map(r => InstrumentResponse(r, instrument)) pipeTo self
        case InstrumentResponse(Response(rawErrorBody, code, statusText, _, _), instrument) =>
            logger.debug(s"Got InstrumentResponse $code $statusText")
            rawErrorBody.fold(
                a => logger.error(s"Error in accessing $instrument: $code $statusText ${a.mkString}"),
                i => {
                    if (i.symbol.nonEmpty && i.tradeable.contains(true) && i.state.contains("active")) {
                        Main.instrument2Symbol += ((instrument, i.symbol.get))
                        context.actorSelection(s"../${MainActor.NAME}") ! AddSymbol(i.symbol.get)
                    }
                    else
                        logger.warn(s"$instrument returns $i")
                }
            )
    }

    override def receive: Receive = Main.clearThreadContextMap andThen _receive
}
