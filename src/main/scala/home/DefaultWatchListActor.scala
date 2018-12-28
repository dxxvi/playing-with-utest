package home

import akka.actor.{Actor, Timers}
import com.typesafe.config.Config
import com.softwaremill.sttp._
import home.util.SttpBackends

import scala.concurrent.Future

object DefaultWatchListActor {
    import akka.actor.Props

    val NAME = "defaultWatchList"
    val AUTHORIZATION = "Authorization"

    sealed trait DefaultWatchListSealedTrait
    case object Tick extends DefaultWatchListSealedTrait
    case class ResponseWrapper(r: Response[List[String]]) extends DefaultWatchListSealedTrait

    def props(config: Config): Props = Props(new DefaultWatchListActor(config))
}

class DefaultWatchListActor(config: Config) extends Actor with Timers with SttpBackends {
    import DefaultWatchListActor._
    import concurrent.duration._
    import context.dispatcher
    import akka.event._
    import akka.pattern.pipe
    import akka.stream.scaladsl.Source
    import akka.util.ByteString

    implicit val logSource: LogSource[AnyRef] = new LogSource[AnyRef] {
        override def genString(t: AnyRef): String = NAME // this function return value is the akkaSource in the MDC
    }
    val log: LoggingAdapter = Logging(context.system, this)

    implicit val backend: SttpBackend[Future, Source[ByteString, Any]] = configureAkkaHttpBackend(config)

    val SERVER: String = config.getString("server")
    val authorization: String = if (config.hasPath(AUTHORIZATION)) config.getString(AUTHORIZATION) else "No-token"
    val defaultWatchListRequest: RequestT[Id, List[String], Nothing] = sttp
            .header("Authorization", authorization)
            .get(uri"$SERVER/watchlists/Default/")
            .response(asString.map(extractInstruments))

    timers.startPeriodicTimer(Tick, Tick, 60.seconds)

    val receive: Receive = {
        case Tick => defaultWatchListRequest.send().map(ResponseWrapper) pipeTo self
        case ResponseWrapper(Response(rawErrorBody, code, statusText, headers, history)) =>
            rawErrorBody.fold(
                _ => log.error("Error in getting default watch list: {} {}", code, statusText),
                instrumentLists => println(instrumentLists)
            )
    }

    private def extractInstruments(s: String): List[String] = {
        import org.json4s._
        import org.json4s.native.JsonMethods._

        (parse(s).asInstanceOf[JObject] \ "results").toOption
                .map(jv => jv.asInstanceOf[JArray].arr)
                .map(jvList => jvList
                            .map(jv => (jv \ "instrument").toOption)
                            .collect {
                                case Some(jv2) => jv2.asInstanceOf[JString].values
                            }
                )
                .getOrElse(List[String]())
    }

}
