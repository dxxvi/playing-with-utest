package home.sparkjava

import akka.actor.{ActorSelection, ActorSystem}
import message.{AddSymbol, Tick}
import org.apache.logging.log4j.scala.Logging
import org.eclipse.jetty.websocket.api.Session
import org.json4s.DefaultFormats
import org.json4s.native.Serialization

class WebSocketListener(system: ActorSystem, mainActorPath: String)
        extends org.eclipse.jetty.websocket.api.WebSocketListener with Logging {
    private val BUY: String = "BUY: "
    private val CANCEL: String = "CANCEL: "
    private val DEBUG_OFF: String = "DEBUG_OFF: "
    private val DEBUG_ON: String = "DEBUG_ON: "
    private val SELL: String = "SELL: "
    private val FUNDAMENTAL_REVIEW: String = "FUNDAMENTAL_REVIEW: "
    private val WATCHLIST_ADD: String = "WATCHLIST_ADD: "

    private var session: Option[Session] = None

    def send(message: String): Unit = {
        this.session.filter(_.isOpen).foreach(_.getRemote.sendString(message))
    }

    override def onWebSocketClose(i: Int, s: String): Unit = {
        logger.debug("WebSocket closed.")
        session = None
    }

    override def onWebSocketConnect(session: Session): Unit = {
        logger.debug("WebSocket connected.")
        this.session = Some(session)

        send(s"DOW_STOCKS: ${Serialization.write(Main.dowStocks)(DefaultFormats)}")

        system.actorSelection(s"$mainActorPath/*") ! Tick
    }

    override def onWebSocketError(throwable: Throwable): Unit = {
        logger.debug(s"WebSocket error: ${throwable.getMessage}")
    }

    override def onWebSocketBinary(bytes: Array[Byte], i: Int, i1: Int): Unit = {
        logger.debug("On WebSocket binary.")
    }

    override def onWebSocketText(s: String): Unit = {
        val orderActor: ActorSelection = system.actorSelection(s"$mainActorPath/../${OrderActor.NAME}")
        if (s startsWith CANCEL)
            orderActor ! OrderActor.Cancel(s.replace(CANCEL, ""))
        else if (s.startsWith(BUY) && s.count(_ == ' ') == 3) {   // s looks like this BUY: AMD: 19 11.07
            val array = s.split(" ")
            orderActor ! OrderActor.Buy(array(1).replace(":", ""), array(2).toInt, array(3).toDouble)
        }
        else if (s.startsWith(SELL) && s.count(_ == ' ') == 3) {  // s looks like this SELL: HTZ: 82 19.04
            val array = s.split(" ")
            orderActor ! OrderActor.Sell(array(1).replace(":", ""), array(2).toInt, array(3).toDouble)
        }
        else if (s startsWith DEBUG_OFF) {
            val symbol = s.replace(DEBUG_OFF, "")                 // this is a symbol or actor name
            system.actorSelection(s"$mainActorPath/symbol-$symbol") ! "DEBUG_OFF"
            system.actorSelection(s"$mainActorPath/../$symbol") ! "DEBUG_OFF"
        }
        else if (s startsWith DEBUG_ON) {
            val symbol = s.replace(DEBUG_ON, "")                  // this is a symbol or actor name
            system.actorSelection(s"$mainActorPath/symbol-$symbol") ! "DEBUG_ON"
            system.actorSelection(s"$mainActorPath/../$symbol") ! "DEBUG_ON"
        }
        else if (s startsWith FUNDAMENTAL_REVIEW) {
            val symbol = s.replace(FUNDAMENTAL_REVIEW, "")
            system.actorSelection(s"$mainActorPath/../${FundamentalActor.NAME}") ! symbol
        }
        else if (s startsWith WATCHLIST_ADD) {
            val symbol = s.replace(WATCHLIST_ADD, "")
            system.actorSelection(s"$mainActorPath/../${DefaultWatchListActor.NAME}") ! AddSymbol(symbol)
        }
    }
}
