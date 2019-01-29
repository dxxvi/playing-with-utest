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
import home.util.{StockDatabase, Util}
import spark.Spark

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration.FiniteDuration
import scala.io.StdIn
import scala.util.{Failure, Success, Try}

/**
  * To run this class, -Dauthorization.username, -Dauthorization.encryptedPassword are needed.
  * The environment variable 'key' is needed.
  */
object Main {
    var accessToken: String = "_"

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

        val websocketListener: WebsocketListener = initializeSpark(actorSystem)

        val defaultWatchListActor =
            actorSystem.actorOf(DefaultWatchListActor.props(config), DefaultWatchListActor.NAME)
//        defaultWatchListActor ! DefaultWatchListActor.Tick
        val quoteActor = actorSystem.actorOf(QuoteActor.props(config), QuoteActor.NAME)
        val orderActor = actorSystem.actorOf(OrderActor.props(config), OrderActor.NAME)
        val websocketActor = actorSystem.actorOf(WebsocketActor.props(websocketListener), WebsocketActor.NAME)

        Spark.get("/:actor/tick", (req: spark.Request, res: spark.Response) => {
            res.`type`("application/json")
            val now = LocalDateTime.now.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            req.params(":actor") match {
                case DefaultWatchListActor.NAME =>
                    defaultWatchListActor ! DefaultWatchListActor.Tick
                    s"Sent ${DefaultWatchListActor.NAME} a Tick at $now"
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
                    actorSystem.actorSelection(s"${defaultWatchListActor.path}/$symbol") ! StockActor.Tick
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
                case "defaultWatchList" =>
                    val mapFuture: Future[Any] = defaultWatchListActor ? DefaultWatchListActor.Debug
                    mapFuture match {
                        case x: Future[_] => toJson(Await.result(x, 3.seconds).asInstanceOf[Map[String, String]])
                        case _ => toJson(Map("error" -> "Hm... Wed Jan 23 00:51"))
                    }
                case "order" =>
                    orderActor ! OrderActor.Debug
                    toJson(Map("message" -> s"Sent a Debug message to $s at $now"))
                case "quote" =>
                    quoteActor ! QuoteActor.Debug
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
                        actorSystem.actorSelection(s"${defaultWatchListActor.path}/$symbol") ? StockActor.Debug
                    mapFuture match {
                        case x: Future[_] => toJson(Await.result(x, 3.seconds).asInstanceOf[Map[String, String]])
                        case _ => toJson(Map("error" -> "Hm... Sat Jan 26 23:42"))
                    }
            }
        })

        Spark.get("/accessToken", (_: spark.Request, _: spark.Response) => accessToken)
/*
        val route =
            path("ws") {
                complete(HttpEntity(ContentTypes.`text/plain(UTF-8)`, "// TODO this path for websocket"))
            } ~
            get {
                path("ws-like") {
                    complete(HttpEntity(ContentTypes.`text/plain(UTF-8)`, "Returns exactly like the websocket"))
                } ~
                path("set" / "quote" / Segment / Segment) { (symbol, lastTradePriceString) =>
                        if (!Set("AMD", "TSLA").contains(symbol))
                            complete(HttpResponse(status = StatusCodes.BadRequest, entity = HttpEntity(ContentTypes.`text/plain(UTF-8)`, "Invalid symbol: " + symbol)))
                        else
                            Try(lastTradePriceString.toDouble) match {
                                case Failure(ex) => complete(HttpResponse(status = StatusCodes.BadRequest, entity = HttpEntity(ContentTypes.`text/plain(UTF-8)`, lastTradePriceString + " is not a number")))
                                case Success(lastTradePrice) => complete(HttpEntity(ContentTypes.`text/plain(UTF-8)`, s"Set last trade price of $symbol to $lastTradePrice"))
                            }
                }

            }

        val bindingFuture = Http().bindAndHandle(route, "localhost", 8080)
        StdIn.readLine()
        bindingFuture.flatMap(_.unbind()).onComplete(_ => actorSystem.terminate())
*/
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
