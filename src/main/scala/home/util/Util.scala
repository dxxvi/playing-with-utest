package home.util

object Util extends SttpBackends {
    import scala.reflect.runtime.universe._
    import scala.util.Success
    import scala.util.Try
    import com.typesafe.config.Config
    import org.json4s._
    import home.QuoteActor.DailyQuote

    def getSymbolFromInstrumentHttpURLConnection(url: String, config: Config): String = {
        import com.softwaremill.sttp._
        import org.json4s.native.JsonMethods._

        implicit val backend: SttpBackend[Id, Nothing] = configureCoreJavaHttpBackend(config)
        sttp.get(uri"$url").send().body match {
            case Right(jString) => (parse(jString) \ "symbol").asInstanceOf[JString].values
            case Left(s) => throw new RuntimeException(s)
        }
    }

    def getDailyQuoteHttpURLConnection(symbols: collection.Set[String], config: Config): List[(String, List[DailyQuote])] = {
        import com.softwaremill.sttp._
        import org.json4s.native.JsonMethods._

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
        def getDailyQuotesFromJArray(ja: JArray): List[DailyQuote] = ja.arr
                .map(jv => for {
                    beginsAt <- fromJValueToOption[String](jv \ "begins_at")
                    openPrice <- fromJValueToOption[Double](jv \ "open_price")
                    closePrice <- fromJValueToOption[Double](jv \ "close_price")
                    highPrice <- fromJValueToOption[Double](jv \ "high_price")
                    lowPrice <- fromJValueToOption[Double](jv \ "low_price")
                } yield DailyQuote(beginsAt, openPrice, closePrice, highPrice, lowPrice))
                .collect {
                    case Some(dailyQuote) => dailyQuote
                }

        sttp.get(uri"$SERVER/quotes/historicals/?interval=day&span=year&symbols=${symbols.mkString(",")}")
                .send()
                .body match {
                    case Right(js) =>
                        (parse(js) \ "results").asInstanceOf[JArray].arr
                                .map(jv => for {
                                    symbol <- fromJValueToOption[String](jv \ "symbol")
                                    dailyQuoteList = getDailyQuotesFromJArray((jv \ "historicals").asInstanceOf[JArray])
                                } yield (symbol, dailyQuoteList))
                                .collect {
                                    case Some(tuple) => tuple
                                }
                    case Left(s) => throw new RuntimeException(
                        s"$SERVER/quotes/historicals/?interval=day&span=year&symbols=... " + s)
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
            case _ => None.asInstanceOf[Option[T]]
        }
        case JLong(num) => typeOf[T] match {
            case t if t =:= typeOf[Int] => Some(num.toInt).asInstanceOf[Option[T]]
            case t if t =:= typeOf[Long] => Some(num.toLong).asInstanceOf[Option[T]]
            case _ => None.asInstanceOf[Option[T]]
        }
        case JBool(bool) => typeOf[T] match {
            case t if t =:= typeOf[Boolean] => Some(bool).asInstanceOf[Option[T]]
            case _ => None.asInstanceOf[Option[T]]
        }
        case _ => None
    }
}
