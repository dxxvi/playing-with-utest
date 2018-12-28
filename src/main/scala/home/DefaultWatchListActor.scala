package home

import akka.actor.{Actor, Timers}
import com.typesafe.config.Config
import home.util.SttpBackends

import scala.concurrent.Future

object DefaultWatchListActor {
    import akka.actor.Props

    val NAME = "defaultWatchList"
    val AUTHORIZATION = "Authorization"

    sealed trait DefaultWatchListSealedTrait
    case object Tick extends DefaultWatchListSealedTrait

    def props(config: Config): Props = Props(new DefaultWatchListActor(config))
}

class DefaultWatchListActor(config: Config) extends Actor with Timers with SttpBackends {
    import DefaultWatchListActor._
    import concurrent.duration._
    import akka.event.Logging
    import akka.event.LogSource
    import akka.stream.scaladsl.Source
    import akka.util.ByteString
    import com.softwaremill.sttp._
    import com.softwaremill.sttp.akkahttp.AkkaHttpBackend

    implicit val logSource: LogSource[AnyRef] = new LogSource[AnyRef] {
        override def genString(t: AnyRef): String = NAME
    }
    val log = Logging(context.system, this)


    implicit val backend: SttpBackend[Future, Source[ByteString, Any]] = configureAkkaHttpBackend(config)
    val SERVER: String = config.getString("server")
    val authorization: String = if (config.hasPath(AUTHORIZATION)) config.getString(AUTHORIZATION) else "No-token"

    timers.startPeriodicTimer(Tick, Tick, 60.seconds)

    val receive: Receive = {
        case Tick =>
            val request = sttp.body(Map("a" -> "b")).post(uri"https://xxxxx")
            val x = request.send()
    }
}
