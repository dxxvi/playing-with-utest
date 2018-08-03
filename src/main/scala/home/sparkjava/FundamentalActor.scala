package home.sparkjava

import akka.actor.{Actor, Props, Timers}
import com.typesafe.config.Config

object FundamentalActor {
    val NAME = "fundamentalActor"
    def props(config: Config): Props = Props(new FundamentalActor(config))
}

class FundamentalActor(config: Config) extends Actor with Timers with Util {
    val _receive: Receive = ???

    override def receive: Receive = Main.clearThreadContextMap andThen _receive


}