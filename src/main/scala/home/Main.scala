package home

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ContentTypes, HttpEntity}
import akka.http.scaladsl.server.Directives._
import akka.stream.ActorMaterializer
import com.typesafe.config.{Config, ConfigFactory}
import home.util.StockDatabase

import scala.concurrent.ExecutionContextExecutor
import scala.io.StdIn

object Main {
    def main(args: Array[String]) {
        val config: Config = ConfigFactory.load()

        addStocksToDatabase(config)

        implicit val actorSystem: ActorSystem = ActorSystem("R")
        implicit val actorMaterializer: ActorMaterializer = ActorMaterializer()
        implicit val executionContext: ExecutionContextExecutor = actorSystem.dispatcher

        val defaultWatchListActor =
            actorSystem.actorOf(DefaultWatchListActor.props(config), DefaultWatchListActor.NAME)
        actorSystem.actorOf(QuoteActor.props(config), QuoteActor.NAME)

//        defaultWatchListActor ! DefaultWatchListActor.Tick

        val route =
            path("ws") {
                complete(HttpEntity(ContentTypes.`text/plain(UTF-8)`, "// TODO this path for websocket"))
            } ~
            get {
                path("ws-like") {
                    complete(HttpEntity(ContentTypes.`text/plain(UTF-8)`, "Returns exactly like the websocket"))
                } ~
                path("set/quote" / symbol / lastTradePrice) {

                }

            }

        val bindingFuture = Http().bindAndHandle(route, "localhost", 8080)
        StdIn.readLine()
        bindingFuture.flatMap(_.unbind()).onComplete(_ => actorSystem.terminate())
    }

/*
    def main(args: Array[String]) {
        addStocksToDatabase(ConfigFactory.load())
    }
*/

    /**
      * @param config has DOW stocks only
      */
    private def addStocksToDatabase(config: Config) {
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
