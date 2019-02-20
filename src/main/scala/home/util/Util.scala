package home.util

import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.runtime.universe._
import scala.util.Failure
import scala.util.Success
import scala.util.Try
import com.typesafe.config.Config
import com.softwaremill.sttp._
import org.json4s._
import org.json4s.native.JsonMethods._
import home._

object Util extends SttpBackends {
    def getSymbolFromInstrumentHttpURLConnection(url: String, config: Config): Try[String] = {
        implicit val backend: SttpBackend[Id, Nothing] = configureCoreJavaHttpBackend(config)
        Try(
            sttp
                .auth.bearer(home.Main.accessToken)
                .get(uri"$url")
                .send()
                .body match {
                    case Right(jString) => (parse(jString) \ "symbol").asInstanceOf[JString].values
                    case Left(s) => throw new RuntimeException(s)
                }
        )
    }

    def getDailyQuoteHttpURLConnection(symbols: collection.Set[String], config: Config):
    List[(String /* symbol */, List[QuoteActor.DailyQuote])] = {
        implicit val backend: SttpBackend[Id, Nothing] = configureCoreJavaHttpBackend(config)
        val SERVER = config.getString("server")

        /*
         * response is like this:
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
        sttp
                .auth.bearer(home.Main.accessToken)
                .get(uri"$SERVER/quotes/historicals/?interval=day&span=year&symbols=${symbols.mkString(",")}")
                .send()
                .body match {
                    case Right(js) =>
                        (parse(js) \ "results").asInstanceOf[JArray].arr
                                .map(jv => for {
                                    symbol <- fromJValueToOption[String](jv \ "symbol")
                                    dailyQuoteList = getIntervalQuotesFromJArray((jv \ "historicals").asInstanceOf[JArray])
                                } yield (symbol, dailyQuoteList)
                                )
                                .collect {
                                    case Some(tuple) => tuple
                                }
                    case Left(s) => throw new RuntimeException(
                        s"$SERVER/quotes/historicals/?interval=day&span=year&symbols=... " + s)
                }
    }

    /**
     * Main.accessToken and Main.watchedSymbols must be populated first.
     * Main.watchedSymbols is splitted into half (the url can take 75 symbols at a time).
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
    def getDailyQuotes(config: Config)(implicit ec: ExecutionContext): Future[(String, String)] =
        getIntervalQuotes(config, "day", "year")

    /**
      * jString is like in the getDailyQuotes(config: Config, interval: String, span: String)
      *                                      (implicit ec: ExecutionContext) function
      */
    def getIntervalQuotes(jString1: String, jString2: String): Map[String /* symbol */, List[QuoteActor.DailyQuote]] = {
        def f(jString: String): Map[String, List[QuoteActor.DailyQuote]] =
            parse(jString) \ "results" match {
                case x: JArray => x.arr
                        .map(jv => for {
                            symbol <- fromJValueToOption[String](jv \ "symbol")
                            dailyQuoteList = getIntervalQuotesFromJArray((jv \ "historicals").asInstanceOf[JArray])
                        } yield (symbol, dailyQuoteList))
                        .collect { case Some(tuple) => tuple }
                        .toMap

                case _ =>
                    println(s"Error1 in $jString")
                    Map.empty[String, List[QuoteActor.DailyQuote]]
            }

        f(jString1) ++ f(jString2)
    }

    def get5minQuotes(config: Config)(implicit ec: ExecutionContext): Future[(String, String)] =
        getIntervalQuotes(config, "5minute", "day")

    private def getIntervalQuotes(config: Config, interval: String, span: String)
                                 (implicit ec: ExecutionContext): Future[(String, String)] = {
        import akka.stream.scaladsl.Source
        import akka.util.ByteString

        val n = Main.watchedSymbols.size / 2
        val symbolList1 = Main.watchedSymbols.take(n)
        val symbolList2 = Main.watchedSymbols.drop(n)

        val SERVER: String = config.getString("server")
        implicit val backend: SttpBackend[Future, Source[ByteString, Any]] = configureAkkaHttpBackend(config)
        val future1: Future[String] = sttp
                .auth.bearer(home.Main.accessToken)
                .get(uri"$SERVER/quotes/historicals/?interval=$interval&span=$span&symbols=${symbolList1.mkString(",")}")
                .response(asString)
                .send()
                .map((r: Response[String]) => r.rawErrorBody match {
                    case Left(_) =>
                        println(s"Error in getting daily quotes of ${symbolList1.mkString(",")}: ${r.statusText}")
                        """{"results":[]}"""
                    case Right(jString) => jString
                })
        val future2: Future[String] = sttp
                .auth.bearer(home.Main.accessToken)
                .get(uri"$SERVER/quotes/historicals/?interval=$interval&span=$span&symbols=${symbolList2.mkString(",")}")
                .response(asString)
                .send()
                .map((r: Response[String]) => r.rawErrorBody match {
                    case Left(_) =>
                        println(s"Error in getting daily quotes of ${symbolList2.mkString(",")}: ${r.statusText}")
                        """{"results":[]}"""
                    case Right(jString) => jString
                })
        for {
            string1 <- future1
            string2 <- future2
        } yield (string1, string2)
    }

    /**
      * Main.accessToken must be ready first. Main.watchedSymbols must be ready second.
      * Returns a string like: "~~~error message" if there's any error. 
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
    def getLastTradePrices(config: Config)(implicit ec: ExecutionContext): Future[String] = {
        import akka.stream.scaladsl.Source
        import akka.util.ByteString

        val SERVER: String = config.getString("server")
        implicit val backend: SttpBackend[Future, Source[ByteString, Any]] = configureAkkaHttpBackend(config)
        sttp
                .auth.bearer(Main.accessToken)
                .get(uri"$SERVER/quotes/?symbols=${Main.watchedSymbols.mkString(",")}")
                .response(asString)
                .send()
                .map((r: Response[String]) => r.rawErrorBody match {
                    case Left(_) =>
                        s"~~~Error in getting last trade prices of ${Main.watchedSymbols.mkString(",")}: ${r.statusText}"
                    case Right(jString) => jString
                })
    }

    /**
     * jString is like in the getLastTradePrices(config: Config)(implicit ec: ExecutionContext) function
     */
    def getLastTradePrices(jString: String): Map[String /* symbol */, Double] = parse(jString) \ "results" match {
        case x: JArray => x.arr
                .map(jv => for {
                    symbol         <- fromJValueToOption[String](jv \ "symbol")
                    lastTradePrice <- fromJValueToOption[Double](jv \ "last_trade_price")
                } yield (symbol, lastTradePrice))
                .collect { case Some(t) => t }
                .toMap
        case _ =>
            println(s"Error2 in $jString")
            Map.empty[String, Double]
    }

    def get5minsQuoteHttpURLConnection(symbols: collection.Set[String], config: Config):
    List[(String, List[home.QuoteActor.DailyQuote])] = {
        implicit val backend: SttpBackend[Id, Nothing] = configureCoreJavaHttpBackend(config)
        val SERVER = config.getString("server")
        sttp.auth.bearer(home.Main.accessToken)
                .get(uri"$SERVER/quotes/historicals/?interval=5minute&span=week&symbols=${symbols.mkString(",")}")
                .send()
                .body match {
            case Right(js) =>
                (parse(js) \ "results").asInstanceOf[JArray].arr
                        .map(jv => for {
                            symbol <- fromJValueToOption[String](jv \ "symbol")
                            dailyQuoteList = getIntervalQuotesFromJArray((jv \ "historicals").asInstanceOf[JArray])
                        } yield (symbol, dailyQuoteList)
                        )
                        .collect {
                            case Some(tuple) => tuple
                        }
            case Left(s) => throw new RuntimeException(
                s"$SERVER/quotes/historicals/?interval=5minute&span=week&symbols=... " + s)
        }
    }

    def fromJValueToOption[T: TypeTag](jValue: JValue): Option[T] = jValue match {
        case JString(x) => typeOf[T] match {
            case t if t =:= typeOf[String] => Some(x).asInstanceOf[Option[T]]
            case t if t =:= typeOf[Int] =>  Try(x.toInt) match {
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
      * This method is here for easy testing. The StockDatabase must be populated first.
      * @param s is like
      * {
      *   "previous": null,
      *   "results": [
      *     {
      *       "updated_at": "2018-12-19T19:58:54.189248Z",
      *       "ref_id": null,
      *       "time_in_force": "gfd",
      *       "fees": "0.00",
      *       "cancel": null,
      *       "response_category": "unknown",
      *       "id": "6fae5051-5fad-4215-9091-4c60d6916a4a",
      *       "cumulative_quantity": "12.00000",
      *       "stop_price": null,
      *       "reject_reason": null,
      *       "instrument": "https://api.robinhood.com/instruments/3d9a6798-df9e-4e29-90e3-3184f2379471/",
      *       "state": "filled",
      *       "trigger": "immediate",
      *       "override_dtbp_checks": false,
      *       "type": "limit",
      *       "last_transaction_at": "2018-12-19T19:58:53.930000Z",
      *       "price": "3.77000000",
      *       "executions": [
      *         {
      *           "timestamp": "2018-12-19T19:58:53.926000Z",
      *           "price": "3.77000000",
      *           "settlement_date": "2018-12-21",
      *           "id": "fb646d63-f6bd-42db-b4f6-b3b5be76a5a8",
      *           "quantity": "1.00000"
      *         },
      *         {
      *           "timestamp": "2018-12-19T19:58:53.930000Z",
      *           "price": "3.77000000",
      *           "settlement_date": "2018-12-21",
      *           "id": "2a308feb-e887-4992-a12f-eeef3127eded",
      *           "quantity": "11.00000"
      *         }
      *       ],
      *       "extended_hours": false,
      *       "account": "https://api.robinhood.com/accounts/5RY82436/",
      *       "url": "https://api.robinhood.com/orders/6fae5051-5fad-4215-9091-4c60d6916a4a/",
      *       "created_at": "2018-12-19T19:41:10.253195Z",
      *       "side": "buy",
      *       "override_day_trade_checks": false,
      *       "position": "https://api.robinhood.com/positions/5RY82436/3d9a6798-df9e-4e29-90e3-3184f2379471/",
      *       "average_price": "3.77000000",
      *       "quantity": "12.00000"
      *     },
      *     {
      *       "updated_at": "2018-12-19T19:21:54.731406Z",
      *       "ref_id": null,
      *       "time_in_force": "gfd",
      *       "fees": "0.04",
      *       "cancel": null,
      *       "response_category": "unknown",
      *       "id": "3b1ca207-26f1-4c08-91df-7064d051d157",
      *       "cumulative_quantity": "3.00000",
      *       "stop_price": null,
      *       "reject_reason": null,
      *       "instrument": "https://api.robinhood.com/instruments/ebab2398-028d-4939-9f1d-13bf38f81c50/",
      *       "state": "filled",
      *       "trigger": "immediate",
      *       "override_dtbp_checks": false,
      *       "type": "limit",
      *       "last_transaction_at": "2018-12-19T19:21:54.493000Z",
      *       "price": "136.17000000",
      *       "executions": [
      *         {
      *           "timestamp": "2018-12-19T19:21:54.483000Z",
      *           "price": "136.17000000",
      *           "settlement_date": "2018-12-21",
      *           "id": "5644059d-097d-4140-8145-d16a84d10c18",
      *           "quantity": "1.00000"
      *         },
      *         {
      *           "timestamp": "2018-12-19T19:21:54.493000Z",
      *           "price": "136.17000000",
      *           "settlement_date": "2018-12-21",
      *           "id": "149d72ca-1b67-468a-bd23-ce01c911ada7",
      *           "quantity": "2.00000"
      *         }
      *       ],
      *       "extended_hours": false,
      *       "account": "https://api.robinhood.com/accounts/5RY82436/",
      *       "url": "https://api.robinhood.com/orders/3b1ca207-26f1-4c08-91df-7064d051d157/",
      *       "created_at": "2018-12-19T19:21:54.194476Z",
      *       "side": "sell",
      *       "override_day_trade_checks": false,
      *       "position": "https://api.robinhood.com/positions/5RY82436/ebab2398-028d-4939-9f1d-13bf38f81c50/",
      *       "average_price": "136.17000000",
      *       "quantity": "3.00000"
      *     },
      *     {
      *       "updated_at": "2018-12-19T20:04:03.583948Z",
      *       "ref_id": null,
      *       "time_in_force": "gfd",
      *       "fees": "0.00",
      *       "cancel": null,
      *       "response_category": "unknown",
      *       "id": "e73ee4a5-3917-4739-88bd-e314641d2800",
      *       "cumulative_quantity": "0.00000",
      *       "stop_price": null,
      *       "reject_reason": null,
      *       "instrument": "https://api.robinhood.com/instruments/dad8fa2c-1e8d-4cb9-b354-1f0b91a4193e/",
      *       "state": "cancelled",
      *       "trigger": "immediate",
      *       "override_dtbp_checks": false,
      *       "type": "limit",
      *       "last_transaction_at": "2018-12-19T20:04:03.368000Z",
      *       "price": "17.25000000",
      *       "executions": [],
      *       "extended_hours": false,
      *       "account": "https://api.robinhood.com/accounts/5RY82436/",
      *       "url": "https://api.robinhood.com/orders/e73ee4a5-3917-4739-88bd-e314641d2800/",
      *       "created_at": "2018-12-19T19:32:58.272936Z",
      *       "side": "sell",
      *       "override_day_trade_checks": false,
      *       "position": "https://api.robinhood.com/positions/5RY82436/dad8fa2c-1e8d-4cb9-b354-1f0b91a4193e/",
      *       "average_price": null,
      *       "quantity": "15.00000"
      *     }
      *   ],
      *   "next": "https://api.robinhood.com/orders/?cursor=cD0yMDE4LTEyLTE4KzE5JTNBMjUlM0ExNy41MTgzNzQlMkIwMCUzQTAw"
      * }
      */
    def extractSymbolAndOrder(s: String): Map[String /* symbol */, List[home.StockActor.Order]] = {
        val jvalueList = (parse(s) \ "results").asInstanceOf[JArray].arr
        jvalueList.map(jv => for {
            instrument         <- fromJValueToOption[String](jv \ "instrument")
            symbol             <- StockDatabase.getInstrumentFromInstrument(instrument).map(_.symbol)
            averagePrice       <- fromJValueToOption[Double](jv \ "average_price")
            createdAt          <- fromJValueToOption[String](jv \ "created_at")
            cumulativeQuantity <- fromJValueToOption[Double](jv \ "cumulative_quantity")
            id                 <- fromJValueToOption[String](jv \ "id")
            price              <- fromJValueToOption[Double](jv \ "price")
            quantity           <- fromJValueToOption[Double](jv \ "quantity")
            side               <- fromJValueToOption[String](jv \ "side")
            state              <- fromJValueToOption[String](jv \ "state")
            updatedAt          <- fromJValueToOption[String](jv \ "updated_at")
        } yield (symbol, home.StockActor.Order(
            if (averagePrice.isNaN) price else averagePrice,
            createdAt,
            cumulativeQuantity,
            id,
            if (price.isNaN) averagePrice else price,
            quantity,
            side,
            state,
            updatedAt
        ))) collect {
            case Some(tuple) => tuple
        } groupBy { t: (String, home.StockActor.Order) =>
            t._1
        } mapValues { list: List[(String, home.StockActor.Order)] =>
            list map (_._2)
        }
    }

    /**
      * StockDatabase must be populated first. Main.watchedSymbols must be populated second.
      * This method only extracts symbols in watchedSymbols and is here for easy testing.
      * @param js is like this
      * {
      *   "previous": null,
      *   "results": [
      *     {
      *       "shares_held_for_stock_grants": "0.0000",
      *       "account": "https://api.robinhood.com/accounts/5RY82436/",
      *       "pending_average_buy_price": "13.5292",
      *       "shares_held_for_options_events": "0.0000",
      *       "intraday_average_buy_price": "0.0000",
      *       "url": "https://api.robinhood.com/positions/5RY82436/5e0dcc39-6692-4126-8741-e6e26f13672b/",
      *       "shares_held_for_options_collateral": "0.0000",
      *       "created_at": "2018-12-17T14:18:17.268914Z",
      *       "updated_at": "2018-12-19T19:42:41.154695Z",
      *       "shares_held_for_buys": "0.0000",
      *       "average_buy_price": "13.5292",
      *       "instrument": "https://api.robinhood.com/instruments/5e0dcc39-6692-4126-8741-e6e26f13672b/",
      *       "intraday_quantity": "0.0000",
      *       "shares_held_for_sells": "0.0000",
      *       "shares_pending_from_options_events": "0.0000",
      *       "quantity": "6.0000"
      *     },
      *     {
      *       "shares_held_for_stock_grants": "0.0000",
      *       "account": "https://api.robinhood.com/accounts/5RY82436/",
      *       "pending_average_buy_price": "0.0000",
      *       "shares_held_for_options_events": "0.0000",
      *       "intraday_average_buy_price": "0.0000",
      *       "url": "https://api.robinhood.com/positions/5RY82436/552aedf0-af4b-4693-8825-cbee56a685bc/",
      *       "shares_held_for_options_collateral": "0.0000",
      *       "created_at": "2019-01-02T10:18:04.328826Z",
      *       "updated_at": "2019-01-02T10:18:04.334476Z",
      *       "shares_held_for_buys": "0.0000",
      *       "average_buy_price": "0.0000",
      *       "instrument": "https://api.robinhood.com/instruments/552aedf0-af4b-4693-8825-cbee56a685bc/",
      *       "intraday_quantity": "0.0000",
      *       "shares_held_for_sells": "0.0000",
      *       "shares_pending_from_options_events": "0.0000",
      *       "quantity": "2.0000"
      *     }
      *   ],
      *   "next": null
      * }
      */
    def extractSymbolAndPosition(js: String): Map[String /* symbol */, Double] = {
        (parse(js) \ "results").asInstanceOf[JArray].arr
                .map(jv => for {
                    instrument <- fromJValueToOption[String](jv \ "instrument")
                    symbol <- StockDatabase.getInstrumentFromInstrument(instrument).map(_.symbol)
                    quantity <- fromJValueToOption[Double](jv \ "quantity")
                    if !quantity.isNaN
                    if home.Main.watchedSymbols contains symbol
                } yield (symbol, quantity))
                .collect {
                    case Some(tuple) => tuple
                }
                .toMap
    }

    /**
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
    def fetchAllQuotes(config: Config)(implicit ec: concurrent.ExecutionContext): Future[String] = {
        import com.softwaremill.sttp._
        import akka.stream.scaladsl.Source
        import akka.util.ByteString

        implicit val backend: SttpBackend[Future, Source[ByteString, Any]] = configureAkkaHttpBackend(config)
        Future("")
    }

    def retrieveAccessToken(config: Config): Either[String, String] = {
        import org.json4s.JsonAST.{JObject, JString}
        import org.json4s.native.Serialization

        if (config.hasPath("accessToken")) return Right(config.getString("accessToken"))

        val SERVER = config.getString("server")

        Encryption.decrypt(Encryption.getKey(config), config.getString("authorization.encryptedPassword")) match {
            case Success(password) =>
                val body = Serialization.write(JObject(
                    "username" -> JString(config.getString("authorization.username")),
                    "password" -> JString(password),
                    "grant_type" -> JString("password"),
                    "client_id" -> JString("c82SH0WZOsabOXGP2sxqcj34FxkvfnWRZBKlBjFS")
                ))(DefaultFormats)

                implicit val backend: SttpBackend[Id, Nothing] = configureCoreJavaHttpBackend(config)
                sttp
                        .header(HeaderNames.ContentType, MediaTypes.Json)
                        .body(body)
                        .post(uri"$SERVER/oauth2/token/")
                        .send()
                        .body match {
                    case Right(jString) => fromJValueToOption[String](parse(jString) \ "access_token") match {
                        case Some(accessToken) =>
                            println(s"Access token: $accessToken")
                            Right(accessToken)
                        case _ => Left(s"No access_token field in $jString")
                    }
                    case x => x
                }
            case Failure(ex) => Left(ex.getMessage)
        }
    }

    def retrievePositionAndRecentOrdersResponseFuture(config: Config)(implicit ec: ExecutionContext):
    Future[OrderActor.Responses] = {
        import akka.stream.scaladsl.Source
        import akka.util.ByteString

        val SERVER: String = config.getString("server")

//        implicit val _ec: ExecutionContext = ec
        implicit val backend: SttpBackend[Future, Source[ByteString, Any]] = configureAkkaHttpBackend(config)

        val recentOrdersRequest: RequestT[Id, String, Nothing] = sttp
                .auth.bearer(Main.accessToken)
                .get(uri"$SERVER/orders/")
                .response(asString)

        val positionRequest: RequestT[Id, String, Nothing] = sttp
                .auth.bearer(Main.accessToken)
                .get(uri"$SERVER/positions/")
                .response(asString)

        val recentOrdersResponseFuture: Future[Response[String]] = recentOrdersRequest.send()
        val positionResponseFuture: Future[Response[String]] = positionRequest.send()
        for {
            recentOrdersResponse <- recentOrdersResponseFuture
            positionResponse <- positionResponseFuture
        } yield OrderActor.Responses(recentOrdersResponse, positionResponse)
    }

    /**
      * @param s looks like this
      * {
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
    def extractSymbolOpenLowHighPrices(s: String): List[(
            String /* symbol */, Double /* open */, Double /* low */, Double /* high */
            )] = {
        def extractOpenLowHighPrices(l: List[home.QuoteActor.DailyQuote]): (
                Double /* open */, Double /* low */, Double /* high */) = {
            val openPrice =
                if (l.nonEmpty && l.head.beginsAt.endsWith(":30:00Z")) l.head.openPrice else Double.NaN
            l.foldLeft((openPrice, Double.NaN, Double.NaN))((t, dq) => {
                val low = if (t._2.isNaN && dq.lowPrice.isNaN) Double.NaN
                    else if (t._2.isNaN) dq.lowPrice
                    else if (dq.lowPrice.isNaN) t._2
                    else math.min(t._2, dq.lowPrice)
                val high = if (t._3.isNaN && dq.highPrice.isNaN) Double.NaN
                    else if (t._3.isNaN) dq.highPrice
                    else if (dq.highPrice.isNaN) t._3
                    else math.min(t._3, dq.highPrice)
                (t._1, low, high)
            })
        }

        (parse(s) \ "results").asInstanceOf[JArray].arr
                .map(jv => for {
                    symbol <- fromJValueToOption[String](jv \ "symbol")
                    dailyQuoteList = getIntervalQuotesFromJArray((jv \ "historicals").asInstanceOf[JArray])
                    openLowHigh = extractOpenLowHighPrices(dailyQuoteList)
                } yield (symbol, openLowHigh._1, openLowHigh._2, openLowHigh._3)
                )
                .collect { case Some(tuple) => tuple }
    }

    /**
      * Reads the order-history.txt from the current directory.
      * Converts the state of cancelled orders having cumulative_quantity > 0 to filled, assigns the value of
      *   cumulative_quantity to quantity.
      * Removes all the cancelled orders.
      */
    def readOrderHistory(config: Config): Map[String /* instrument */, List[home.StockActor.Order]] = {
        import io.Source
        import home.StockActor.Order

        // jv is a line in order-history.txt
        def convertJValueToStockActorOrder(jv: JValue): Option[Order] =  for {
            averagePrice       <- fromJValueToOption[Double](jv \ "average_price")
            createdAt          <- fromJValueToOption[String](jv \ "created_at")
            cumulativeQuantity <- fromJValueToOption[Double](jv \ "cumulative_quantity")
            id                 <- fromJValueToOption[String](jv \ "id")
            price              <- fromJValueToOption[Double](jv \ "price")
            quantity           <- fromJValueToOption[Double](jv \ "quantity")
            side               <- fromJValueToOption[String](jv \ "side")
            state              <- fromJValueToOption[String](jv \ "state")
            updatedAt          <- fromJValueToOption[String](jv \ "updated_at")
            if !cumulativeQuantity.isNaN || !quantity.isNaN
            if !averagePrice.isNaN || !price.isNaN
            if "confirmed" == state || "filled" == state || ("cancelled" == state && cumulativeQuantity > 0)
        } yield state match {
            case "confirmed" | "filled" =>
                Order(averagePrice, createdAt, cumulativeQuantity, id, price, quantity, side, state, updatedAt)
            // convert a cancelled order to a filled order if a part of the order was executed
            case "cancelled" => Order(averagePrice, createdAt, cumulativeQuantity, id, price, cumulativeQuantity, side,
                "filled", updatedAt)
        }

        Source.fromFile("order-history.txt").getLines()
                .map(jstring => {
                    val jv = parse(jstring)
                    (fromJValueToOption[String](jv \ "instrument"), convertJValueToStockActorOrder(jv))
                })
                .collect {
                    case (Some(instrument), Some(order)) => (instrument, order)
                }
                .toList
                .groupBy(_._1)
                .mapValues(tupleList => tupleList.map(_._2))
    }

    // StockDatabase must be populated first.
    def retrieveWatchedSymbols(config: Config): List[String /* symbol */] = {
        val SERVER = config.getString("server")
        implicit val backend: SttpBackend[Id, Nothing] = configureCoreJavaHttpBackend(config)
        sttp
                .auth.bearer(home.Main.accessToken)
                .get(uri"$SERVER/watchlists/Default/")
                .send()
                .body match {
            case Left(s) =>
                println(s"Error: unable to get the default watch list: $s")
                System.exit(-1)
                Nil
            case Right(jString) =>
                (parse(jString) \ "results").asInstanceOf[JArray].arr
                        .map(jv => fromJValueToOption[String](jv \ "instrument"))
                        .collect {
                            case Some(instrument) if StockDatabase.containsInstrument(instrument) => instrument
                            case Some(instrument) if !StockDatabase.containsInstrument(instrument) =>
                                println(
                                    s"""Error: StockDatabase doesn't have $instrument
                                       |  {
                                       |    instrument = ${instrument.replace("https://api.robinhood.com/instruments", "${I}")}
                                       |    name =
                                       |    simple_name =
                                       |  }
                                     """.stripMargin)
                                System.exit(-1)
                                "to make the compiler happy, the app will exit before reaching here"
                        }
                        .map(instrument => StockDatabase.getInstrumentFromInstrument(instrument).get.symbol)
        }
    }

    /**
      * Main.accessToken, StockDatabase and Main.watchedSymbols must be ready.
      */
    def writeOrderHistoryToFile(config: Config): Unit = {
        import java.nio.file._
        import java.nio.file.StandardOpenOption._
        import com.softwaremill.sttp.Uri._
        import org.json4s.native.Serialization

        implicit val backend: SttpBackend[Id, Nothing] = Util.configureCoreJavaHttpBackend(config)
        val path = Paths.get("order-history.txt")
        Files.deleteIfExists(path)
        val SERVER = config.getString("server")

        Main.watchedSymbols.foreach(symbol => {
            val instrument = StockDatabase.getInstrumentFromSymbol(symbol) match {
                case None =>
                    println(s"StockDatabase doesn't have $symbol")
                    System.exit(-1)
                    "to make the compiler happy"
                case Some(x) => x.instrument
            }
            var nextUri: Uri = uri"$SERVER/orders/"
                    .queryFragment(QueryFragment.KeyValue("instrument", instrument, valueEncoding = QueryFragmentEncoding.All))
            while (nextUri.host != "none") {
                val t1 = System.currentTimeMillis
                /*
                 * {
                 *   "previous": null,
                 *   "results": [
                 *     {
                 *       "updated_at": "2019-01-29T18:05:38.081543Z",
                 *       "ref_id": "9466c909-4345-4bd6-b03d-49f4293854fc",
                 *       "time_in_force": "gfd",
                 *       "fees": "0.02",
                 *       "cancel": null,
                 *       "response_category": "unknown",
                 *       "id": "56626c76-1495-48f6-92f6-e5c20ba71124",
                 *       "cumulative_quantity": "1.00000",
                 *       "stop_price": null,
                 *       "reject_reason": null,
                 *       "instrument": "https://api.robinhood.com/instruments/2bbdb493-dbb1-4e9c-ac98-6e7c93b117c0/",
                 *       "state": "filled",
                 *       "trigger": "immediate",
                 *       "override_dtbp_checks": false,
                 *       "type": "limit",
                 *       "last_transaction_at": "2019-01-29T18:05:37.866000Z",
                 *       "price": "11.22000000",
                 *       "executions": [
                 *         {
                 *           "timestamp": "2019-01-29T18:05:37.866000Z",
                 *           "price": "11.22000000",
                 *           "settlement_date": "2019-01-31",
                 *           "id": "e3ed82c1-cb0a-43a8-a394-c992d4224dcc",
                 *           "quantity": "1.00000"
                 *         }
                 *       ],
                 *       "extended_hours": false,
                 *       "account": "https://api.robinhood.com/accounts/5RY82436/",
                 *       "url": "https://api.robinhood.com/orders/56626c76-1495-48f6-92f6-e5c20ba71124/",
                 *       "created_at": "2019-01-29T17:47:33.022908Z",
                 *       "side": "sell",
                 *       "override_day_trade_checks": false,
                 *       "position": "https://api.robinhood.com/positions/5RY82436/2bbdb493-dbb1-4e9c-ac98-6e7c93b117c0/",
                 *       "average_price": "11.22000000",
                 *       "quantity": "1.00000"
                 *     },
                 *     {
                 *       "updated_at": "2019-01-04T14:33:30.411975Z",
                 *       "ref_id": "50070df0-75c7-42ff-8c59-e7b9e0f48952",
                 *       "time_in_force": "gfd",
                 *       "fees": "0.02",
                 *       "cancel": null,
                 *       "response_category": "unknown",
                 *       "id": "c7092a2f-47d2-4664-a587-25bb4924bef5",
                 *       "cumulative_quantity": "5.00000",
                 *       "stop_price": null,
                 *       "reject_reason": null,
                 *       "instrument": "https://api.robinhood.com/instruments/2bbdb493-dbb1-4e9c-ac98-6e7c93b117c0/",
                 *       "state": "filled",
                 *       "trigger": "immediate",
                 *       "override_dtbp_checks": false,
                 *       "type": "limit",
                 *       "last_transaction_at": "2019-01-04T14:33:30.122000Z",
                 *       "price": "13.93000000",
                 *       "executions": [
                 *         {
                 *           "timestamp": "2019-01-04T14:33:30.122000Z",
                 *           "price": "13.93000000",
                 *           "settlement_date": "2019-01-08",
                 *           "id": "3d1d634f-dc1a-468c-9c00-27e7c684e00c",
                 *           "quantity": "5.00000"
                 *         }
                 *       ],
                 *       "extended_hours": false,
                 *       "account": "https://api.robinhood.com/accounts/5RY82436/",
                 *       "url": "https://api.robinhood.com/orders/c7092a2f-47d2-4664-a587-25bb4924bef5/",
                 *       "created_at": "2019-01-04T14:33:03.209546Z",
                 *       "side": "sell",
                 *       "override_day_trade_checks": false,
                 *       "position": "https://api.robinhood.com/positions/5RY82436/2bbdb493-dbb1-4e9c-ac98-6e7c93b117c0/",
                 *       "average_price": "13.93000000",
                 *       "quantity": "5.00000"
                 *     }
                 *   ],
                 *   "next": "https://api.robinhood.com/orders/?cursor=cD0yMDE5LTAxLTA0KzE0JTNBMzMlM0EwMy4yMDk1NDYlMkIwMCUzQTAw"
                 * }
                 */
                sttp
                        .auth.bearer(home.Main.accessToken)
                        .get(nextUri)
                        .send()
                        .body match {
                    case Left(s) =>
                        println(s"Error: $s")
                        nextUri = Uri("none", None, "none", Some(4), Vector.empty, Vector.empty, None)
                    case Right(s) =>
                        val jValue: JValue = parse(s)
                        (jValue \ "results").asInstanceOf[JArray].arr foreach (jv => {
                            val jString = Serialization.write(jv
                                    .removeField(_._1 == "account")
                                    .removeField(_._1 == "url")
                                    .removeField(_._1 == "position")
                                    .removeField(_._1 == "ref_id")
                                    .removeField(_._1 == "cancel")
                                    .removeField(_._1 == "time_in_force")
                                    .removeField(_._1 == "stop_price")
                                    .removeField(_._1 == "trigger")
                                    .removeField(_._1 == "override_dtbp_checks")
                                    .removeField(_._1 == "override_day_trade_checks")
                                    .removeField(_._1 == "response_category")
                                    .removeField(_._1 == "reject_reason")
                                    .removeField(_._1 == "extended_hours")
                            )(DefaultFormats)
                            Files.writeString(path, jString + "\n", CREATE, APPEND)
                        })
                        val nextUrlOption = Util.fromJValueToOption[String](jValue \ "next")
                        nextUri = if (nextUrlOption.isDefined) uri"${nextUrlOption.get}" else
                            Uri("none", None, "none", Some(4), Vector.empty, Vector.empty, None)
                }
                val t2 = System.currentTimeMillis
                println(t2 - t1)
                Thread.sleep(2000)
            }
        })
    }

    /**
      * @param ja looks like this:
      * [
      *   {
      *     "begins_at": "2019-01-28T14:30:00Z",
      *     "close_price": "19.505000",
      *     "high_price": "19.560000",
      *     "interpolated": false,
      *     "low_price": "19.200000",
      *     "open_price": "19.200000",
      *     "session": "reg",
      *     "volume": 228090
      *   },
      *   {
      *     "begins_at": "2019-01-28T16:10:00Z",
      *     "close_price": "19.835000",
      *     "high_price": "19.865000",
      *     "interpolated": false,
      *     "low_price": "19.800000",
      *     "open_price": "19.820000",
      *     "session": "reg",
      *     "volume": 22993
      *   }
      * ]
      */
    private def getIntervalQuotesFromJArray(ja: JArray): List[QuoteActor.DailyQuote] = ja.arr
            .map(jv => for {
                beginsAt   <- fromJValueToOption[String](jv \ "begins_at")
                openPrice  <- fromJValueToOption[Double](jv \ "open_price")
                closePrice <- fromJValueToOption[Double](jv \ "close_price")
                highPrice  <- fromJValueToOption[Double](jv \ "high_price")
                lowPrice   <- fromJValueToOption[Double](jv \ "low_price")
                volume     <- fromJValueToOption[Long](jv \ "volume")
            } yield QuoteActor.DailyQuote(beginsAt, openPrice, closePrice, highPrice, lowPrice, volume))
            .collect { case Some(dailyQuote) => dailyQuote }

}
