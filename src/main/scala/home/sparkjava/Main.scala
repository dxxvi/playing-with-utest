package home.sparkjava

import java.nio.file.{Files, Path}
import java.time.LocalDateTime

import akka.actor.ActorSystem
import com.softwaremill.sttp._
import com.typesafe.config.{Config, ConfigFactory}
import home.sparkjava.model.DailyQuote
import org.json4s._
import org.json4s.native.JsonMethods._
import spark.{Request, Response, Spark}

import scala.collection.JavaConverters._
import scala.collection.concurrent.TrieMap
import scala.concurrent.duration._
import scala.io.StdIn
import scala.util.Try

object Main extends Util {
    var accessToken: String = "_"
    var dowFuture: Int = 0
    val instrument2Symbol: TrieMap[String, String] = TrieMap() // has instruments in default watch list only
    // instrument2NameSymbol has all instruments I ever need
    val instrument2NameSymbol: TrieMap[String, (String /* name */, String /* symbol */)] = TrieMap()

    // compare 2 timestamps of the format yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'. The '.SSSSSS' is optional.
    val timestampOrdering: Ordering[String] = Ordering.comparatorToOrdering[String]((a: String, b: String) => {
        val aa = a.split("[-T:Z]")
        val bb = b.split("[-T:Z]")
        (aa.iterator zip bb.iterator) // reason to use iterator: to make it lazy
                .map { case (a0, b0) => a0.toDouble.compareTo(b0.toDouble) }
                .find(_ != 0)
                .getOrElse(0)
    })

    val dowStocks: collection.mutable.Set[String] = collection.mutable.HashSet[String]()

    def buildStocksDB(config: Config) {
        dowStocks ++= config.getConfig("dow").root().keySet().asScala

        def f(a: String, b: String): String =
            if (a == "") b
            else if (b == "") a
            else if (a.length < b.length) a
            else b

        instrument2NameSymbol ++= config.getConfig("dow").root().keySet().asScala
                .map(symbol => (
                        config.getString(s"dow.$symbol.instrument"),
                        (f(config.getString(s"dow.$symbol.name"), config.getString(s"dow.$symbol.simple_name")), symbol)
                ))
        val conf = ConfigFactory.load("stock.conf")
        instrument2NameSymbol ++= conf.getConfig("soi").root().keySet().asScala
                .map(symbol => (
                        conf.getString(s"soi.$symbol.instrument"),
                        (f(conf.getString(s"soi.$symbol.name"), conf.getString(s"soi.$symbol.simple_name")), symbol)
                ))
    }

    private def readOrderHistoryAndCreateStockActors(config: Config, actorSystem: ActorSystem) {
        import model.{Position, Quote}
        import scala.concurrent.Await
        import scala.io.Source
        import actorSystem.dispatcher

        val instrument2OrderElementList = Source.fromFile("order-history.txt").getLines()
                .map(jstring => {
                    val jv = parse(jstring)
                    (fromJValueToOption[String](jv \ "instrument"), model.Orders.toOrderElement(jv))
                })
                .collect {
                    case (Some(instrument), Some(order)) => (instrument, order)
                }
                .toList
                .groupBy(_._1)
                .mapValues(tupleList => tupleList.map(_._2))

        val symbol2Quote = Quote.deserialize(Await.result(getLastTradePrices(config), 2.seconds))
                .groupBy(_.symbol)
                .mapValues(_.head)

        var t: (String, String) = Await.result(getDailyQuotes(config), 3.seconds)
        val symbol2DailyQuoteList = DailyQuote.deserialize(t._1) ++ DailyQuote.deserialize(t._2)

        t = Await.result(get5minQuotes(config), 3.seconds)
        val symbol25minQuoteList: Map[String, List[DailyQuote]] = DailyQuote.deserialize(t._1) ++ DailyQuote.deserialize(t._2)
        val symbol2OpenHighLow: Map[String, (Double, Double, Double)] = calculateOpenHighLow(symbol25minQuoteList)

        val instrument2Position: Map[String, Position] = Position
                .deserialize(Await.result(getPositions(config), 3.seconds))
                .filter(p => p.quantity > 0)
                .map(p => (p.instrument, p))
                .toMap

        for ((instrument, symbol) <- instrument2Symbol) {
            val q = symbol2Quote.get(symbol)
            if (q.isEmpty) {
                println(s"No quote for $symbol")
                System.exit(-1)
            }
            val dQuotes = symbol2DailyQuoteList.get(symbol)
            if (dQuotes.isEmpty || dQuotes.get.isEmpty) {
                println(s"No daily quotes for $symbol")
                System.exit(-1)
            }

            val p = instrument2Position.getOrElse(instrument, Position(
                None, None, None, None, None, None, None, None, None, instrument, None, None, None, 0
            ))

            val openHighLow: (Double, Double, Double) = symbol2OpenHighLow.getOrElse(symbol, {
                println(s"Error: unable to find open high low for $symbol")
                System.exit(-1)
                (Double.NaN, Double.NaN, Double.NaN) // to make the compiler happy
            })
            if (openHighLow._1.isNaN || openHighLow._2.isNaN || openHighLow._3.isNaN) {
                println(s"Error: one of open high low for $symbol is Double.NaN")
                System.exit(-1)
            }

            actorSystem.actorOf(
                StockActor.props(symbol, config, instrument2OrderElementList.getOrElse(instrument, Nil), q.get,
                    dQuotes.get, p, openHighLow._1, openHighLow._2, openHighLow._3),
                s"symbol-$symbol"
            )
        }
    }

    private def writeOrderHistoryToFile(config: Config, actorSystem: ActorSystem) {
        import actorSystem.dispatcher

        val path = Path.of("order-history.txt")
        if (!Files.exists(path)) {
            import java.nio.file._
            import java.nio.file.StandardOpenOption._
            import scala.concurrent.Await
            import com.softwaremill.sttp.Uri._
            import org.json4s.native.Serialization
            import home.sparkjava.model.Position

            implicit val backend: SttpBackend[Id, Nothing] = configureCoreJavaHttpBackend(config)
            val SERVER = config.getString("server")

            val instrument2Position: Map[String, Position] = Position
                    .deserialize(Await.result(getPositions(config), 3.seconds))
                    .filter(p => p.quantity > 0)
                    .map(p => (p.instrument, p))
                    .toMap

            instrument2Symbol.keySet.intersect(instrument2Position.keySet).foreach(instrument => {
                var nextUri: Uri = uri"${SERVER}orders/"
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
                            .auth.bearer(accessToken)
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
                                        .removeField(_._1 == "extended_hours")
                                )(DefaultFormats)
                                Files.writeString(path, jString + "\n", CREATE, APPEND)
                            })
                            val nextUrlOption = fromJValueToOption[String](jValue \ "next")
                            nextUri = if (nextUrlOption.isDefined) uri"${nextUrlOption.get}" else
                                Uri("none", None, "none", Some(4), Vector.empty, Vector.empty, None)
                    }
                    val t2 = System.currentTimeMillis
                    println(t2 - t1)
                    Thread.sleep(1789)
                }
            })
            println(s"order-history.txt is just created. Please run the app again.")
            System.exit(0)
        }
    }

    def main(args: Array[String]): Unit = {
        val config: Config = ConfigFactory.load()

        accessToken = config.getString("accessToken")

        buildStocksDB(config)

        val actorSystem = ActorSystem("R")

        /**
          * @param s looks like default-watch-list.json
          */
        def extractInstruments(s: String): List[String] = {
            import org.json4s._
            import org.json4s.native.JsonMethods._
            parse(s).asInstanceOf[JObject].values.get("results").fold({
                println(s"Error: no field 'results' in $s")
                System.exit(-1)
                List[String]()
            }) {
                case results: List[Map[String, _]] =>
                    results.map(m => m.get("instrument")).collect { case Some(x) => x.asInstanceOf[String] }
                case x =>
                    println(s"Error: unexpected field 'results' type $x")
                    System.exit(-1)
                    List[String]()
            }
        }

        val SERVER: String = config.getString("server")
        implicit val httpBackend: SttpBackend[Id, Nothing] = configureCoreJavaHttpBackend(config)
        sttp
                .auth.bearer(accessToken)
                .get(uri"${SERVER}watchlists/Default/")
                .send()
                .body match {
            case Left(s) =>
                println(s"Error in getting default watch list: $s")
                System.exit(-1)
            case Right(s) =>
                extractInstruments(s).foreach(instrument => {
                    val o: Option[(String, String)] = instrument2NameSymbol.get(instrument)
                    if (o.isEmpty) {
                        println(s"Error: instrument2NameSymbol doesn't have $instrument")
                        System.exit(-1)
                    }
                    val symbol = o.get._2
                    instrument2Symbol += ((instrument, symbol))
                })
        }

        writeOrderHistoryToFile(config, actorSystem)
        readOrderHistoryAndCreateStockActors(config, actorSystem)

        val webSocketListener = initializeSpark(actorSystem)
        actorSystem.actorOf(WebSocketActor.props(webSocketListener), WebSocketActor.NAME)
        actorSystem.actorOf(QuoteActor.props(config), QuoteActor.NAME)
        actorSystem.actorOf(OrderActor.props(config), OrderActor.NAME)

        StdIn.readLine()
        Spark.stop()
        actorSystem.terminate()
    }

    private def initializeSpark(actorSystem: ActorSystem): WebSocketListener = {
        import scala.concurrent._
        import akka.pattern.ask
        import akka.util.Timeout

        implicit val timeout: Timeout = Timeout(5.seconds)
        val webSocketListener = new WebSocketListener(actorSystem)
        Spark.staticFiles.location("/static")
        Spark.webSocket("/ws", webSocketListener)

        Spark.get("/dow/:dowFuture", (request: Request, response: Response) => {
            Main.dowFuture = Try(request.params(":dowFuture").toInt).getOrElse(0)
            s"DOW future is set to ${Main.dowFuture}"
        })

        Spark.get("/dow", (request: Request, response: Response) => {
            s"Dow future is ${Main.dowFuture}"
        })

        Spark.get("/debug/:symbol", (req: Request, res: Response) => {
            def toJson(map: Map[String, String]): String = {
                import org.json4s.DefaultFormats
                import org.json4s.JsonAST.{JField, JObject, JString}
                import org.json4s.native.Serialization
                val jFields: List[JField] = map.map(t => (t._1, JString(t._2))).toList
                Serialization.write(JObject(jFields))(DefaultFormats)
            }

            res.`type`("application/json")
            val symbol = req.params(":symbol")
            val mapFuture: Future[Any] = actorSystem.actorSelection(s"/user/symbol-$symbol") ? "DEBUG"
            mapFuture match {
                case x: Future[_] => toJson(Await.result(x, 3.seconds).asInstanceOf[Map[String, String]])
                case _ => toJson(Map("error" -> "Hm... Sun Feb 10 02:12"))
            }
        })

        Spark.init()                   // Needed if no HTTP route is defined after the WebSocket routes
        webSocketListener
    }

    def calculateShortDuration(): FiniteDuration = {
        val hour = LocalDateTime.now.getHour
        if (hour < 9 || hour > 15) 29.seconds else 4.seconds
    }
}

