package home

import akka.actor.{Actor, Timers}
import com.softwaremill.sttp._
import com.typesafe.config.Config
import home.util.SttpBackends

object QuoteActor {
    import akka.actor.Props

    val NAME = "quote"

    sealed trait QuoteSealedTrait
    case object Tick extends QuoteSealedTrait
    case class ResponseWrapper(r: Response[List[(String, Double)]]) extends QuoteSealedTrait

    def props(config: Config): Props = Props(new QuoteActor(config))
}

class QuoteActor(config: Config) extends Actor with Timers with SttpBackends {
    import QuoteActor._
    import context.dispatcher
    import scala.concurrent.Future
    import scala.concurrent.duration._
    import akka.event._
    import akka.pattern.pipe
    import akka.stream.scaladsl.Source
    import akka.util.ByteString

    implicit val logSource: LogSource[AnyRef] = new LogSource[AnyRef] {
        override def genString(t: AnyRef): String = NAME
    }
    val log: LoggingAdapter = Logging(context.system, this)

    val SERVER: String = config.getString("server")
    implicit val backend: SttpBackend[Future, Source[ByteString, Any]] = configureAkkaHttpBackend(config)
    val quotesRequest: RequestT[Id, List[(String, Double)], Nothing] = sttp
            .get(uri"$SERVER/quotes/?symbols=${DefaultWatchListActor.commaSeparatedSymbolString}")
            .response(asString.map(extractLastTradePriceAndSymbol))

    timers.startPeriodicTimer(Tick, Tick, 4019.millis)

    override def receive: Receive = {
        case Tick if DefaultWatchListActor.commaSeparatedSymbolString.nonEmpty =>
            quotesRequest.send().map(ResponseWrapper) pipeTo self
        case Tick if DefaultWatchListActor.commaSeparatedSymbolString.isEmpty =>
            log.info("Do nothing because the DefaultWatchListActor is not done yet.")
        case ResponseWrapper(Response(rawErrorBody, code, statusText, _, _)) => rawErrorBody.fold(
            _ => log.error("Error in getting quotes: {} {}", code, statusText),
            symbolQuoteList => symbolQuoteList.foreach(tuple => {
                val stockActor = context.actorSelection(s"../${DefaultWatchListActor.NAME}/${tuple._1}")
                stockActor ! StockActor.Quote(tuple._2)
            })
        )

    }

    private def extractLastTradePriceAndSymbol(jString: String): List[(String, Double)] = ???
}
