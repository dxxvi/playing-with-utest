package home

import akka.event.LoggingAdapter
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.softwaremill.sttp._
import home.util.JsonUtil
import org.json4s._
import org.json4s.native.JsonMethods._

import scala.concurrent.{ExecutionContext, Future}

trait PositionUtil extends JsonUtil with AppUtil {
    /**
      * @return Future("940fc3f5-1db5-4fed-b452-f3a2e4562b5f" -> 0, "5e0dcc39-6692-4126-8741-e6e26f13672b" -> 6, ...)
      */
    def getAllPositions(accessToken: String)
                       (implicit be: SttpBackend[Future, Source[ByteString, Any]],
                                 ec: ExecutionContext,
                                 log: LoggingAdapter):
    Future[Map[String /*instrument*/, (Double /*quantity*/, String /*account*/)]] =
        sttp
                .auth.bearer(accessToken)
                .get(uri"https://api.robinhood.com/positions/?nonzero=true")
                .response(asString)
                .send()
                .collect {
                    case Response(Left(_), _, statusText, _, _) =>
                        log.error("/positions/ returns {}", statusText)
                        """{"next": null, "previous": null, "results": []}"""
                    case Response(Right(s), _, _, _, _) =>
                        if (fromJValueToOption[String](parse(s) \ "next").nonEmpty)
                            log.warning("There's a next url in /positions/ return")
                        s
                }
                .map(extractInstrumentAndPosition)

    /**
      * @param js looks like this
      *         {
      *           "next": null,      // ignore this for now
      *           "previous": null,
      *           "results": [
      *             {
      *               "account": "https://api.robinhood.com/accounts/5RY82436/",
      *               "average_buy_price": "27.7120",
      *               "created_at": "2017-02-16T20:59:19.153162Z",
      *               "instrument": "https://api.robinhood.com/instruments/940fc3f5-1db5-4fed-b452-f3a2e4562b5f/",
      *               "intraday_average_buy_price": "0.0000",
      *               "intraday_quantity": "0.0000",
      *               "pending_average_buy_price": "27.7120",
      *               "quantity": "3.0000",
      *               "shares_held_for_buys": "1.0000",
      *               "shares_held_for_options_collateral": "0.0000",
      *               "shares_held_for_options_events": "0.0000",
      *               "shares_held_for_sells": "0.0000",
      *               "shares_held_for_stock_grants": "0.0000",
      *               "shares_pending_from_options_events": "0.0000",
      *               "updated_at": "2019-04-11T13:39:59.143762Z",
      *               "url": "https://api.robinhood.com/positions/5RY82436/940fc3f5-1db5-4fed-b452-f3a2e4562b5f/"
      *             },
      *             {
      *               "shares_held_for_stock_grants": "0.0000",
      *               "account": "https://api.robinhood.com/accounts/5RY82436/",
      *               "pending_average_buy_price": "13.5292",
      *               "shares_held_for_options_events": "0.0000",
      *               "intraday_average_buy_price": "0.0000",
      *               "url": "https://api.robinhood.com/positions/5RY82436/5e0dcc39-6692-4126-8741-e6e26f13672b/",
      *               "shares_held_for_options_collateral": "0.0000",
      *               "created_at": "2018-12-17T14:18:17.268914Z",
      *               "updated_at": "2018-12-19T19:42:41.154695Z",
      *               "shares_held_for_buys": "0.0000",
      *               "average_buy_price": "13.5292",
      *               "instrument": "https://api.robinhood.com/instruments/5e0dcc39-6692-4126-8741-e6e26f13672b/",
      *               "intraday_quantity": "0.0000",
      *               "shares_held_for_sells": "0.0000",
      *               "shares_pending_from_options_events": "0.0000",
      *               "quantity": "6.0000"
      *             }
      *           ]
      *         }
      * @return "940fc3f5-1db5-4fed-b452-f3a2e4562b5f" -> 3, "5e0dcc39-6692-4126-8741-e6e26f13672b" -> 6
      */
    private def extractInstrumentAndPosition(js: String)(implicit log: LoggingAdapter): Map[String, (Double, String)] =
        parse(js) \ "results" match {
            case JArray(arr) => arr
                    .map(jv =>
                        for {
                            instrument <- fromJValueToOption[String](jv \ "instrument")
                            quantity   <- fromJValueToOption[Double](jv \ "quantity")
                            if !quantity.isNaN
                            account    <- fromJValueToOption[String](jv \ "account")
                        } yield (extractInstrument(instrument), (quantity, account))
                    )
                    .collect { case Some(tuple) => tuple }
                    .toMap
            case _ =>
                log.error("No results field in {}", js)
                Map.empty[String, (Double, String)]
        }

}
