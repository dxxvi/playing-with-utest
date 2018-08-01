package home.sparkjava

import akka.actor.{Actor, Props, Timers}
import com.typesafe.config.Config
import org.apache.logging.log4j.scala.Logging

object FundamentalActor {
    val NAME = "fundamentalActor"
    def props(config: Config): Props = Props(new FundamentalActor(config))
}

class FundamentalActor(config: Config) extends Actor with Timers with Util {
    override def receive: Receive = ???
}