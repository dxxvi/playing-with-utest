package home.sparkjava

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import com.typesafe.scalalogging.Logger

object WebSocketActor {
    val NAME = "webSocketActor"
    def props(webSocketListener: WebSocketListener): Props = Props(new WebSocketActor(webSocketListener))
}

class WebSocketActor(webSocketListener: WebSocketListener) extends Actor with ActorLogging {
    val logger: Logger = Logger[WebSocketActor]

    override def receive: Receive = {
        case x: String => webSocketListener.send(x)
        case x => logger.debug(s"Don't know what to do with $x yet")
    }
}
