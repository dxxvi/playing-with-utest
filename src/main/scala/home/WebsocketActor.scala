package home

import akka.actor.Actor
import home.util.TimersX

object WebsocketActor {
    import akka.actor.Props

    val NAME: String = "websocket"

    sealed trait WebsocketSealedTrait
    case object Tick extends WebsocketSealedTrait
    case class Message(s: String) extends WebsocketSealedTrait
    case object Debug extends WebsocketSealedTrait

    def props(websocketListener: WebsocketListener): Props = Props(new WebsocketActor(websocketListener))
}

class WebsocketActor(websocketListener: WebsocketListener) extends Actor with TimersX {
    import WebsocketActor._
    import akka.event._
    import collection.mutable.ListBuffer
    import concurrent.duration._
    import org.json4s._
    import org.json4s.native.Serialization

    implicit val logSource: LogSource[AnyRef] = (_: AnyRef) => NAME
    val log: LoggingAdapter = Logging(context.system, this)

    val messages: ListBuffer[String] = ListBuffer.empty[String]

    timersx.startPeriodicTimer(Tick, Tick, 2200.millis)

    override def receive: Receive = {
        case Tick => if (messages.nonEmpty) websocketListener.send(Serialization.write(JArray(
            messages.toList.map(JString(_))
        ))(DefaultFormats))

        case Message(s) => s +=: messages

        case Debug =>
            val map = debug()
            sender() ! map
    }

    private def debug(): Map[String, String] = {
        val s = s"""
               |${WebsocketActor.NAME} debug information:
               |  messages: $messages
             """.stripMargin
        log.info(s)
        Map("messages" -> Serialization.write(JArray(messages.toList.map(JString(_))))(DefaultFormats))
    }
}
