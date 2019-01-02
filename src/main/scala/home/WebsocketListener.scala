package home
import akka.actor.ActorSystem
import akka.event.{LogSource, Logging, LoggingAdapter}
import org.eclipse.jetty.websocket.api.Session

class WebsocketListener(actorSystem: ActorSystem) extends org.eclipse.jetty.websocket.api.WebSocketListener {
    implicit val logSource: LogSource[AnyRef] = new LogSource[AnyRef] {
        override def genString(t: AnyRef): String = "WebsocketListener"
    }
    val log: LoggingAdapter = Logging(actorSystem, this)

    private var session: Option[Session] = None

    def send(message: String): Unit = {
        this.session.filter(_.isOpen).foreach(_.getRemote.sendString(message))
    }

    override def onWebSocketBinary(bytes: Array[Byte], i: Int, i1: Int): Unit = log.debug("onWebSocketBinary")

    override def onWebSocketText(s: String): Unit = log.info("onWebSocketText {}", s)

    override def onWebSocketClose(i: Int, s: String): Unit = {
        session = None
        log.debug("onWebSocketClose")
    }

    override def onWebSocketConnect(_session: Session): Unit = session = Some(_session)

    override def onWebSocketError(throwable: Throwable): Unit = log.error(throwable, "onWebSocketError")
}
