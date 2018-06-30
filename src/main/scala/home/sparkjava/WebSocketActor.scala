package home.sparkjava

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import com.typesafe.scalalogging.Logger

object WebSocketActor {
    val NAME = "webSocketActor"
    def props(webSocketListener: WebSocketListener): Props = Props(new WebSocketActor(webSocketListener))
}

class WebSocketActor(webSocketListener: WebSocketListener) extends Actor with ActorLogging {
    val logger: Logger = Logger[WebSocketActor]
    var debug = false

    override def receive: Receive = {
        case "DEBUG_ON" => debug = true
        case "DEBUG_OFF" => debug = false
        case x: String =>
            webSocketListener.send(x)
            if (debug) logger.debug(s"`$x` was sent to browser")
        case x => logger.debug(s"Don't know what to do with $x yet")
    }
}
