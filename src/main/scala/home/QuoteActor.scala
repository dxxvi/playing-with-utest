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
                                 lowPrice: Double
                         )

    sealed trait QuoteSealedTrait
    case object Tick extends QuoteSealedTrait
    case class ResponseWrapper1(r: Response[List[Quote]]) extends QuoteSealedTrait
    case class ResponseWrapper2(r: Response[List[(String, Double, Double, Double)]]) extends QuoteSealedTrait
    case class DailyQuoteRequest(symbol: String) extends QuoteSealedTrait
    case class TodayQuotesRequest(symbol: String) extends QuoteSealedTrait
    case object Debug extends QuoteSealedTrait

    def props(config: Config): Props = Props(new QuoteActor(config))
}

class QuoteActor(config: Config) extends Actor with TimersX with SttpBackends {
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
    val symbolsNeedTodayQuote: collection.mutable.Set[String] = collection.mutable.Set.empty[String]

    timersx.startPeriodicTimer(Tick, Tick, 4019.millis)

    override def receive: Receive = {
        case Tick if DefaultWatchListActor.commaSeparatedSymbolString.nonEmpty =>
            fetchAllQuotes() pipeTo self
            if (symbolsNeedTodayQuote.nonEmpty) {
                fetchTodayQuotes() foreach { responseWrapper2Future => responseWrapper2Future pipeTo self }
                symbolsNeedTodayQuote.clear()
            }
            if (symbolsNeedDailyQuote.nonEmpty) {
                fetchDailyQuotes()
                symbolsNeedDailyQuote.clear()
            }

        case Tick if DefaultWatchListActor.commaSeparatedSymbolString.isEmpty =>
            log.info("Skip because the DefaultWatchListActor is not done yet.")

        case ResponseWrapper1(Response(rawErrorBody, code, statusText, _, _)) => rawErrorBody.fold(
            _ => log.error("Error in getting quotes: {} {}", code, statusText),
            quoteList => quoteList.foreach(q => {
                val stockActor =
                    context.actorSelection(s"../${DefaultWatchListActor.NAME}/${q.symbol}")
                stockActor ! StockActor.Quote(q.lastTradePrice, q.updatedAt)
            })
        )

        case ResponseWrapper2(Response(rawErrorBody, code, statusText, _, _)) => rawErrorBody.fold(
            _ => log.error("Error in getting today quotes: {} {}", code, statusText),
            tupleList => tupleList.foreach(t => {
                val stockActor = context.actorSelection(s"../${DefaultWatchListActor.NAME}/${t._1}")
                stockActor ! StockActor.OpenLowHigh(t._2, t._3, t._4)
            })
        )

        case DailyQuoteRequest(symbol) => symbolsNeedDailyQuote += symbol

        case TodayQuotesRequest(symbol) => symbolsNeedTodayQuote += symbol

        case Debug =>
            val map = debug()
            sender() ! map
    }

    /**
      * Fetch the last trade prices of all stocks, send them to the stock actors.
      */
    private def fetchAllQuotes(): Future[ResponseWrapper1] = {
        implicit val backend: SttpBackend[Future, Source[ByteString, Any]] = configureAkkaHttpBackend(config)
        sttp
                .auth.bearer(Main.accessToken)
                .get(uri"$SERVER/quotes/?symbols=${DefaultWatchListActor.commaSeparatedSymbolString}")
                .response(asString.map(extractLastTradePriceAndSymbol))
                .send()
                .map(ResponseWrapper1)
    }

    private def fetchDailyQuotes() {
        implicit val backend: SttpBackend[Id, Nothing] = configureCoreJavaHttpBackend(config)

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

    private def fetchTodayQuotes(): Iterator[Future[ResponseWrapper2]] = {
        import akka.stream.scaladsl.Source
        import akka.util.ByteString

        implicit val backend: SttpBackend[Future, Source[ByteString, Any]] = configureAkkaHttpBackend(config)

        symbolsNeedTodayQuote.grouped(75).map(symbols => {
            /* {
             *   "results": [
             *     {
             *       "bounds": "regular",
             *       "historicals": [
             *         {
             *           "begins_at": "2019-01-28T14:30:00Z",
             *           "close_price": "20.360000",
             *           "high_price": "20.720000",
             *           "interpolated": false,
             *           "low_price": "20.142500",
             *           "open_price": "20.320000",
             *           "session": "reg",
             *           "volume": 4958447
             *         },
             *         {
             *           "begins_at": "2019-01-28T16:10:00Z",
             *           "close_price": "20.890000",
             *           "high_price": "20.940000",
             *           "interpolated": false,
             *           "low_price": "20.830000",
             *           "open_price": "20.860000",
             *           "session": "reg",
             *           "volume": 833146
             *         }
             *       ],
             *       "instrument": "https://api.robinhood.com/instruments/940fc3f5-1db5-4fed-b452-f3a2e4562b5f/",
             *       "interval": "5minute",
             *       "open_price": "20.320000",
             *       "open_time": "2019-01-28T14:30:00Z",
             *       "previous_close_price": "21.930000",
             *       "previous_close_time": "2019-01-25T21:00:00Z",
             *       "quote": "https://api.robinhood.com/quotes/940fc3f5-1db5-4fed-b452-f3a2e4562b5f/",
             *       "span": "day",
             *       "symbol": "AMD"
             *     },
             *     {
             *       "bounds": "regular",
             *       "historicals": [
             *         {
             *           "begins_at": "2019-01-28T14:30:00Z",
             *           "close_price": "19.505000",
             *           "high_price": "19.560000",
             *           "interpolated": false,
             *           "low_price": "19.200000",
             *           "open_price": "19.200000",
             *           "session": "reg",
             *           "volume": 228090
             *         },
             *         {
             *           "begins_at": "2019-01-28T16:10:00Z",
             *           "close_price": "19.835000",
             *           "high_price": "19.865000",
             *           "interpolated": false,
             *           "low_price": "19.800000",
             *           "open_price": "19.820000",
             *           "session": "reg",
             *           "volume": 22993
             *         }
             *       ],
             *       "instrument": "https://api.robinhood.com/instruments/dad8fa2c-1e8d-4cb9-b354-1f0b91a4193e/",
             *       "interval": "5minute",
             *       "open_price": "19.200000",
             *       "open_time": "2019-01-28T14:30:00Z",
             *       "previous_close_price": "20.120000",
             *       "previous_close_time": "2019-01-25T21:00:00Z",
             *       "quote": "https://api.robinhood.com/quotes/dad8fa2c-1e8d-4cb9-b354-1f0b91a4193e/",
             *       "span": "day",
             *       "symbol": "ON"
             *     }
             *   ]
             * }
             */
            sttp
                    .auth.bearer(home.Main.accessToken)
                    .get(uri"$SERVER/quotes/historicals/?interval=5minute&span=day&symbols=${symbols.mkString(",")}")
                    .response(asString.map(Util.extractSymbolOpenLowHighPrices))
                    .send
                    .map(ResponseWrapper2)
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
