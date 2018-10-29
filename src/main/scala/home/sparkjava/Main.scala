package home.sparkjava

import akka.actor.ActorSystem
import com.typesafe.config.{Config, ConfigFactory}
import message.Tick
import org.apache.logging.log4j.ThreadContext
import spark.{Request, Response, Route, Spark}

import scala.collection.JavaConverters._
import scala.collection.concurrent.TrieMap
import scala.io.StdIn

object Main {
    val instrument2Symbol: TrieMap[String, String] = TrieMap()
    val clearThreadContextMap: PartialFunction[Any, Any] = { case x => ThreadContext.clearMap(); x }

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
    var djia: Double = 0 // from cnbc.com

    def main(args: Array[String]): Unit = {
        val config: Config = ConfigFactory.load()

        buildStocksDB(config)

        val actorSystem = ActorSystem("R")
        val mainActor = actorSystem.actorOf(MainActor.props(config), MainActor.NAME)
        val webSocketListener = initializeSpark(actorSystem, mainActor.path.toString)
        actorSystem.actorOf(WebSocketActor.props(webSocketListener), WebSocketActor.NAME)
        actorSystem.actorOf(PositionActor.props(config), PositionActor.NAME)
        actorSystem.actorOf(FundamentalActor.props(config), FundamentalActor.NAME)
        actorSystem.actorOf(QuoteActor.props(config), QuoteActor.NAME)
        actorSystem.actorOf(OrderActor.props(config), OrderActor.NAME)

        val dwlActor = actorSystem.actorOf(DefaultWatchListActor.props(config), DefaultWatchListActor.NAME)

        actorSystem.actorOf(InstrumentActor.props(config), InstrumentActor.NAME)

        dwlActor ! Tick

        StdIn.readLine()
        Spark.stop()
        actorSystem.terminate()
    }

    private def initializeSpark(actorSystem: ActorSystem, mainActorPath: String): WebSocketListener = {
        val webSocketListener = new WebSocketListener(actorSystem, mainActorPath)
        Spark.staticFiles.location("/static")
        Spark.webSocket("/ws", webSocketListener)

        Spark.get("/quotes/historicals/:symbol", (request: Request, response: Response) => {
            val symbol = request.params(":symbol")
            actorSystem.actorSelection(s"$mainActorPath/../${QuoteActor.NAME}") ! QuoteActor.Download(symbol)
            s"$symbol.csv was generated."
        })

        Spark.init()                   // Needed if no HTTP route is defined after the WebSocket routes
        webSocketListener
    }

    def buildStocksDB(config: Config) {
        dowStocks ++= config.getConfig("dow").root().keySet().asScala

        def f(a: String, b: String): String =
            if (a == "") b
            else if (b == "") a
            else if (a.length < b.length) a
            else b

        InstrumentActor.instrument2NameSymbol ++= config.getConfig("dow").root().keySet().asScala
                .map(symbol => (
                        config.getString(s"dow.$symbol.instrument"),
                        (f(config.getString(s"dow.$symbol.name"), config.getString(s"dow.$symbol.simple_name")), symbol)
                ))
        val conf = ConfigFactory.load("stock.conf")
        InstrumentActor.instrument2NameSymbol ++= conf.getConfig("soi").root().keySet().asScala
                .map(symbol => (
                        conf.getString(s"soi.$symbol.instrument"),
                        (f(conf.getString(s"soi.$symbol.name"), conf.getString(s"soi.$symbol.simple_name")), symbol)
                ))
    }
}

