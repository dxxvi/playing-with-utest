package home

import akka.actor.{Actor, Timers}
import com.typesafe.config.Config
import com.softwaremill.sttp._
import home.util.SttpBackends

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

object DefaultWatchListActor {
    import akka.actor.Props

    val NAME: String = "defaultWatchList"
    val AUTHORIZATION: String = "Authorization"

    private var _commaSeparatedSymbolString: String = ""

    def commaSeparatedSymbolString: String = _commaSeparatedSymbolString

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
    import home.model.Instrument
    import home.util.StockDatabase
    import home.util.Util

    implicit val logSource: LogSource[AnyRef] = (t: AnyRef) => NAME
    val log: LoggingAdapter = Logging(context.system, this)

    val SERVER: String = config.getString("server")
    val authorization: String = if (config.hasPath(AUTHORIZATION)) config.getString(AUTHORIZATION) else "No-token"
    val defaultWatchListRequest: RequestT[Id, List[String], Nothing] = sttp
            .header(AUTHORIZATION, authorization)
            .get(uri"$SERVER/watchlists/Default/")
            .response(asString.map(extractInstruments))

    override def receive: Receive = {
        case Tick =>
            implicit val backend: SttpBackend[Future, Source[ByteString, Any]] = configureAkkaHttpBackend(config)
            defaultWatchListRequest.send().map(ResponseWrapper) pipeTo self
        case ResponseWrapper(Response(rawErrorBody, code, statusText, _, _)) =>
            rawErrorBody.fold(
                _ => {
                    log.error("Error in getting default watch list: {} {}. Will try again in 4s.", code, statusText)
                    // timers.startSingleTimer(Tick, Tick, 4019.millis)
                },
                instrumentLists => {
                    val symbolList: List[String] = instrumentLists
                            .map(i => (i, StockDatabase.getInstrumentFromInstrument(i)))
                            .collect(collectAndWriteLogMessagesKnownSymbols)
                            .collect {
                                case Some(symbol) => symbol
                            }
                    symbolList.foreach(symbol => context.actorOf(StockActor.props(symbol), symbol))
                    _commaSeparatedSymbolString = symbolList.mkString(",")
                }
            )
    }

    private def extractInstruments(s: String): List[String] = {
        import org.json4s._
        import org.json4s.native.JsonMethods._

        Try(
            (parse(s).asInstanceOf[JObject] \ "results").toOption
                .map(jv => jv.asInstanceOf[JArray].arr)
                .map(jvList => jvList
                            .map(jv => (jv \ "instrument").toOption)
                            .collect {
                                case Some(jv2) => jv2.asInstanceOf[JString].values
                            }
                )
                .getOrElse(List[String]())
        ) match {
            case x: Success[List[String]] => x.get
            case Failure(ex) =>
                log.error(ex, "Check this default watch list response: {}", s)
                List[String]()
        }
    }

    private val collectAndWriteLogMessagesKnownSymbols: PartialFunction[(String, Option[Instrument]), Option[String]] = {
        case Tuple2(_, Some(Instrument(symbol, _, _, _, _))) => Some(symbol)
        case Tuple2(i, None) =>
            Try(Util.getSymbolFromInstrumentHttpURLConnection(i, config)) match {
                case Success(symbol) =>
                    log.error("The default watch list has a bad stock {}", symbol)
                case Failure(ex) => log.error(ex, "Error for {}", i)
            }
            None
    }
}
