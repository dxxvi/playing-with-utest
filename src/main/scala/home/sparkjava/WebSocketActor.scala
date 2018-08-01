package home.sparkjava

import akka.actor.{Actor, Props}
import org.apache.logging.log4j.ThreadContext
import org.apache.logging.log4j.scala.Logging

object WebSocketActor {
    val NAME = "webSocketActor"
    def props(webSocketListener: WebSocketListener): Props = Props(new WebSocketActor(webSocketListener))
}

class WebSocketActor(webSocketListener: WebSocketListener) extends Actor with Logging {
    var debug = false

    val _receive: Receive = {
        case "DEBUG_ON" => debug = true
        case "DEBUG_OFF" => debug = false
        case x: String =>
            webSocketListener.send(x)
            if (debug) logger.debug(s"`$x` was sent to browser")
        case x => logger.debug(s"Don't know what to do with $x yet")
    }

    val sideEffect: PartialFunction[Any, Any] = {
        case x =>
            ThreadContext.clearMap()
            x
    }

    override def receive: Receive = sideEffect andThen _receive
}
