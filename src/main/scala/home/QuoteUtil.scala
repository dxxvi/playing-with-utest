package home

import akka.event.LoggingAdapter
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.softwaremill.sttp._
import home.model.{Quote, Stats}
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics
import org.json4s._
import org.json4s.native.JsonMethods._

import scala.annotation.tailrec
import scala.concurrent.{ExecutionContext, Future}

trait QuoteUtil extends home.util.JsonUtil with AppUtil {
    /**
      * @return Future[List[(symbol, instrument, interval, List[Quote\])\]\]
      *         the quotes in the list are in the ascending begins_at order
      */
    def get5minQuotes(accessToken: String, symbols: List[String])
                     (implicit be: SttpBackend[Future, Source[ByteString, Any]],
                               ec: ExecutionContext,
                               log: LoggingAdapter): Future[List[(String, String, String, List[Quote])]] =
        getIntervalQuotes(accessToken, "5minute", "week", symbols)

    /**
      * @return Future[List[(symbol, instrument, interval, List[Quote\])\]\]
      *         the quotes in the list are in the ascending begins_at order
      */
    def getDailyQuotes(accessToken: String, symbols: List[String])
                      (implicit be: SttpBackend[Future, Source[ByteString, Any]],
                                ec: ExecutionContext,
                                log: LoggingAdapter): Future[List[(String, String, String, List[Quote])]] =
        getIntervalQuotes(accessToken, "day", "year", symbols)

    /**
      * @return Map(symbol -> stats)
      */
    def getStats(accessToken: String, symbols: List[String])
                (implicit be: SttpBackend[Future, Source[ByteString, Any]],
                          ec: ExecutionContext,
                          log: LoggingAdapter): Future[Map[String /* symbol */, Stats]] = {
        def f(quotes: List[Quote], g: Quote => Double): Array[Double] = {
            val descriptiveStatistics = new DescriptiveStatistics(quotes.map(g).toArray)
            (1 to 99).map(descriptiveStatistics.getPercentile(_)).toArray
        }
        
        getDailyQuotes(accessToken, symbols)
                .map((l: List[(String /* symbol */ , String, String, List[Quote])]) => l.map {
                    case (symbol, _, _, _list) =>
                        val list: List[Quote] = _list.reverse

                        val date2PreviousClose: Map[String, Double] = getDate2PreviousClose(_list)

                        val list62 = list.take(62)
                        val HL_3m = f(list62, q => q.high - q.low)
                        val HO_3m = f(list62, q => q.high - q.open)
                        val OL_3m = f(list62, q => q.open - q.low)
                        val HPC_3m = f(list62, q => q.high - date2PreviousClose(q.beginsAt.substring(0, 10)))
                        val PCL_3m = f(list62, q => date2PreviousClose(q.beginsAt.substring(0, 10)) - q.low)
                        val H_3m = f(list62, q => q.high)
                        val L_3m = f(list62, q => q.low)

                        val list22 = list.take(22)
                        val HL_1m = f(list22, q => q.high - q.low)
                        val HO_1m = f(list22, q => q.high - q.open)
                        val OL_1m = f(list22, q => q.open - q.low)
                        val HPC_1m = f(list22, q => q.high - date2PreviousClose(q.beginsAt.substring(0, 10)))
                        val PCL_1m = f(list22, q => date2PreviousClose(q.beginsAt.substring(0, 10)) - q.low)
                        val H_1m = f(list22, q => q.high)
                        val L_1m = f(list22, q => q.low)

                        (symbol, Stats(
                            HL_3m, HO_3m, OL_3m, HPC_3m, PCL_3m, H_3m, L_3m, HL_1m, HO_1m, OL_1m, HPC_1m, PCL_1m, H_1m, L_1m
                        ))
                }.toMap)
    }
        
    /**
      * NOTE: this method is not private because I want to test it.
      * @param list must be in the ascending order of beginsAt, beginsAt is like 2019-04-19T23:22:21.123456Z
      * @return
      */
    def getDate2PreviousClose(list: List[Quote]): Map[String, Double] = {
        if (list.isEmpty) return Map.empty[String, Double]

        case class X(date: String, close: Double, map: Map[String, Double])

        val finalX = list.foldLeft(X(list.head.beginsAt.substring(0, 10), list.head.close, Map.empty))((x, q) =>
            if (q.beginsAt.startsWith(x.date)) X(x.date, q.close, x.map)
            else X(q.beginsAt.substring(0, 10), q.close, x.map + (q.beginsAt.substring(0, 10) -> x.close))
        )
        finalX.map
    }

    /**
      * The return of a /quotes/historicals/?interval=xxx&span=xxx&symbols=a,b,c looks like this:
      * {
      *   "results": [
      *     {
      *       "quote": "https://api.robinhood.com/quotes/940fc3f5-1db5-4fed-b452-f3a2e4562b5f/",
      *       "symbol": "AMD",
      *       "interval": "day",
      *       "span": "year",
      *       "bounds": "regular",
      *       "instrument": "https://api.robinhood.com/instruments/940fc3f5-1db5-4fed-b452-f3a2e4562b5f/",
      *       "historicals": [
      *         {
      *           "begins_at": "2018-01-02T00:00:00Z",
      *           "open_price": "10.420000",
      *           "close_price": "10.980000",
      *           "high_price": "11.020000",
      *           "low_price": "10.340000",
      *           "volume": 44146332,
      *           "session": "reg",
      *           "interpolated": false
      *         },
      *         {
      *           "begins_at": "2018-12-31T00:00:00Z",
      *           "open_price": "18.150000",
      *           "close_price": "18.460000",
      *           "high_price": "18.510100",
      *           "low_price": "17.850000",
      *           "volume": 84732181,
      *           "session": "reg",
      *           "interpolated": false
      *         }
      *       ]
      *     },
      *     {
      *       "quote": "https://api.robinhood.com/quotes/dad8fa2c-1e8d-4cb9-b354-1f0b91a4193e/",
      *       "symbol": "ON",
      *       "interval": "day",
      *       "span": "year",
      *       "bounds": "regular",
      *       "instrument": "https://api.robinhood.com/instruments/dad8fa2c-1e8d-4cb9-b354-1f0b91a4193e/",
      *       "historicals": [
      *         {
      *           "begins_at": "2018-01-02T00:00:00Z",
      *           "open_price": "21.030000",
      *           "close_price": "21.810000",
      *           "high_price": "21.879000",
      *           "low_price": "21.000000",
      *           "volume": 7027432,
      *           "session": "reg",
      *           "interpolated": false
      *         },
      *         {
      *           "begins_at": "2018-12-31T00:00:00Z",
      *           "open_price": "16.380000",
      *           "close_price": "16.510000",
      *           "high_price": "16.510000",
      *           "low_price": "16.170000",
      *           "volume": 2985432,
      *           "session": "reg",
      *           "interpolated": false
      *         }
      *       ]
      *     }
      *   ]
      * }
      * @return Future[List[(symbol, instrument, interval, List[Quote\])\]\]
      */
    private def getIntervalQuotes(accessToken: String, interval: String, span: String, symbols: List[String])
                                 (implicit be: SttpBackend[Future, Source[ByteString, Any]],
                                           ec: ExecutionContext,
                                           log: LoggingAdapter): Future[List[(String, String, String, List[Quote])]] = {
        val x: List[Future[List[(String, String, String, List[Quote])]]] = cutIntoChunks(symbols, 74, Nil)
                .map(chunk /* list of symbols */ => sttp
                    .auth.bearer(accessToken)
                    .get(uri"https://api.robinhood.com/quotes/historicals/?interval=$interval&span=$span&symbols=${chunk.mkString(",")}")
                    .response(asString)
                    .send()
                    .collect {
                        case Response(Left(_), _, statusText, _, _) =>
                            log.error(s"/quotes/historicals/?interval=$interval&span=$span&symbols=${chunk.mkString(",")} returns {}", statusText)
                            """{"results": []}"""
                        case Response(Right(s), _, _, _, _) => s
                    }
                    .map(s => parse(s) \ "results" match {
                        case JArray(arr) =>
                            arr
                                    .map(extractSymbolInstrumentIntervalQuotes)
                                    .collect { case Some(t) => t }
                        case _ =>
                            log.error("No results field in {}", s)
                            Nil
                    })

                )
        Future.sequence(x).map(_.flatten)
    }

    /**
      * @param jValue is like this
      *     {
      *       "quote": "https://api.robinhood.com/quotes/dad8fa2c-1e8d-4cb9-b354-1f0b91a4193e/",
      *       "symbol": "ON",
      *       "interval": "day",
      *       "span": "year",
      *       "bounds": "regular",
      *       "instrument": "https://api.robinhood.com/instruments/dad8fa2c-1e8d-4cb9-b354-1f0b91a4193e/",
      *       "historicals": [
      *         {
      *           "begins_at": "2018-01-02T00:00:00Z",
      *           "open_price": "21.030000",
      *           "close_price": "21.810000",
      *           "high_price": "21.879000",
      *           "low_price": "21.000000",
      *           "volume": 7027432,
      *           "session": "reg",
      *           "interpolated": false
      *         },
      *        {
      *           "begins_at": "2018-12-31T00:00:00Z",
      *           "open_price": "16.380000",
      *           "close_price": "16.510000",
      *           "high_price": "16.510000",
      *           "low_price": "16.170000",
      *           "volume": 2985432,
      *           "session": "reg",
      *           "interpolated": false
      *         }
      *       ]
      *     }
      * @return Option[(symbol, instrument, interval, List[Quote\])\]
      */
    private def extractSymbolInstrumentIntervalQuotes(jValue: JValue): Option[(String, String, String, List[Quote])] = {
        val quotes: List[Quote] = jValue \ "historicals" match {
            case JArray(arr) =>
                arr
                        .map(jv =>
                            for {
                                beginsAt <- fromJValueToOption[String](jv \ "begins_at")
                                open     <- fromJValueToOption[Double](jv \ "open_price")
                                close    <- fromJValueToOption[Double](jv \ "close_price")
                                high     <- fromJValueToOption[Double](jv \ "high_price")
                                low      <- fromJValueToOption[Double](jv \ "low_price")
                            } yield Quote(beginsAt, open, close, high, low)
                        )
                        .collect { case Some(quote) => quote }
            case _ => Nil
        }
        if (quotes.isEmpty) None
        else
            for {
                symbol   <- fromJValueToOption[String](jValue \ "symbol")
                ins      <- fromJValueToOption[String](jValue \ "instrument")
                interval <- fromJValueToOption[String](jValue \ "interval")
            } yield (
                    symbol,
                    extractInstrument(ins),
                    interval,
                    quotes
            )
    }

    // cut list into chunks of not more than n elements
    @tailrec
    private def cutIntoChunks[A](list: List[A], n: Int, result: List[List[A]]): List[List[A]] = {
        if (list.isEmpty) result
        else cutIntoChunks(list.drop(n), n, result :+ list.take(n))
    }
}
