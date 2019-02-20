package home.sparkjava

import akka.actor.{ActorSelection, ActorSystem}
import message.{AddSymbol, Tick}
import org.eclipse.jetty.websocket.api.Session
import org.json4s.DefaultFormats
import org.json4s.native.Serialization

class WebSocketListener(system: ActorSystem)
        extends org.eclipse.jetty.websocket.api.WebSocketListener {
    private val BUY: String = "BUY: "
    private val CANCEL: String = "CANCEL: "
    private val DEBUG: String = "DEBUG: "
    private val SELL: String = "SELL: "
    private val FUNDAMENTAL_REVIEW: String = "FUNDAMENTAL_REVIEW: "
    private val WATCHLIST_ADD: String = "WATCHLIST_ADD: "

    private var session: Option[Session] = None

    def send(message: String): Unit = {
        this.session.filter(_.isOpen).foreach(_.getRemote.sendString(message))
    }

    override def onWebSocketClose(i: Int, s: String): Unit = {
        session = None
    }

    override def onWebSocketConnect(session: Session): Unit = {
        this.session = Some(session)

        send(s"DOW_STOCKS: ${Serialization.write(Main.dowStocks)(DefaultFormats)}")
    }

    override def onWebSocketError(throwable: Throwable): Unit = {
    }

    override def onWebSocketBinary(bytes: Array[Byte], i: Int, i1: Int): Unit = {
    }

    override def onWebSocketText(s: String): Unit = {
        val orderActor: ActorSelection = system.actorSelection(s"/user/${OrderActor.NAME}")
        if (s startsWith CANCEL)
            orderActor ! OrderActor.Cancel(s.replace(CANCEL, ""))
        else if ((s.startsWith(BUY) || s.startsWith(SELL)) && s.count(_ == ' ') == 4) {
            // s looks like this BUY: AMD: 19 11.07 instrument
            val array = s.split(" ")
            orderActor ! OrderActor.BuySell(
                array(0).replace(":", "").toLowerCase,
                array(1).replace(":", ""),
                array(4),
                array(2).toInt,
                array(3).toDouble
            )
        }
    }
}
