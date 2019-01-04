package home

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.stream.ActorMaterializer
import com.typesafe.config.{Config, ConfigFactory}
import home.util.StockDatabase
import spark.Spark

import scala.concurrent.ExecutionContextExecutor
import scala.io.StdIn
import scala.util.{Failure, Success, Try}

object Main {
    def main(args: Array[String]) {
        val config: Config = ConfigFactory.load()

        addStocksToDatabase(config)

        implicit val actorSystem: ActorSystem = ActorSystem("R")
        implicit val actorMaterializer: ActorMaterializer = ActorMaterializer()

        val websocketListener: WebsocketListener = initializeSpark(actorSystem)

        val defaultWatchListActor =
            actorSystem.actorOf(DefaultWatchListActor.props(config), DefaultWatchListActor.NAME)
        val quoteActor = actorSystem.actorOf(QuoteActor.props(config), QuoteActor.NAME)
        val orderActor = actorSystem.actorOf(OrderActor.props(config), OrderActor.NAME)
        val websocketActor = actorSystem.actorOf(WebsocketActor.props(websocketListener), WebsocketActor.NAME)

        Spark.get("/:actor/tick", (req: spark.Request, _: spark.Response) => {
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

        Spark.get("/:symbol/debug", (req: spark.Request, _: spark.Response) => {
            val now = LocalDateTime.now.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            val s = req.params(":symbol")
            s match {
                case "defaultWatchList" => defaultWatchListActor ! DefaultWatchListActor.Debug
                case "order" => orderActor ! OrderActor.Debug
                case "quote" => quoteActor ! QuoteActor.Debug
                case "websocket" => websocketActor ! WebsocketActor.Debug
                case symbol: String =>
                    actorSystem.actorSelection(s"${defaultWatchListActor.path}/$symbol") ! StockActor.Debug
            }
            s"Sent a Debug message to $s at $now"
        })
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

/*
    def main(args: Array[String]) {
        addStocksToDatabase(ConfigFactory.load())
    }
*/

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
            "WMT", "DIS"
        )

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

        def addStocksInConfigToDatabase(c: Config, f: Function2[String, JavaMapEntryScalaSet, Unit]) {
            c.entrySet().asScala
                    .groupBy(e => e.getKey.takeWhile(_ != '.'))
                    .foreach(tuple => f(tuple._1, tuple._2))
        }

        addStocksInConfigToDatabase(config.getConfig("dow"), addDowStockToDatabase)
        addStocksInConfigToDatabase(ConfigFactory.load("stock.conf").getConfig("soi"), addRegularStockToDatabase)

//        StockDatabase.debug()
    }
}
