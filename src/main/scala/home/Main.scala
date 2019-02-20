package home

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

import akka.actor.{ActorSystem, TimerScheduler}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.pattern.ask
import akka.stream.ActorMaterializer
import com.typesafe.config.{Config, ConfigFactory}
import home.util.{StockDatabase, SttpBackends, Util}
import spark.Spark

import scala.concurrent.{Await, ExecutionContext, ExecutionContextExecutor}
import scala.concurrent.duration._
import scala.io.StdIn
import scala.util.{Failure, Success, Try}

/**
  * To run this class, -Dauthorization.username, -Dauthorization.encryptedPassword are needed.
  * The environment variable 'key' is needed.
  */
object Main extends SttpBackends {
    var accessToken: String = "_"
    var watchedSymbols: List[String] = Nil

    val timerScheduler: TimerScheduler = new TimerScheduler {
        private val MESSAGE: String = "This is TimersX doing nothing"
        private var lastAccessTime: Long = 0

        override def startPeriodicTimer(key: Any, msg: Any, interval: FiniteDuration): Unit = print()

        override def startSingleTimer(key: Any, msg: Any, timeout: FiniteDuration): Unit = print()

        override def isTimerActive(key: Any): Boolean = true

        override def cancel(key: Any): Unit = print()

        override def cancelAll(): Unit = print()

        private def print(): Unit = {
            val now = System.currentTimeMillis
            if (now - lastAccessTime > 19482) {
                println(s"$MESSAGE now: $now lastAccessTime: $lastAccessTime")
                lastAccessTime = now
            }
        }
    }

    def main(args: Array[String]) {
        val config: Config = ConfigFactory.load()

        addStocksToDatabase(config)

        Util.retrieveAccessToken(config) match {
            case Right(x) => accessToken = x
            case Left(errorMessage) =>
                println(s"Error: $errorMessage")
                System.exit(-1)
        }

        implicit val actorSystem: ActorSystem = ActorSystem("R")
        implicit val actorMaterializer: ActorMaterializer = ActorMaterializer()
        implicit val ec: ExecutionContext = actorSystem.dispatcher

        watchedSymbols = Util.retrieveWatchedSymbols(config)

        val websocketListener: WebsocketListener = initializeSpark(actorSystem)

        if (config.hasPath("akkaTimers")) {
            Util.writeOrderHistoryToFile(config)
            println(s"Everything is good. Run this app again without -DakkaTimers")
            System.exit(0)
        }
        /*
         * The orderHistory has filled and confirmed orders only. All cancelled orders with cumulative_quantity > 0 are
         * converted to filled orders with the quantity adjusted.
         */
        val symbol2OrderHistory: Map[String, List[StockActor.Order]] = Util.readOrderHistory(config).map(t =>
            StockDatabase.getInstrumentFromInstrument(t._1) match {
                case Some(instrument) => (instrument.symbol, t._2)
                case _ =>
                    println(s"""...  {
                           |    instrument = ${t._1}
                           |    name = "..."
                           |    simple_name = "to make Util.readOrderHistory happy"
                           |  }""".stripMargin)
                    System.exit(-1)
                    ("to make the compiler happy", t._2)
            }
        )

        val r: OrderActor.Responses = Await.result(Util.retrievePositionAndRecentOrdersResponseFuture(config), 3.seconds)
        val either: Either[Array[Byte], (String, String)] = for {
            a <- r.recentOrdersResponse.rawErrorBody
            b <- r.positionResponse.rawErrorBody
        } yield (a, b)
        val positionAndRecentOrderTuple: (Map[String, List[StockActor.Order]], Map[String, Double]) = either match {
            case Left(_) =>
                println(s"Error: recent order response: ${r.recentOrdersResponse.statusText}, " +
                        s"position response: ${r.positionResponse.statusText}")
                System.exit(-1)
                (Map.empty[String, List[StockActor.Order]], Map.empty[String, Double]) // to make the compiler happy
            case Right((recentOrdersJString, positionJString)) =>
                val symbol2OrderList: Map[String, List[StockActor.Order]] = Util.extractSymbolAndOrder(recentOrdersJString)
                // only symbols in the default watch list are here
                val symbol2Position: Map[String, Double] = Util.extractSymbolAndPosition(positionJString)
                (symbol2OrderList, symbol2Position)
        }

        val jString = Await.result(Util.getLastTradePrices(config), 3.seconds)
        if (jString startsWith "~~~") {
            println(s"Error: $jString")
            System.exit(-1)
        }
        val symbol2LastTradePrice: Map[String, Double] = Util.getLastTradePrices(jString)

        val dailyQuoteStrings: (String, String) = Await.result(Util.getDailyQuotes(config), 3.seconds)
        val symbol2DailyQuoteList: Map[String, List[QuoteActor.DailyQuote]] =
            Util.getIntervalQuotes(dailyQuoteStrings._1, dailyQuoteStrings._2)

        watchedSymbols.foreach(symbol => {
            val orderHistory = symbol2OrderHistory.get(symbol) match {
                case Some(x) => x
                case _ => List.empty[StockActor.Order]
            }
            actorSystem.actorOf(
                StockActor.props(
                    symbol,
                    orderHistory,
                    positionAndRecentOrderTuple._1.getOrElse(symbol, List.empty[StockActor.Order]),
                    // it's correct to not find position for some watched symbols. It's because we never bought them.
                    positionAndRecentOrderTuple._2.getOrElse(symbol, 0),
                    symbol2LastTradePrice.getOrElse(symbol, Double.NaN),
                    symbol2DailyQuoteList.getOrElse(symbol, {
                        println(s"Error: unable to find the daily quotes for $symbol")
                        System.exit(-1)
                        Nil
                    })
                ),
                symbol
            ) // create all StockActor's
        })

        val quoteActor = actorSystem.actorOf(QuoteActor.props(config), QuoteActor.NAME)
        val orderActor = actorSystem.actorOf(OrderActor.props(config), OrderActor.NAME)
        val websocketActor = actorSystem.actorOf(WebsocketActor.props(websocketListener), WebsocketActor.NAME)

        Spark.get("/:actor/tick", (req: spark.Request, res: spark.Response) => {
            res.`type`("application/json")
            val now = LocalDateTime.now.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            req.params(":actor") match {
                case OrderActor.NAME =>
                    orderActor ! OrderActor.Tick
                    s"Sent ${OrderActor.NAME} a Tick at $now"
                case QuoteActor.NAME =>
                    quoteActor ! QuoteActor.Tick
                    s"Sent ${QuoteActor.NAME} a Tick at $now"
                case WebsocketActor.NAME =>
                    websocketActor ! WebsocketActor.Tick
                    s"Sent ${WebsocketActor.NAME} a Tick at $now"
                case symbol: String =>
                    actorSystem.actorSelection(s"/user/$symbol") ! StockActor.Tick
                    s"Sent StockActor $symbol a Tick at $now"
            }
        })

        Spark.get("/:symbol/debug", (req: spark.Request, res: spark.Response) => {
            import concurrent.{Await, Future}
            import concurrent.duration._
            import akka.util.Timeout
            implicit val timeout: Timeout = Timeout(5.seconds)

            res.`type`("application/json")

            val now = LocalDateTime.now.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            val s = req.params(":symbol")
            s match {
                case "order" =>
                    val mapFuture: Future[Any] = orderActor ? OrderActor.Debug
                    mapFuture match {
                        case x: Future[_] => toJson(Await.result(x, 3.seconds).asInstanceOf[Map[String, String]])
                        case _ => toJson(Map("error" -> "Hm... Tue Jan 29 14:32"))
                    }
                case "quote" =>
                    val mapFuture: Future[Any] = quoteActor ? QuoteActor.Debug
                    mapFuture match {
                        case x: Future[_] => toJson(Await.result(x, 3.seconds).asInstanceOf[Map[String, String]])
                        case _ => toJson(Map("error" -> "Hm... Sun Jan 27 01:18"))
                    }
                case "websocket" =>
                    websocketActor ! WebsocketActor.Debug
                    toJson(Map("message" -> s"Sent a Debug message to $s at $now"))
                case symbol: String =>
                    val mapFuture: Future[Any] =
                        actorSystem.actorSelection(s"/user/$symbol") ? StockActor.Debug
                    mapFuture match {
                        case x: Future[_] => toJson(Await.result(x, 3.seconds).asInstanceOf[Map[String, String]])
                        case _ => toJson(Map("error" -> "Hm... Sat Jan 26 23:42"))
                    }
            }
        })

        Spark.get("/accessToken", (_: spark.Request, _: spark.Response) => accessToken)

        Spark.get("/:symbol/debug/:debugCommand", (req: spark.Request, res: spark.Response) => {
            res.`type`("application/json")
            val symbol = req.params(":symbol")
            val debugCommand: StockActor.DebugCommand.Value =
                StockActor.DebugCommand.withName(req.params(":debugCommand"))
            actorSystem.actorSelection(s"/user/$symbol") ! StockActor.DebugCommandWrapper(debugCommand)
            "{}"
        })

        Spark.post("/:symbol/position-orderList", (req: spark.Request, res: spark.Response) => {
            import org.json4s._
            import org.json4s.native.JsonMethods._

            val symbol = req.params(":symbol")
            /*
            The request body is like this: {
              position: 1,
              orders: [
                {"updated_at":"2018-07-31T16:32:14.860509Z","id":"5a47ef91-9fed-43fc-9b6f-6f6525ac8a70","cumulative_quantity":"1.00000","state":"filled","created_at":"2018-07-31T12:38:51.393562Z","side":"buy","average_price":"99.89000000","quantity":"1.00000"},
                {"updated_at":"2018-07-31T16:32:14.860509Z","id":"6b58f0a2-a0fe-540d-ac70-707636bd9b81","cumulative_quantity":"1.00000","state":"confirmed","created_at":"2018-07-31T12:38:52.404673Z","side":"buy","average_price":"99.89000000","quantity":"1.00000"}
              ]
            }
             */
            Try {
                val jValue = parse(req.body)
                val position = Util.fromJValueToOption[Double](jValue \ "position").get
                val orders = (jValue \ "orders").asInstanceOf[JArray].arr.map(jv => {
                    val updatedAt = Util.fromJValueToOption[String](jv \ "updated_at").get
                    val id = Util.fromJValueToOption[String](jv \ "id").get
                    val cumulativeQuantity = Util.fromJValueToOption[Double](jv \ "cumulative_quantity").getOrElse(Double.NaN)
                    val state = Util.fromJValueToOption[String](jv \ "state").get
                    val price = Util.fromJValueToOption[Double](jv \ "price").getOrElse(Double.NaN)
                    val createdAt = Util.fromJValueToOption[String](jv \ "created_at").get
                    val side = Util.fromJValueToOption[String](jv \ "side").get
                    val averagePrice = Util.fromJValueToOption[Double](jv \ "average_price").getOrElse(Double.NaN)
                    val quantity = Util.fromJValueToOption[Double](jv \ "quantity").get
                    StockActor.Order(averagePrice, createdAt, cumulativeQuantity, id, price, quantity, side, state, updatedAt)
                })
                val stockActor = actorSystem.actorSelection(s"/user/$symbol")
                stockActor ! StockActor.PositionAndOrderList(position, orders)
            } match {
                case Success(_) => "{}"
                case Failure(ex) => s"""{"error":"${ex.getMessage}"}"""
            }
        })
    }

    private def initializeSpark(actorSystem: ActorSystem): WebsocketListener = {
        val webSocketListener = new WebsocketListener(actorSystem)
        Spark.staticFiles.location("/html")
        Spark.staticFiles.location("/static")
        Spark.webSocket("/ws", webSocketListener)

        Spark.get("/debug/:symbol", (request: spark.Request, response: spark.Response) => {
            val symbol = request.params(":symbol")
            "Hello World!"
        })

        webSocketListener
    }

    private def toJson(map: Map[String, String]): String = {
        import org.json4s.DefaultFormats
        import org.json4s.JsonAST.{JField, JObject, JString}
        import org.json4s.native.Serialization
        val jFields: List[JField] = map.map(t => (t._1, JString(t._2))).toList
        Serialization.write(JObject(jFields))(DefaultFormats)
    }

    /**
      * This method is public for UtilTests.testExtractSymbolAndOrder (and maybe other test methods) to use.
      * @param config has DOW stocks only
      */
    def addStocksToDatabase(config: Config) {
        import scala.collection.JavaConverters._
        import scala.collection.mutable
        import com.typesafe.config.ConfigValue

        type JavaMapEntryScalaSet = mutable.Set[java.util.Map.Entry[String, ConfigValue]]

        /*
         * https://www.investopedia.com/terms/n/nasdaq100.asp
         * https://www.cnbc.com/nasdaq-100/
         */
        val nasdaq100 = Set("AAL", "AAPL", "ADBE", "ADI", "ADP", "ADSK", "ALGN", "ALXN", "AMAT", "AMGN",
            "AMZN", "ATVI", "ASML", "AVGO", "BIDU", "BIIB", "BMRN", "CDNS", "CELG", "CERN", "CHKP", "CHTR", "CTRP",
            "CTAS", "CSCO", "CTXS", "CMCSA", "COST", "CSX", "CTSH", "DISH", "DLTR", "EA", "EBAY", "ESRX", "EXPE",
            "FAST", "FB", "FISV", "FOX", "FOXA", "GILD", "GOOG", "GOOGL", "HAS", "HSIC", "HOLX", "ILMN", "INCY", "INTC",
            "INTU", "ISRG", "IDXX", "JBHT", "JD", "KLAC", "KHC", "LBTYA", "LBTYK", "LRCX", "MELI", "MAR", "MCHP",
            "MDLZ", "MNST", "MSFT", "MU", "MXIM", "MYL", "NFLX", "NTES", "NVDA", "NXPI", "ORLY", "PAYX", "PCAR", "BKNG",
            "PYPL", "QCOM", "QRTEA", "REGN", "ROST", "STX", "SHPG", "SIRI", "SWKS", "SBUX", "SYMC", "SNPS", "TTWO",
            "TSLA", "TXN", "TMUS", "ULTA", "VOD", "VRTX", "WBA", "WDC", "WDAY", "VRSK", "WYNN", "XEL", "XLNX")
        val dow = Set("AXP", "AAPL", "BA", "CAT", "CSCO", "CVX", "DWDP", "XOM", "GS", "HD", "IBM", "INTC",
            "JNJ", "KO", "JPM", "MCD", "MMM", "MRK", "MSFT", "NKE", "PFE", "PG", "TRV", "UNH", "UTX", "VZ", "V", "WBA",
            "WMT", "DIS")

        def getStringValueForKey(key: String, set: JavaMapEntryScalaSet): String = set
                .find(e => e.getKey == key)
                .map(e => e.getValue.unwrapped().asInstanceOf[String])
                .get

        def getInstrumentNameSimplename(symbol: String, set: JavaMapEntryScalaSet): (String, String, String) = (
                getStringValueForKey(symbol + ".instrument", set),
                getStringValueForKey(symbol + ".name", set),
                getStringValueForKey(symbol + ".simple_name", set)
        )

        val addDowStockToDatabase = (symbol: String, set: JavaMapEntryScalaSet) => {
            val t3 = getInstrumentNameSimplename(symbol, set)
            StockDatabase.addDowStock(symbol, t3._1, t3._2, t3._3)
        }

        val addNasdaq100StockToDatabase = (symbol: String, set: JavaMapEntryScalaSet) => {
            val t3 = getInstrumentNameSimplename(symbol, set)
            StockDatabase.addNasdaq100Stock(symbol, t3._1, t3._2, t3._3)
        }

        val addRegularStockToDatabase = (symbol: String, set: JavaMapEntryScalaSet) =>
            if (dow.contains(symbol)) { val x = 0 }
            else if (nasdaq100.contains(symbol)) addNasdaq100StockToDatabase(symbol, set)
            else {
                val t3 = getInstrumentNameSimplename(symbol, set)
                StockDatabase.addRegularStock(symbol, t3._1, t3._2, t3._3)
            }

        def addStocksInConfigToDatabase(c: Config, f: (String, JavaMapEntryScalaSet) => Unit) {
            c.entrySet().asScala
                    .groupBy(e => e.getKey.takeWhile(_ != '.'))
                    .foreach(tuple => f(tuple._1, tuple._2))
        }

        addStocksInConfigToDatabase(config.getConfig("dow"), addDowStockToDatabase)
        addStocksInConfigToDatabase(ConfigFactory.load("stock.conf").getConfig("soi"), addRegularStockToDatabase)

//        StockDatabase.debug()
    }
}
