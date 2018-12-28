package home

import akka.actor.ActorSystem
import com.typesafe.config.{Config, ConfigFactory}

import scala.io.StdIn

object Main {
    def main(args: Array[String]): Unit = {
        val config: Config = ConfigFactory.load()

        val actorSystem = ActorSystem("R")
        val defaultWatchListActor =
            actorSystem.actorOf(DefaultWatchListActor.props(config), DefaultWatchListActor.NAME)
        val amdActor = actorSystem.actorOf(StockActor.props("amd"), "AMD")
        val axpActor = actorSystem.actorOf(StockActor.props("axp"), "American-Express")

        amdActor ! "I send a string to amd"
        axpActor ! "I send another string to AXP"
        defaultWatchListActor ! DefaultWatchListActor.Tick

        StdIn.readLine()
        actorSystem.terminate()
    }
}
