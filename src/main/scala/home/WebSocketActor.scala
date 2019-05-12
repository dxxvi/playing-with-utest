package home

import akka.actor.{Actor, Props}
import akka.event.{Logging, LoggingAdapter}
import home.message.{Debug, M1, Tick}
import org.json4s._

import scala.collection.mutable.ListBuffer

object WebSocketActor {
    val NAME: String = "WEBSOCKET"

    def props: Props = Props(new WebSocketActor)
}

/**
  * This actor is used to make sure that we don't call the jettyWebSocketSession.send parallelly.
  */
class WebSocketActor extends Actor {
    val log: LoggingAdapter = Logging(context.system, this)(_ => WebSocketActor.NAME)

    val m1Buffer: ListBuffer[JObject] = ListBuffer.empty

    override def receive: Receive = {
        case M1(jObject) => m1Buffer append jObject
        case M1.All =>
            sender() ! m1Buffer.toList
            m1Buffer.clear()

        case Debug => sender() ! JObject()
        case Tick         => // ignore these
        case M1.ClearHash =>
    }
}
