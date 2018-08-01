package home.sparkjava

import akka.actor.{Actor, Props, Timers}
import akka.pattern.pipe
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.typesafe.config.Config
import com.softwaremill.sttp._
import org.apache.logging.log4j.ThreadContext

import scala.concurrent.Future

object DefaultWatchListActor {
    val NAME = "defaultWatchListActor"

    case class AddSymbol(symbol: String)

    def props(config: Config): Props = Props(new DefaultWatchListActor(config))
}

class DefaultWatchListActor(config: Config) extends Actor with Timers with Util {
    import context.dispatcher

    val SERVER: String = config.getString("server")
    val authorization: String = if (config.hasPath("Authorization")) config.getString("Authorization") else "No token"
    implicit val httpBackend: SttpBackend[Future, Source[ByteString, Any]] = configureAkkaHttpBackend(config)

    val _receive: Receive = {
        case Tick => sttp.header("Authorization", authorization)
                .get(uri"${SERVER}watchlists/Default")
                .response(asString)
                .send() pipeTo self
        case r: Response[String] => println(s"${r.code} ${r.unsafeBody}")
        case x => println(s"Don't know what to do with $x")
    }

    override def receive: Receive = sideEffect andThen _receive

    val sideEffect: PartialFunction[Any, Any] = { case x => ThreadContext.clearMap(); x }
}
