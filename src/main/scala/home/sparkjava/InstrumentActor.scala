package home.sparkjava

import akka.actor.{Actor, Props}
import akka.pattern.pipe
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.softwaremill.sttp._
import com.softwaremill.sttp.json4s._
import com.typesafe.config.{Config, ConfigFactory}
import home.sparkjava.message.{AddSymbol, Tick}

import scala.collection.concurrent.TrieMap
import scala.concurrent.Future

object InstrumentActor {
    val NAME = "instrumentActor"
    val instrument2NameSymbol: TrieMap[String, (String /* name */, String /* symbol */)] = TrieMap()

    case class Instrument(
             country: Option[String],
             state: Option[String],
             symbol: Option[String],
             tradeable: Option[Boolean]
     )
    case class InstrumentResponse(i: Response[Instrument], instrument: String)
    case class InstrumentList(instruments: List[String])

    def props(config: Config): Props = Props(new InstrumentActor(config))
}

class InstrumentActor(config: Config) extends Actor with Util {
    import InstrumentActor._
    import context.dispatcher

    val SERVER: String = config.getString("server")
    val authorization: String = if (config.hasPath("Authorization")) config.getString("Authorization") else "No token"
    implicit val httpBackend: SttpBackend[Future, Source[ByteString, Any]] = configureAkkaHttpBackend(config)

    val _receive: Receive = {
        case InstrumentList(instruments) =>
            instruments.foreach(instrument => {
                val symbolO = instrument2NameSymbol.get(instrument)
                if (symbolO.isDefined) {
                    Main.instrument2Symbol += ((instrument, symbolO.get._2))
                    context.actorSelection(s"../${MainActor.NAME}") ! AddSymbol(symbolO.get._2)
                }
                else sttp
                  .get(uri"$instrument")
                  .response(asJson[Instrument])
                  .send()
                  .map(r => InstrumentResponse(r, instrument)) pipeTo self
            })
            context.actorSelection(s"../${FundamentalActor.NAME}") ! Tick
        case InstrumentResponse(Response(rawErrorBody, code, statusText, _, _), instrument) =>
            rawErrorBody.fold(
                _ => logger.error(s"Error in accessing $instrument: $code $statusText"),
                i => {
                    if (i.symbol.nonEmpty && i.tradeable.contains(true) && i.state.contains("active")) {
                        logger.debug(s"We might need to add ${i.symbol.get} = $instrument to stock.conf")
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
