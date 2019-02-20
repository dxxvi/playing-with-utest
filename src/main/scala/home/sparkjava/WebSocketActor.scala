package home.sparkjava

import akka.actor.{Actor, Props, Timers}
import akka.event.{LogSource, Logging, LoggingAdapter}
import home.sparkjava.message.Tick

import scala.collection.mutable.ListBuffer
import scala.concurrent.duration._

object WebSocketActor {
    val NAME = "wsActor"

    def props(wsListener: WebSocketListener): Props = Props(new WebSocketActor(wsListener))
}

class WebSocketActor(wsListener: WebSocketListener) extends Actor with Timers with Util {
    import WebSocketActor._

    implicit val logSource: LogSource[AnyRef] = (_: AnyRef) => NAME
    val log: LoggingAdapter = Logging(context.system, this)

    timers.startPeriodicTimer(Tick, Tick, 2200.milliseconds)

    val quoteMessages: ListBuffer[String] = ListBuffer[String]()

    override def receive: Receive = {
        case Tick =>
            if (quoteMessages.nonEmpty) {
                wsListener.send("MULTI_QUOTES: " + quoteMessages.mkString(" | "))
                quoteMessages.clear()
            }
        case x: String => quoteMessages.+=:(x) // prepend
        case x => log.debug(s"Don't know what to do with $x yet")
    }
}
