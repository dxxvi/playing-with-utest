package home

import akka.actor.{Actor, Props, Timers}
import com.typesafe.config.Config

object DefaultWatchListActor {
    val NAME = "defaultWatchList"

    sealed trait DefaultWatchListSealedTrait
    case object Tick extends DefaultWatchListSealedTrait

    def props(config: Config): Props = Props(new DefaultWatchListActor(config))
}

class DefaultWatchListActor(config: Config) extends Actor with Timers {
    import DefaultWatchListActor._
    import concurrent.duration._
    import akka.event.Logging
    import akka.event.LogSource
    import com.softwaremill.sttp._

    implicit val logSource: LogSource[AnyRef] = new LogSource[AnyRef] {
        override def genString(t: AnyRef): String = NAME
    }
    val log = Logging(context.system, this)

    implicit val backend: SttpBackend[Id, Nothing] = HttpURLConnectionBackend()

    timers.startPeriodicTimer(Tick, Tick, 60.seconds)

    val receive: Receive = {
        case Tick =>
            val request = sttp.body(Map("a" -> "b")).post(uri"https://xxxxx")
            val x = request.send()
    }
}
