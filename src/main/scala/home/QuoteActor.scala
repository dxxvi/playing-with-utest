package home

import akka.actor.{Actor, Timers}
import com.softwaremill.sttp._
import com.typesafe.config.Config
import home.util.SttpBackends

object QuoteActor {
    import akka.actor.Props

    val NAME: String = "quote"

    case class Quote(symbol: String, lastTradePrice: Double, updatedAt: String)
    case class DailyQuote(
                                 beginsAt: String,
                                 openPrice: Double,
                                 closePrice: Double,
                                 highPrice: Double,
                                 lowPrice: Double
                         )

    sealed trait QuoteSealedTrait
    case object Tick extends QuoteSealedTrait
    case class ResponseWrapper(r: Response[List[Quote]]) extends QuoteSealedTrait
    case class DailyQuoteRequest(symbol: String) extends QuoteSealedTrait

    def props(config: Config): Props = Props(new QuoteActor(config))
}

class QuoteActor(config: Config) extends Actor with Timers with SttpBackends {
    import QuoteActor._
    import context.dispatcher
    import scala.concurrent.Future
    import scala.concurrent.duration._
    import scala.util.Failure
    import scala.util.Success
    import scala.util.Try
    import akka.event._
    import akka.pattern.pipe
    import akka.stream.scaladsl.Source
    import akka.util.ByteString
    import org.json4s._
    import org.json4s.native.JsonMethods._
    import home.util.Util

    implicit val logSource: LogSource[AnyRef] = (t: AnyRef) => NAME
    val log: LoggingAdapter = Logging(context.system, this)

    val SERVER: String = config.getString("server")
    val symbolsNeedDailyQuote: collection.mutable.Set[String] = collection.mutable.Set.empty[String]

    // timers.startPeriodicTimer(Tick, Tick, 94019.millis)

    override def receive: Receive = {
        case Tick if DefaultWatchListActor.commaSeparatedSymbolString.nonEmpty =>
            fetchAllQuotes() pipeTo self
            fetchDailyQuotes()

        case Tick if DefaultWatchListActor.commaSeparatedSymbolString.isEmpty =>
            log.info("Skip because the DefaultWatchListActor is not done yet.")

        case ResponseWrapper(Response(rawErrorBody, code, statusText, _, _)) => rawErrorBody.fold(
            _ => log.error("Error in getting quotes: {} {}", code, statusText),
            quoteList => quoteList.foreach(q => {
                val stockActor =
                    context.actorSelection(s"../${DefaultWatchListActor.NAME}/${q.symbol}")
                stockActor ! StockActor.Quote(q.lastTradePrice, q.updatedAt)
            })
        )

        case DailyQuoteRequest(symbol) => symbolsNeedDailyQuote += symbol
    }

    private def fetchAllQuotes(): Future[ResponseWrapper] = {
        implicit val backend: SttpBackend[Future, Source[ByteString, Any]] = configureAkkaHttpBackend(config)
        sttp
                .get(uri"$SERVER/quotes/?symbols=${DefaultWatchListActor.commaSeparatedSymbolString}")
                .response(asString.map(extractLastTradePriceAndSymbol))
                .send()
                .map(ResponseWrapper)
    }

    private def fetchDailyQuotes() {
        implicit val backend: SttpBackend[Id, Nothing] = configureCoreJavaHttpBackend(config)
        if (symbolsNeedDailyQuote.nonEmpty)
            symbolsNeedDailyQuote.grouped(75).foreach(symbols => {
                Try(Util.getDailyQuoteHttpURLConnection(symbols, config)) match {
                    case Success(list) => list.foreach(tuple => {
                        val stockActor =
                            context.actorSelection(s"../${DefaultWatchListActor.NAME}/${tuple._1}")
                        stockActor ! StockActor.DailyQuoteListWrapper(tuple._2)
                    })
                    case Failure(ex) => log.error("Error in getting daily quotes", ex)
                }
            })
    }

    /**
      * @param js is like this
      *           {
      *             "results": [
      *                {
      *                  "adjusted_previous_close": "17.820000",
      *                  "ask_price": "17.970000",
      *                  "ask_size": 3800,
      *                  "bid_price": "17.960000",
      *                  "bid_size": 5400,
      *                  "has_traded": true,
      *                  "instrument": "https://api.robinhood.com/instruments/940fc3f5-1db5-4fed-b452-f3a2e4562b5f/",
      *                  "last_extended_hours_trade_price": null,
      *                  "last_trade_price": "18.010000",
      *                  "last_trade_price_source": "nls",
      *                  "previous_close": "17.820000",
      *                  "previous_close_date": "2018-12-28",
      *                  "symbol": "AMD",
      *                  "trading_halted": false,
      *                  "updated_at": "2018-12-31T16:09:15Z"
      *                },
      *                {
      *                  "adjusted_previous_close": "16.330000",
      *                  "ask_price": "16.300000",
      *                  "ask_size": 600,
      *                  "bid_price": "16.290000",
      *                  "bid_size": 1000,
      *                  "has_traded": true,
      *                  "instrument": "https://api.robinhood.com/instruments/dad8fa2c-1e8d-4cb9-b354-1f0b91a4193e/",
      *                  "last_extended_hours_trade_price": null,
      *                  "last_trade_price": "16.220000",
      *                  "last_trade_price_source": "nls",
      *                  "previous_close": "16.330000",
      *                  "previous_close_date": "2018-12-28",
      *                  "symbol": "ON",
      *                  "trading_halted": false,
      *                  "updated_at": "2018-12-31T16:09:15Z"
      *                },
      *                {
      *                  "adjusted_previous_close": "333.870000",
      *                  "ask_price": "328.110000",
      *                  "ask_size": 200,
      *                  "bid_price": "327.850000",
      *                  "bid_size": 200,
      *                  "has_traded": true,
      *                  "instrument": "https://api.robinhood.com/instruments/e39ed23a-7bd1-4587-b060-71988d9ef483/",
      *                  "last_extended_hours_trade_price": null,
      *                  "last_trade_price": "328.730000",
      *                  "last_trade_price_source": "nls",
      *                  "previous_close": "333.870000",
      *                  "previous_close_date": "2018-12-28",
      *                  "symbol": "TSLA",
      *                  "trading_halted": false,
      *                  "updated_at": "2018-12-31T16:09:16Z"
      *                }
      *             ]
      *           }
      * @return
      */
    private def extractLastTradePriceAndSymbol(js: String): List[Quote] = {
        val optionList: List[Option[Quote]] = (parse(js) \ "results").asInstanceOf[JArray].arr
                .map(jv => for {
                    symbol <- Util.fromJValueToOption[String](jv \ "symbol")
                    lastTradePrice <- Util.fromJValueToOption[Double](jv \ "last_trade_price")
                    updatedAt <- Util.fromJValueToOption[String](jv \ "updated_at")
                } yield Quote(symbol, lastTradePrice, updatedAt))
        optionList.collect {
            case Some(q) => q
        }
    }
}
