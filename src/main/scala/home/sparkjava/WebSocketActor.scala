package home.sparkjava

import akka.actor.{Actor, Props}
import org.apache.logging.log4j.ThreadContext

object WebSocketActor {
    val NAME = "wsActor"

    def props(wsListener: WebSocketListener): Props = Props(new WebSocketActor(wsListener))
}

class WebSocketActor(wsListener: WebSocketListener) extends Actor with Util {
    import WebSocketActor._

    val _receive: Receive = {
        case x: String => wsListener.send(x)
        case x => logger.debug(s"Don't know what to do with $x yet")
    }

    override def receive: Receive = sideEffect andThen _receive

    private def sideEffect: PartialFunction[Any, Any] = { case x => ThreadContext.put("symbol", NAME); x }
}
