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
        val array: Array[Double] = (for {
            m <- a.split("[-T:Z]").map(_.toDouble)
            n <- b.split("[-T:Z]").map(_.toDouble)
            if m != n
        } yield m - n).take(1)
        if (array.isEmpty) 0 else if (array(0) > 0) 1 else -1
    })

    def main(args: Array[String]): Unit = {
        val config = ConfigFactory.load()

        val actorSystem = ActorSystem("R")

        val mainActor = actorSystem.actorOf(MainActor.props(), "mainActor")
        val webSocketListener = initializeSpark(mainActor)
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

    private def initializeSpark(mainActor: ActorRef): WebSocketListener = {
        val webSocketListener = new WebSocketListener(mainActor)
        Spark.staticFiles.location("/static")
        Spark.webSocket("/ws", webSocketListener)

        Spark.init()                   // Needed if no HTTP route is defined after the WebSocket routes
        webSocketListener
    }
}
