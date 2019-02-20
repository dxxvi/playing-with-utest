package home.sparkjava

import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.softwaremill.sttp._
import com.typesafe.config.Config
import org.json4s._

import scala.concurrent.Future
import scala.reflect.runtime.universe._
import scala.util.{Success, Try}
import scala.util.matching.Regex

trait Util {
    private val decimalPart: Regex = """\.\d+$""".r

    def isDow(symbol: String): Boolean = Main.dowStocks.contains(symbol)

    def configureAkkaHttpBackend(config: Config): SttpBackend[Future, Source[ByteString, Any]] = {
        import com.softwaremill.sttp.akkahttp._

        val o = for {
            proxyHost <- if (config.hasPath("proxy.host")) Some(config.getString("proxy.host")) else None
            proxyPort <- if (config.hasPath("proxy.port")) Some(config.getInt("proxy.port")) else None
        } yield (proxyHost, proxyPort)

        o.fold(AkkaHttpBackend())(t => AkkaHttpBackend(options = SttpBackendOptions.httpProxy(t._1, t._2)))
    }

    def configureCoreJavaHttpBackend(config: Config): SttpBackend[Id, Nothing] = {
        val o = for {
            proxyHost <- if (config.hasPath("proxy.host")) Some(config.getString("proxy.host")) else None
            proxyPort <- if (config.hasPath("proxy.port")) Some(config.getInt("proxy.port")) else None
        } yield (proxyHost, proxyPort)
        o.fold(HttpURLConnectionBackend())(t => HttpURLConnectionBackend(options = SttpBackendOptions.httpProxy(t._1, t._2)))
    }

    def fromStringToOption[T: TypeTag](jValue: JValue, field: String): Option[T] = fromJValueToOption[T](jValue \ field)

    // T is String, Int, Long or Boolean only.
    def fromToOption[T: TypeTag](jValue: JValue, field: String): Option[T] = fromJValueToOption[T](jValue \ field)

    def fromJValueToOption[T: TypeTag](jValue: JValue): Option[T] = jValue match {
        case JString(x) => typeOf[T] match {
            case t if t =:= typeOf[String] => Some(x).asInstanceOf[Option[T]]
            case t if t =:= typeOf[Int] =>  Try(decimalPart.replaceAllIn(x, "").toInt) match {
                case Success(i) => Some(i).asInstanceOf[Option[T]]
                case _ => None.asInstanceOf[Option[T]]
            }
            case t if t =:= typeOf[Long] => Try(x.toLong) match {
                case Success(l) => Some(l).asInstanceOf[Option[T]]
                case _ => None.asInstanceOf[Option[T]]
            }
            case t if t =:= typeOf[Double] => Try(x.toDouble) match {
                case Success(d) => Some(d).asInstanceOf[Option[T]]
                case _ => None.asInstanceOf[Option[T]]
            }
            case t if t =:= typeOf[Boolean] => Try(x.toBoolean) match {
                case Success(b) => Some(b).asInstanceOf[Option[T]]
                case _ => None.asInstanceOf[Option[T]]
            }
            case _ => None.asInstanceOf[Option[T]]
        }
        case JInt(num) => typeOf[T] match {
            case t if t =:= typeOf[Int] => Some(num.toInt).asInstanceOf[Option[T]]
            case t if t =:= typeOf[Long] => Some(num.toLong).asInstanceOf[Option[T]]
            case t if t =:= typeOf[Double] => Some(num.toDouble).asInstanceOf[Option[T]]
            case _ => None.asInstanceOf[Option[T]]
        }
        case JLong(num) => typeOf[T] match {
            case t if t =:= typeOf[Int] => Some(num.toInt).asInstanceOf[Option[T]]
            case t if t =:= typeOf[Long] => Some(num.toLong).asInstanceOf[Option[T]]
            case t if t =:= typeOf[Double] => Some(num.toDouble).asInstanceOf[Option[T]]
            case _ => None.asInstanceOf[Option[T]]
        }
        case JBool(bool) => typeOf[T] match {
            case t if t =:= typeOf[Boolean] => Some(bool).asInstanceOf[Option[T]]
            case _ => None.asInstanceOf[Option[T]]
        }
        case JNull => typeOf[T] match {
            case t if t =:= typeOf[Double] => Some(Double.NaN).asInstanceOf[Option[T]]
            case _ => None.asInstanceOf[Option[T]]
        }
        case _ => None
    }

    /**
      * Main.accessToken must be ready first. Main.watchedSymbols must be ready second.
      * The returned string is like this
      * {
      *   "results": [
      *      {
      *        "adjusted_previous_close": "17.820000",
      *        "ask_price": "17.970000",
      *        "ask_size": 3800,
      *        "bid_price": "17.960000",
      *        "bid_size": 5400,
      *        "has_traded": true,
      *        "instrument": "https://api.robinhood.com/instruments/940fc3f5-1db5-4fed-b452-f3a2e4562b5f/",
      *        "last_extended_hours_trade_price": null,
      *        "last_trade_price": "18.010000",
      *        "last_trade_price_source": "nls",
      *        "previous_close": "17.820000",
      *        "previous_close_date": "2018-12-28",
      *        "symbol": "AMD",
      *        "trading_halted": false,
      *        "updated_at": "2018-12-31T16:09:15Z"
      *      },
      *      {
      *        "adjusted_previous_close": "16.330000",
      *        "ask_price": "16.300000",
      *        "ask_size": 600,
      *        "bid_price": "16.290000",
      *        "bid_size": 1000,
      *        "has_traded": true,
      *        "instrument": "https://api.robinhood.com/instruments/dad8fa2c-1e8d-4cb9-b354-1f0b91a4193e/",
      *        "last_extended_hours_trade_price": null,
      *        "last_trade_price": "16.220000",
      *        "last_trade_price_source": "nls",
      *        "previous_close": "16.330000",
      *        "previous_close_date": "2018-12-28",
      *        "symbol": "ON",
      *        "trading_halted": false,
      *        "updated_at": "2018-12-31T16:09:15Z"
      *      },
      *      {
      *        "adjusted_previous_close": "333.870000",
      *        "ask_price": "328.110000",
      *        "ask_size": 200,
      *        "bid_price": "327.850000",
      *        "bid_size": 200,
      *        "has_traded": true,
      *        "instrument": "https://api.robinhood.com/instruments/e39ed23a-7bd1-4587-b060-71988d9ef483/",
      *        "last_extended_hours_trade_price": null,
      *        "last_trade_price": "328.730000",
      *        "last_trade_price_source": "nls",
      *        "previous_close": "333.870000",
      *        "previous_close_date": "2018-12-28",
      *        "symbol": "TSLA",
      *        "trading_halted": false,
      *        "updated_at": "2018-12-31T16:09:16Z"
      *      }
      *   ]
      * }
      */
    def getLastTradePrices(config: Config)(implicit ec: concurrent.ExecutionContext): Future[String] = {
        import akka.stream.scaladsl.Source
        import akka.util.ByteString

        val SERVER: String = config.getString("server")
        implicit val backend: SttpBackend[Future, Source[ByteString, Any]] = configureAkkaHttpBackend(config)
        sttp
                .auth.bearer(Main.accessToken)
                .get(uri"$SERVER/quotes/?symbols=${Main.instrument2Symbol.values.mkString(",")}")
                .response(asString)
                .send()
                .map((r: Response[String]) => r.rawErrorBody match {
                    case Left(_) =>
                        println(s"Error in getting last trade prices of ${Main.instrument2Symbol.values.mkString(",")}: ${r.statusText}")
                        """{"results":[]}"""
                    case Right(jString) => jString
                })
    }

    /**
      * Main.accessToken and Main.instrument2Symbol must be populated first.
      * Main.instrument2Symbol is splitted into half (the url can take 75 symbols at a time).
      * That's why the return is Future[(String, String)].
      * The returned string is like this:
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
      *   ]
      * }
      */
    def getDailyQuotes(config: Config)(implicit ec: concurrent.ExecutionContext): Future[(String, String)] =
        getIntervalQuotes(config, "day", "year")

    def get5minQuotes(config: Config)(implicit ec: concurrent.ExecutionContext): Future[(String, String)] =
        getIntervalQuotes(config, "5minute", "day")

    private def getIntervalQuotes(config: Config, interval: String, span: String)
                                 (implicit ec: concurrent.ExecutionContext): Future[(String, String)] = {
        import akka.stream.scaladsl.Source
        import akka.util.ByteString

        val n = Main.instrument2Symbol.size / 2
        val symbolList1 = Main.instrument2Symbol.values.take(n)
        val symbolList2 = Main.instrument2Symbol.values.drop(n)

        val SERVER: String = config.getString("server")
        implicit val backend: SttpBackend[Future, Source[ByteString, Any]] = configureAkkaHttpBackend(config)
        val future1: Future[String] = sttp
                .auth.bearer(Main.accessToken)
                .get(uri"${SERVER}quotes/historicals/?interval=$interval&span=$span&symbols=${symbolList1.mkString(",")}")
                .response(asString)
                .send()
                .map((r: Response[String]) => r.rawErrorBody match {
                    case Left(_) =>
                        println(s"Error in getting daily quotes of ${symbolList1.mkString(",")}: ${r.statusText}")
                        """{"results":[]}"""
                    case Right(jString) => jString
                })
        val future2: Future[String] = sttp
                .auth.bearer(Main.accessToken)
                .get(uri"${SERVER}quotes/historicals/?interval=$interval&span=$span&symbols=${symbolList2.mkString(",")}")
                .response(asString)
                .send()
                .map((r: Response[String]) => r.rawErrorBody match {
                    case Left(_) =>
                        println(s"Error in getting daily quotes of ${symbolList2.mkString(",")}: ${r.statusText}")
                        """{"results":[]}"""
                    case Right(jString) => jString
                })
        future1 zip future2
    }

    def getPositions(config: Config)(implicit ec: concurrent.ExecutionContext): Future[String] = {
        val SERVER: String = config.getString("server")
        implicit val backend: SttpBackend[Future, Source[ByteString, Any]] = configureAkkaHttpBackend(config)

        sttp
                .auth.bearer(Main.accessToken)
                .get(uri"${SERVER}positions/")
                .response(asString)
                .send()
                .map((r: Response[String]) => r.rawErrorBody match {
                    case Left(_) =>
                        println(s"Error in getting positions: ${r.statusText}")
                        """{"results":[]}"""
                    case Right(jString) => jString
                })
    }

    // open, high, low can be Double.NaN
    def calculateOpenHighLow(symbol25minQuoteList: Map[String, List[home.sparkjava.model.DailyQuote]]):
        Map[String, (Double /* open */, Double /* high */, Double /* low */)] = symbol25minQuoteList.mapValues(
        _.foldLeft((Double.NaN, Double.NaN, Double.NaN))((t, dq) => {
            val open: Double = if (t._1.isNaN) dq.open_price else t._1
            val high: Double =
                if (t._2.isNaN) dq.high_price
                else if (!dq.high_price.isNaN && dq.high_price > t._2) dq.high_price
                else t._2
            val low: Double =
                if (t._3.isNaN) dq.low_price
                else if (!dq.low_price.isNaN && dq.low_price > t._3) dq.low_price
                else t._3
            (open, high, low)
        })
    )
}
