package home

import akka.actor.Actor
import com.softwaremill.sttp._
import com.typesafe.config.Config
import home.util.{StockDatabase, SttpBackends, TimersX}

object OrderActor {
    import akka.actor.Props

    val NAME: String = "order"

    sealed trait OrderSealedTrait
    case object Tick extends OrderSealedTrait
    case object Debug extends OrderSealedTrait
    case class Responses(recentOrdersResponse: Response[String], positionResponse: Response[String])
            extends OrderSealedTrait

    private case class Order(
                            averagePrice: Double,
                            createdAt: String,
                            cumulativeQuantity: Double,
                            id: String,
                            price: Double,
                            quantity: Double,
                            side: String,
                            state: String,
                            updatedAt: String
                    )

    def props(config: Config): Props = Props(new OrderActor(config))
}

class OrderActor(config: Config) extends Actor with TimersX with SttpBackends {
    import OrderActor._
    import context.dispatcher
    import concurrent.Future
    import concurrent.duration._
    import akka.actor.ActorSelection
    import akka.event._
    import akka.pattern.pipe
    import akka.stream.scaladsl.Source
    import akka.util.ByteString
    import home.util.Util

    implicit val logSource: LogSource[AnyRef] = (_: AnyRef) => NAME
    val log: LoggingAdapter = Logging(context.system, this)

    timersx.startPeriodicTimer(Tick, Tick, 4019.millis)

    override def receive: Receive = {
        case Tick =>
            Util.retrievePositionAndRecentOrdersResponseFuture(config) pipeTo self

        case Responses(
            Response(recentOrdersBody, _, statusText1, _, _),
            Response(positionBody, _, statusText2, _, _)
        ) =>
            val x: Either[Array[Byte], (String, String)] = for {
                a <- recentOrdersBody
                b <- positionBody
            } yield (a, b)
            x match {
                case Left(_) => log.error("Something wrong: recent orders request: {}, position request: {}",
                    statusText1, statusText2)
                /**
                  * recentOrdersJString is like this
                  * {
                  *   "previous": null,
                  *   "results": [
                  *     {
                  *       "updated_at": "2018-12-14T20:24:30.223556Z",
                  *       "ref_id": null,
                  *       "time_in_force": "gfd",
                  *       "fees": "0.00",
                  *       "cancel": null,
                  *       "response_category": "unknown",
                  *       "id": "c31b57e9-4094-4361-a8fc-5c8e76b488ba",
                  *       "cumulative_quantity": "10.00000",
                  *       "stop_price": null,
                  *       "reject_reason": null,
                  *       "instrument": "https://api.robinhood.com/instruments/cfa64e84-2864-45e7-aac9-fd02e7d1e369/",
                  *       "state": "filled",
                  *       "trigger": "immediate",
                  *       "override_dtbp_checks": false,
                  *       "type": "limit",
                  *       "last_transaction_at": "2018-12-14T20:24:29.995000Z",
                  *       "price": "12.84000000",
                  *       "executions": [
                  *         {
                  *           "timestamp": "2018-12-14T20:24:29.986000Z",
                  *           "price": "12.84000000",
                  *           "settlement_date": "2018-12-18",
                  *           "id": "b8e7b30a-0752-48b0-a4f6-45d4937ffbbe",
                  *           "quantity": "1.00000"
                  *         },
                  *         {
                  *           "timestamp": "2018-12-14T20:24:29.995000Z",
                  *           "price": "12.84000000",
                  *           "settlement_date": "2018-12-18",
                  *           "id": "ddbea042-b328-4785-9d90-87b4ff767c78",
                  *           "quantity": "9.00000"
                  *         }
                  *       ],
                  *       "extended_hours": false,
                  *       "account": "https://api.robinhood.com/accounts/5RY82436/",
                  *       "url": "https://api.robinhood.com/orders/c31b57e9-4094-4361-a8fc-5c8e76b488ba/",
                  *       "created_at": "2018-12-14T20:24:26.841574Z",
                  *       "side": "buy",
                  *       "override_day_trade_checks": false,
                  *       "position": "https://api.robinhood.com/positions/5RY82436/cfa64e84-2864-45e7-aac9-fd02e7d1e369/",
                  *       "average_price": "12.84000000",
                  *       "quantity": "10.00000"
                  *     },
                  *     {
                  *       "updated_at": "2018-07-27T23:18:44.134967Z",
                  *       "ref_id": null,
                  *       "time_in_force": "gfd",
                  *       "fees": "0.02",
                  *       "cancel": null,
                  *       "response_category": "unknown",
                  *       "id": "f3569b6e-e3a2-497a-9f5f-5555c07b4bb8",
                  *       "cumulative_quantity": "8.00000",
                  *       "stop_price": null,
                  *       "reject_reason": null,
                  *       "instrument": "https://api.robinhood.com/instruments/cfa64e84-2864-45e7-aac9-fd02e7d1e369/",
                  *       "state": "filled",
                  *       "trigger": "immediate",
                  *       "override_dtbp_checks": false,
                  *       "type": "limit",
                  *       "last_transaction_at": "2018-07-27T14:24:57.707000Z",
                  *       "price": "18.31000000",
                  *       "executions": [
                  *         {
                  *           "timestamp": "2018-07-27T14:24:57.707000Z",
                  *           "price": "18.31000000",
                  *           "settlement_date": "2018-07-31",
                  *           "id": "192db9ac-487a-4ba8-9626-1eb3837e3270",
                  *           "quantity": "8.00000"
                  *         }
                  *       ],
                  *       "extended_hours": false,
                  *       "account": "https://api.robinhood.com/accounts/5RY82436/",
                  *       "url": "https://api.robinhood.com/orders/f3569b6e-e3a2-497a-9f5f-5555c07b4bb8/",
                  *       "created_at": "2018-07-27T14:11:40.076797Z",
                  *       "side": "sell",
                  *       "override_day_trade_checks": false,
                  *       "position": "https://api.robinhood.com/positions/5RY82436/cfa64e84-2864-45e7-aac9-fd02e7d1e369/",
                  *       "average_price": "18.31000000",
                  *       "quantity": "8.00000"
                  *     }
                  *   ],
                  *   "next": "https://api.robinhood.com/orders/?cursor=cD0yMDE4LTA3LTI3KzE0JTNBMTElM0E0MC4wNzY3OTclMkIwMCUzQTAw&instrument=https%3A%2F%2Fapi.robinhood.com%2Finstruments%2Fcfa64e84-2864-45e7-aac9-fd02e7d1e369%2F"
                  * }
                  */
                /**
                  * positionJString is like this
                  * {
                  *   "previous": null,
                  *   "results": [
                  *     {
                  *       "shares_held_for_stock_grants": "0.0000",
                  *       "account": "https://api.robinhood.com/accounts/5RY82436/",
                  *       "pending_average_buy_price": "17.9902",
                  *       "shares_held_for_options_events": "0.0000",
                  *       "intraday_average_buy_price": "0.0000",
                  *       "url": "https://api.robinhood.com/positions/5RY82436/dad8fa2c-1e8d-4cb9-b354-1f0b91a4193e/",
                  *       "shares_held_for_options_collateral": "0.0000",
                  *       "created_at": "2017-03-13T12:48:46.038376Z",
                  *       "updated_at": "2019-01-24T16:28:07.287290Z",
                  *       "shares_held_for_buys": "0.0000",
                  *       "average_buy_price": "17.9902",
                  *       "instrument": "https://api.robinhood.com/instruments/dad8fa2c-1e8d-4cb9-b354-1f0b91a4193e/",
                  *       "intraday_quantity": "0.0000",
                  *       "shares_held_for_sells": "0.0000",
                  *       "shares_pending_from_options_events": "0.0000",
                  *       "quantity": "1.0000"
                  *     },
                  *     {
                  *       "shares_held_for_stock_grants": "0.0000",
                  *       "account": "https://api.robinhood.com/accounts/5RY82436/",
                  *       "pending_average_buy_price": "0.0000",
                  *       "shares_held_for_options_events": "0.0000",
                  *       "intraday_average_buy_price": "0.0000",
                  *       "url": "https://api.robinhood.com/positions/5RY82436/5dbe8ac1-abd8-44bd-bbdb-c1cd899271ff/",
                  *       "shares_held_for_options_collateral": "0.0000",
                  *       "created_at": "2017-03-21T05:03:00.582149Z",
                  *       "updated_at": "2018-12-18T17:05:58.982270Z",
                  *       "shares_held_for_buys": "0.0000",
                  *       "average_buy_price": "0.0000",
                  *       "instrument": "https://api.robinhood.com/instruments/5dbe8ac1-abd8-44bd-bbdb-c1cd899271ff/",
                  *       "intraday_quantity": "0.0000",
                  *       "shares_held_for_sells": "0.0000",
                  *       "shares_pending_from_options_events": "0.0000",
                  *       "quantity": "0.0000"
                  *     }
                  *   ],
                  *   "next": null
                  * }
                  */
                case Right((recentOrdersJString, positionJString)) =>
                    val symbol2OrderList: Map[String, List[StockActor.Order]] = Util.extractSymbolAndOrder(recentOrdersJString)
                    // only symbols in the default watch list are here
                    val symbol2Position: Map[String, Double] = Util.extractSymbolAndPosition(positionJString)
                    symbol2Position.foreach(t => {
                        val stockActor = context.actorSelection(s"/user/${t._1}")
                        stockActor ! StockActor.PositionAndOrderList(t._2, symbol2OrderList.getOrElse(t._1, Nil))
                    })
            }

        case Debug =>
            val map = debug()
            sender() ! map
    }

    private def debug(): Map[String, String] = {
        log.info(s"$NAME debug information:")
        Map("message" -> "Nothing in OrderActor to show")
    }
}
