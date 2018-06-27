package home.sparkjava

import akka.actor.{ActorRef, ActorSystem}
import com.typesafe.config.ConfigFactory
import spark.Spark

import scala.collection.concurrent.TrieMap
import scala.io.StdIn

object Main {
    val instrument2Symbol: TrieMap[String, String] = TrieMap()

    // compare 2 timestamps of the format yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'. The '.SSSSSS' is optional.
    val timestampOrdering: Ordering[String] = Ordering.comparatorToOrdering[String]((a: String, b: String) => {
        val aa = a.split("[-T:Z]")
        val bb = b.split("[-T:Z]")
        (aa.iterator zip bb.iterator)                      // reason to use iterator: to make it lazy
                .map { case (a0, b0) => a0.toDouble.compareTo(b0.toDouble) }
                .find(_ != 0)
                .getOrElse(0)
    })

    def main(args: Array[String]): Unit = {
        val config = ConfigFactory.load()

        val actorSystem = ActorSystem("R")

        val mainActor = actorSystem.actorOf(MainActor.props(), MainActor.NAME)
        val webSocketListener = initializeSpark(actorSystem, mainActor.path.toString)
        actorSystem.actorOf(PositionActor.props(config), PositionActor.NAME)
        actorSystem.actorOf(FundamentalActor.props(config), FundamentalActor.NAME)
        actorSystem.actorOf(DefaultWatchListActor.props(config), DefaultWatchListActor.NAME)
        actorSystem.actorOf(InstrumentActor.props(config), InstrumentActor.NAME)
        actorSystem.actorOf(WebSocketActor.props(webSocketListener), WebSocketActor.NAME)
        actorSystem.actorOf(QuoteActor.props(config), QuoteActor.NAME)
        actorSystem.actorOf(OrderActor.props(config), OrderActor.NAME)

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
}
