package home

import akka.actor.Actor
import com.softwaremill.sttp._
import com.typesafe.config.Config
import home.util.{SttpBackends, TimersX}

object QuoteActor {
    import akka.actor.Props

    val NAME: String = "quote"

    case class Quote(symbol: String, lastTradePrice: Double, updatedAt: String)
    case class DailyQuote(
                                 beginsAt: String,
                                 openPrice: Double,
                                 closePrice: Double,
                                 highPrice: Double,
                                 lowPrice: Double,
                                 volume: Long
                         )

    sealed trait QuoteSealedTrait
    case object Tick extends QuoteSealedTrait
    case class LastTradePriceIntervalQuote(
                                              ltpJString: String,
                                              intervalQuoteJString1: String,
                                              intervalQuoteJString2: String
                                          ) extends QuoteSealedTrait
    case object Debug extends QuoteSealedTrait

    def props(config: Config): Props = Props(new QuoteActor(config))
}

class QuoteActor(config: Config) extends Actor with TimersX with SttpBackends {
    import QuoteActor._
    import context.dispatcher
    import scala.concurrent.Future
    import scala.concurrent.duration._
    import akka.event._
    import akka.pattern.pipe
    import home.util.Util

    implicit val logSource: LogSource[AnyRef] = (t: AnyRef) => NAME
    val log: LoggingAdapter = Logging(context.system, this)

    val SERVER: String = config.getString("server")
    val symbolsNeedDailyQuote: collection.mutable.Set[String] = collection.mutable.Set.empty[String]
    val symbolsNeedTodayQuote: collection.mutable.Set[String] = collection.mutable.Set.empty[String]

    timersx.startPeriodicTimer(Tick, Tick, 4019.millis)

    override def receive: Receive = {
        case Tick =>
            val lastTradePriceJStringFuture: Future[String] = Util.getLastTradePrices(config)
            val intervalQuoteJStringTupleFuture: Future[(String, String)] = Util.get5minQuotes(config)
            val lastTradePriceIntervalQuoteFuture = for {
                ltpJString <- lastTradePriceJStringFuture
                t          <- intervalQuoteJStringTupleFuture
            } yield LastTradePriceIntervalQuote(ltpJString, t._1, t._2)
            lastTradePriceIntervalQuoteFuture pipeTo self

        case LastTradePriceIntervalQuote(ltpJString, intervalQuoteJString1, intervalQuoteJString2) =>
            val symbol2LastTradePrice = Util.getLastTradePrices(ltpJString)
            val symbol2DailyQuoteList =
                Util.getIntervalQuotes(intervalQuoteJString1, intervalQuoteJString2)
            symbol2DailyQuoteList.foreach(t => {
                val stockActor = context.actorSelection(s"/user/${t._1}")
                stockActor ! StockActor.LastTradePrice5minQuoteList(
                    symbol2LastTradePrice.getOrElse(t._1, Double.NaN),
                    t._2
                )
            })

        case Debug =>
            val map = debug()
            sender() ! map
    }

    private def debug(): Map[String, String] = {
        var s = s"""
               |${QuoteActor.NAME} debug information:
               |  symbolsNeedDailyQuote: $symbolsNeedDailyQuote
               |  symbolsNeedTodayQuote: $symbolsNeedTodayQuote
             """.stripMargin
        log.info(s)
        Map(
            "symbolsNeedDailyQuote" -> symbolsNeedDailyQuote.mkString(","),
            "symbolsNeedTodayQuote" -> symbolsNeedTodayQuote.mkString(",")
        )
    }
}
