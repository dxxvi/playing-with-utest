package home

import akka.actor.{Actor, Timers}

object WebsocketActor {
    import akka.actor.Props

    val NAME: String = "websocket"

    sealed trait WebsocketSealedTrait
    case object Tick extends WebsocketSealedTrait
    case class Message(s: String) extends WebsocketSealedTrait

    def props(websocketListener: WebsocketListener): Props = Props(new WebsocketActor(websocketListener))
}

class WebsocketActor(websocketListener: WebsocketListener) extends Actor with Timers {
    import WebsocketActor._
    import akka.event._
    import collection.mutable.ListBuffer
    import concurrent.duration._
    import org.json4s._
    import org.json4s.native.JsonMethods._

    implicit val logSource: LogSource[AnyRef] = (t: AnyRef) => NAME
    val log: LoggingAdapter = Logging(context.system, this)

    val messages: ListBuffer[String] = ListBuffer.empty[String]

    timers.startPeriodicTimer(Tick, Tick, 2200.millis)

    override def receive: Receive = {
        case Tick => if (messages.nonEmpty) websocketListener.send(compact(render(JArray(
            messages.toList.map(JString(_))
        ))))

        case Message(s) => s +=: messages
    }
}
