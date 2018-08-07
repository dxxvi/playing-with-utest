package home.sparkjava

import java.util.concurrent.atomic.AtomicInteger

import akka.actor.ActorSystem
import com.typesafe.config.{Config, ConfigFactory}
import org.apache.logging.log4j.ThreadContext
import spark.Spark

import scala.collection.JavaConverters._
import scala.collection.concurrent.TrieMap
import scala.io.StdIn

object Main {
    val instrument2Symbol: TrieMap[String, String] = TrieMap()
    val requestCount = new AtomicInteger()
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

    def main(args: Array[String]): Unit = {
        val config: Config = ConfigFactory.load()
        val actorSystem = ActorSystem("R")
        val mainActor = actorSystem.actorOf(MainActor.props(config), MainActor.NAME)
        val webSocketListener = initializeSpark(actorSystem, mainActor.path.toString)
        actorSystem.actorOf(WebSocketActor.props(webSocketListener), WebSocketActor.NAME)
        actorSystem.actorOf(DefaultWatchListActor.props(config), DefaultWatchListActor.NAME)
        actorSystem.actorOf(InstrumentActor.props(config), InstrumentActor.NAME)

        StdIn.readLine()
        Spark.stop()
        actorSystem.terminate()
    }

    private def initializeSpark(system: ActorSystem, mainActorPath: String): WebSocketListener = {
        val webSocketListener = new WebSocketListener(system, mainActorPath)
        Spark.staticFiles.location("/static")
        Spark.webSocket("/ws", webSocketListener)

        Spark.init()                   // Needed if no HTTP route is defined after the WebSocket routes
        webSocketListener
    }

    private def buildDowStocks(config: Config) {
        dowStocks ++= config.getConfig("dow").entrySet().asScala.map(_.getKey)
    }
}

