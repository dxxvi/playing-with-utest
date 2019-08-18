package home

import akka.event.LoggingAdapter
import akka.stream.scaladsl.Source
import akka.util.ByteString
import home.model.LastTradePrice
import com.softwaremill.sttp._
import org.json4s._
import org.json4s.native.JsonMethods._

import scala.concurrent.{ExecutionContext, Future}

trait LastTradePriceUtil extends home.util.JsonUtil {
    def getLastTradePrices(accessToken: String, symbols: List[String])
                          (implicit be: SttpBackend[Future, Source[ByteString, Any]],
                           ec: ExecutionContext,
                           log: LoggingAdapter): Future[List[LastTradePrice]] = {
        sttp
                .auth.bearer(accessToken)
                .get(uri"https://api.robinhood.com/quotes/?symbols=${symbols.mkString(",")}")
                .response(asString)
                .send()
                .collect {
                    case Response(Left(_), _, statusText, _, _) =>
                        log.error("The /quotes/ endpoint returns {}", statusText)
                        """{"results": []"""
                    case Response(Right(js), _, _, _, _) => js
                }
                .map(getLastTradePrices)
    }

    /**
     * @param js looks like this
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
    private def getLastTradePrices(js: String)
                                  (implicit log: LoggingAdapter): List[LastTradePrice] = {
        parse(js) \ "results" match {
            case x: JArray => x.arr
                    .map(jv =>
                        for {
                            symbol         <- fromJValueToOption[String](jv \ "symbol")
                            instrument     <- fromJValueToOption[String](jv \ "instrument")
                            lastTradePrice <- fromJValueToOption[Double](jv \ "last_trade_price")
                            previousClose  <- fromJValueToOption[Double](jv \ "previous_close")
                            updatedAt      <- fromJValueToOption[String](jv \ "updated_at")
                        } yield LastTradePrice(
                            lastTradePrice,
                            previousClose,
                            symbol,
                            instrument.replaceAll("^https://api.robinhood.com/instruments/", "")
                                    .replaceAll("/$", ""),
                            updatedAt
                        )
                    )
                    .collect { case Some(lastTradePrice) => lastTradePrice }
            case _ =>
                log.error("{} doesn't look like a /quotes/?symbols=... return", js)
                List.empty[LastTradePrice]
        }
    }
}