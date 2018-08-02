package home.sparkjava

import akka.actor.{Actor, Props}
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.softwaremill.sttp._
import com.softwaremill.sttp.json4s._
import com.typesafe.config.Config
import org.apache.logging.log4j.ThreadContext

import scala.concurrent.Future

object InstrumentActor {
    val NAME = "instrumentActor"

    case class StringResponse(r: Response[String])

    def props(config: Config): Props = Props(new InstrumentActor(config))
}

class InstrumentActor(config: Config) extends Actor with Util {
    import InstrumentActor._
    import context.dispatcher

    val SERVER: String = config.getString("server")
    val authorization: String = if (config.hasPath("Authorization")) config.getString("Authorization") else "No token"
    implicit val httpBackend: SttpBackend[Future, Source[ByteString, Any]] = configureAkkaHttpBackend(config)

    val _receive: Receive = {
        case instrument: String => logger.debug(s"Gonna access $instrument")
    }

    override def receive: Receive = Main.sideEffect andThen _receive
}
