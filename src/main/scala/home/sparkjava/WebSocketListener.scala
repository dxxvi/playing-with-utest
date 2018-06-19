package home.sparkjava

import akka.actor.ActorRef
import com.typesafe.scalalogging.Logger
import org.eclipse.jetty.websocket.api.Session

class WebSocketListener(mainActor: ActorRef) extends org.eclipse.jetty.websocket.api.WebSocketListener {
    private val logger: Logger = Logger[WebSocketListener]
    private var session: Option[Session] = None

    def send(message: String): Unit = {
        this.session.filter(_.isOpen).foreach(_.getRemote.sendString(message))
    }

    override def onWebSocketClose(i: Int, s: String): Unit = {
        logger.debug("WebSocket closed.")
        session = None
    }

    override def onWebSocketConnect(session: Session): Unit = {
        logger.debug("WebSocket connected. There's a session.")
        this.session = Some(session)
    }

    override def onWebSocketError(throwable: Throwable): Unit = {
        logger.debug(s"WebSocket error: ${throwable.getMessage}")
    }

    override def onWebSocketBinary(bytes: Array[Byte], i: Int, i1: Int): Unit = {
        logger.debug("On WebSocket binary.")
    }

    override def onWebSocketText(s: String): Unit = {
        mainActor ! s
    }
}
