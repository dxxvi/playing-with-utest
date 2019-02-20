package home.sparkjava

import akka.actor.{Actor, Props, Timers}
import akka.event.{LogSource, Logging, LoggingAdapter}
import akka.pattern.pipe
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.softwaremill.sttp._
import com.typesafe.config.Config
import home.sparkjava.message.Tick
import home.sparkjava.model.{DailyQuote, Quote}

import scala.concurrent.Future

object QuoteActor {
    val NAME = "quoteActor"

    case class CurrentAndTodayQuote(quoteString: String, todayQuoteString1: String, todayQuoteString2: String)
    def props(config: Config): Props = Props(new QuoteActor(config))
}

class QuoteActor(config: Config) extends Actor with Timers with Util {
    import QuoteActor._
    import context.dispatcher

    val SERVER: String = config.getString("server")
    implicit val logSource: LogSource[AnyRef] = (_: AnyRef) => NAME
    val log: LoggingAdapter = Logging(context.system, this)
    implicit val httpBackend: SttpBackend[Future, Source[ByteString, Any]] = configureAkkaHttpBackend(config)

//    timers.startPeriodicTimer(Tick, Tick, Main.calculateShortDuration())

    override def receive: Receive = {
        case Tick =>
            val quoteResponse = getLastTradePrices(config)
            val todayQuoteResponse = get5minQuotes(config)
            val currentAndTodayQuoteFuture: Future[CurrentAndTodayQuote] = for {
                quoteString <- quoteResponse
                t <- todayQuoteResponse
            } yield CurrentAndTodayQuote(quoteString, t._1, t._2)
            currentAndTodayQuoteFuture pipeTo self

        case CurrentAndTodayQuote(quoteString, todayQuoteString1, todayQuoteString2) =>
            val symbol2Quote: Map[String, Quote] = Quote.deserialize(quoteString).groupBy(_.symbol).mapValues(_.head)
            val symbol25minQuoteList: Map[String, List[DailyQuote]] =
                DailyQuote.deserialize(todayQuoteString1) ++ DailyQuote.deserialize(todayQuoteString2)
            val symbol2OpenHighLow = calculateOpenHighLow(symbol25minQuoteList)

            import StockActor.QuoteOpenHighLow
            for ((symbol, quote) <- symbol2Quote) symbol2OpenHighLow get symbol match {
                case Some((open, high, low)) =>
                    context.actorSelection(s"/user/symbol-$symbol") ! QuoteOpenHighLow(quote, open, high, low)
                case None =>
                    val stockActor = context.actorSelection(s"/user/symbol-$symbol")
                    stockActor ! QuoteOpenHighLow(quote, Double.NaN, Double.NaN, Double.NaN)
            }
    }
}
