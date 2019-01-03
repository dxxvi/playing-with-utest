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
        case JNull => typeOf[T] match {
            case t if t =:= typeOf[Double] => Some(Double.NaN).asInstanceOf[Option[T]]
            case _ => None.asInstanceOf[Option[T]]
        }
        case _ => None
    }

    /**
      * This method is here for easy testing.
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
    def extractSymbolAndOrder(s: String): List[(String, home.StockActor.Order)] = {
        import org.json4s._
        import org.json4s.native.JsonMethods._

        (parse(s) \ "results").asInstanceOf[JArray].arr.map(jv => for {
            instrument <- Util.fromJValueToOption[String](jv \ "instrument")
            symbol <- StockDatabase.getInstrumentFromInstrument(instrument).map(_.symbol)
            averagePrice <- Util.fromJValueToOption[Double](jv \ "average_price")
            createdAt <- Util.fromJValueToOption[String](jv \ "created_at")
            cumulativeQuantity <- Util.fromJValueToOption[Double](jv \ "cumulative_quantity")
            id <- Util.fromJValueToOption[String](jv \ "id")
            price <- Util.fromJValueToOption[Double](jv \ "price")
            quantity <- Util.fromJValueToOption[Double](jv \ "quantity")
            side <- Util.fromJValueToOption[String](jv \ "side")
            state <- Util.fromJValueToOption[String](jv \ "state")
            updatedAt <- Util.fromJValueToOption[String](jv \ "updated_at")
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
        }
    }

    /**
      * This method is here for easy testing.
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
    def extractSymbolAndPosition(js: String): List[(String, Double)] = {
        import org.json4s._
        import org.json4s.native.JsonMethods._

        (parse(js) \ "results").asInstanceOf[JArray].arr
                .map(jv => for {
                    instrument <- fromJValueToOption[String](jv \ "instrument")
                    symbol <- StockDatabase.getInstrumentFromInstrument(instrument).map(_.symbol)
                    quantity <- fromJValueToOption[Double](jv \ "quantity")
                    if !quantity.isNaN
                } yield (symbol, quantity))
                .collect {
                    case Some(tuple) => tuple
                }
    }
}
