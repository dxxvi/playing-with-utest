package home.sparkjava

import akka.actor.{Actor, Props}
import akka.pattern.pipe
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.softwaremill.sttp._
import com.softwaremill.sttp.json4s._
import com.typesafe.config.{Config, ConfigFactory, ConfigValue}
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

    val instrument2Symbol: collection.mutable.Map[String, String] = collection.mutable.Map[String, String]()
    readInstrument2Symbol()
    val SERVER: String = config.getString("server")
    val authorization: String = if (config.hasPath("Authorization")) config.getString("Authorization") else "No token"
    implicit val httpBackend: SttpBackend[Future, Source[ByteString, Any]] = configureAkkaHttpBackend(config)

    val _receive: Receive = {
        case instrument: String =>
            val symbolO = instrument2Symbol.get(instrument)
            if (symbolO.isDefined) {
                Main.instrument2Symbol += ((instrument, symbolO.get))
                context.actorSelection(s"../${MainActor.NAME}") ! AddSymbol(symbolO.get)
            }
            else if (Main.requestCount.get < 19) {
                sttp
                        .get(uri"$instrument")
                        .response(asJson[Instrument])
                        .send()
                        .map(r => InstrumentResponse(r, instrument)) pipeTo self
                Main.requestCount.incrementAndGet()
            }
            else logger.debug(s"Not gonna find the symbol for $instrument now, the request count too high ${Main.requestCount.get}")
        case InstrumentResponse(Response(rawErrorBody, code, statusText, _, _), instrument) =>
            Main.requestCount.decrementAndGet()
            rawErrorBody.fold(
                a => logger.error(s"Error in accessing $instrument: $code $statusText"),
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

    private def readInstrument2Symbol() {
        import collection.JavaConverters._
        val f: java.util.Map.Entry[String, ConfigValue] => (String, String) =
            e => (e.getValue.unwrapped().asInstanceOf[String], e.getKey)
        instrument2Symbol ++= config.getConfig("dow").entrySet().asScala.map(f)

        instrument2Symbol ++= ConfigFactory.load("stock.conf").getConfig("soi").entrySet().asScala.map(f)
    }
}
