package home.sparkjava

import akka.actor.{Actor, Props}
import com.typesafe.config.Config
import org.apache.logging.log4j.ThreadContext

object MainActor {
    val NAME = "mainActor"

    def props(config: Config): Props = Props(new MainActor)
}

class MainActor extends Actor with Util {
    val _receive: Receive = ???

    override def receive: Receive = Main.sideEffect andThen _receive
}
