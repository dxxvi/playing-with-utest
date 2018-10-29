package home.sparkjava

import akka.actor.{Actor, Props, Timers}
import home.sparkjava.message.Tick
import org.apache.logging.log4j.ThreadContext

import scala.collection.mutable.ListBuffer
import scala.concurrent.duration._

object WebSocketActor {
    val NAME = "wsActor"

    def props(wsListener: WebSocketListener): Props = Props(new WebSocketActor(wsListener))
}

class WebSocketActor(wsListener: WebSocketListener) extends Actor with Timers with Util {
    import WebSocketActor._

    timers.startPeriodicTimer(Tick, Tick, 2200.milliseconds)

    val quoteMessages: ListBuffer[String] = ListBuffer[String]()

    val _receive: Receive = {
        case Tick =>
            if (quoteMessages.nonEmpty) {
                wsListener.send("MULTI_QUOTES: " + quoteMessages.mkString(" | "))
                quoteMessages.clear()
            }
        case x: String => quoteMessages.+=:(x) // prepend
        case x => logger.debug(s"Don't know what to do with $x yet")
    }

    override def receive: Receive = sideEffect andThen _receive

    private def sideEffect: PartialFunction[Any, Any] = { case x => ThreadContext.put("symbol", NAME); x }
}
