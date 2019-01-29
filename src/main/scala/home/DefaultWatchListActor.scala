package home

import akka.actor.Actor
import com.typesafe.config.Config
import com.softwaremill.sttp._
import home.util.{SttpBackends, TimersX}

import scala.util.{Failure, Success, Try}

object DefaultWatchListActor {
    import akka.actor.Props

    val NAME: String = "defaultWatchList"
    val AUTHORIZATION: String = "Authorization"

    private var _commaSeparatedSymbolString: String = ""

    def commaSeparatedSymbolString: String = _commaSeparatedSymbolString

    sealed trait DefaultWatchListSealedTrait
    case object Tick extends DefaultWatchListSealedTrait
    case object Debug extends DefaultWatchListSealedTrait

    def props(config: Config): Props = Props(new DefaultWatchListActor(config))
}

class DefaultWatchListActor(config: Config) extends Actor with TimersX with SttpBackends {
    import DefaultWatchListActor._
    import concurrent.duration._
    import akka.event._
    import home.model.Instrument
    import home.util.{StockDatabase, Util}

    implicit val logSource: LogSource[AnyRef] = (_: AnyRef) => NAME
    implicit val backend: SttpBackend[Id, Nothing] = configureCoreJavaHttpBackend(config)
    val log: LoggingAdapter = Logging(context.system, this)

    val SERVER: String = config.getString("server")
    val defaultWatchListRequest: Request[String, Nothing] = sttp
            .auth.bearer(Main.accessToken)
            .get(uri"$SERVER/watchlists/Default/")

    override def receive: Receive = {
        case Tick =>
            defaultWatchListRequest
                    .send()
                    .body match {
                        case Right(json) =>
                            val symbolList: List[String] = extractInstruments(json)
                                    .map(i => (i, StockDatabase.getInstrumentFromInstrument(i)))
                                    .collect(collectAndWriteLogMessagesKnownSymbols)
                                    .collect {
                                        case Some(symbol) => symbol
                                    }
                            log.debug("self is: {}", self)
                            symbolList.foreach(symbol => {
                                val actorRef = context.actorOf(StockActor.props(symbol), symbol)
                                log.debug("Create actor {}", actorRef)
                            })
                            _commaSeparatedSymbolString = symbolList.mkString(",")
                        case Left(s) =>
                            log.error("Error in getting default watch list: {}. Will try again in 4s.", s)
                            timersx.startSingleTimer(Tick, Tick, 4019.millis)
                    }

        case Debug =>
            val map = debug()
            sender() ! map
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
            Util.getSymbolFromInstrumentHttpURLConnection(i, config) match {
                case Success(symbol) => log.error("The default watch list has a bad stock {}", symbol)
                case Failure(ex) => log.error(ex, "Error for {}", i)
            }
            None
    }

    private def debug(): Map[String, String] = {
        val s = s"""
               |${DefaultWatchListActor.NAME} debug information:
               |  commaSeparatedSymbolString: $commaSeparatedSymbolString
             """.stripMargin
        log.info(s)
        Map("commaSeparatedSymbolString" -> commaSeparatedSymbolString)
    }
}
