package home.sparkjava

import akka.actor.{Actor, Props, Timers}
import akka.pattern.pipe
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.typesafe.config.Config
import com.softwaremill.sttp._
import message.Tick

import scala.concurrent.Future
import scala.concurrent.duration._

object DefaultWatchListActor {
    val NAME = "defaultWatchListActor"

    case class InstrumentResponse(r: Response[List[String]])

    def props(config: Config): Props = Props(new DefaultWatchListActor(config))
}

class DefaultWatchListActor(config: Config) extends Actor with Timers with Util {
    import DefaultWatchListActor._
    import context.dispatcher

    val SERVER: String = config.getString("server")
    val authorization: String = if (config.hasPath("Authorization")) config.getString("Authorization") else "No token"
    implicit val httpBackend: SttpBackend[Future, Source[ByteString, Any]] = configureAkkaHttpBackend(config)

    timers.startPeriodicTimer(Tick, Tick, 30.seconds)

    val _receive: Receive = {
        case Tick => sttp.header("Authorization", authorization)
                .get(uri"${SERVER}watchlists/Default")
                .response(asString.map(extractInstruments))
                .send()
                .map(InstrumentResponse) pipeTo self
            Main.requestCount.incrementAndGet()
        case InstrumentResponse(Response(rawErrorBody, code, statusText, _, _)) =>
            Main.requestCount.decrementAndGet()
            logger.debug(s"Got InstrumentResponse: $code $statusText; Request count: ${Main.requestCount.get}")
            rawErrorBody.fold(
                a => logger.error(s"Error in getting default watch list: $code $statusText ${a.mkString}"),
                a => a foreach { instrument =>
                    if (!Main.instrument2Symbol.contains(instrument))
                        context.actorSelection(s"../${InstrumentActor.NAME}") ! instrument
                }
            )
        case x => logger.error(s"Don't know what to do with $x")
    }

    override def receive: Receive = Main.clearThreadContextMap andThen _receive

    /**
      * @param s looks like default-watch-list.json
      */
    private def extractInstruments(s: String): List[String] = {
        import org.json4s._
        import org.json4s.native.JsonMethods._
        parse(s).asInstanceOf[JObject].values.get("results").fold({
            logger.error(s"No field 'results' in $s")
            List[String]()
        }) {
            case results: List[Map[String, _]] =>
                results.map(m => m.get("instrument")).collect { case Some(x) => x.asInstanceOf[String] }
            case x =>
                logger.error(s"Unexpected field 'results' type $x")
                List[String]()
        }
    }
}
