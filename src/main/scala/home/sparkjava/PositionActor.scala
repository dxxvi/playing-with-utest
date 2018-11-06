package home.sparkjava

import java.time.LocalTime

import akka.actor.{Actor, Props, Timers}
import akka.pattern.pipe
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.softwaremill.sttp._
import com.typesafe.config.Config
import home.sparkjava.model.Position
import message.Tick

import scala.concurrent.Future

object PositionActor {
    val NAME = "positionActor"

    case class PositionResponse(r: Response[List[Position]])

    def props(config: Config): Props = Props(new PositionActor(config))
}

class PositionActor(config: Config) extends Actor with Timers with Util {
    import PositionActor._
    import context.dispatcher

    val SERVER: String = config.getString("server")
    val authorization: String = if (config.hasPath("Authorization")) config.getString("Authorization") else "No token"
    implicit val httpBackend: SttpBackend[Future, Source[ByteString, Any]] = configureAkkaHttpBackend(config)

    timers.startPeriodicTimer(Tick, Tick, Main.calculateShortDuration())

    val _receive: Receive = {
        case Tick =>
            sttp.header("Authorization", authorization)
                .get(uri"${SERVER}positions/")
                .response(asString.map(Position.deserialize))
                .send()
                .map(PositionResponse) pipeTo self
        case PositionResponse(Response(rawErrorBody, code, statusText, _, _)) =>
            rawErrorBody.fold(
                _ => logger.error(s"Error in getting positions: $code $statusText"),
                a => {
                    logger.debug(s"Got ${a.size} positions: ${a.count(p => p.quantity.isDefined)} usable")
                    a foreach { position => {
                        if (position.quantity.exists(_ >= 0))
                            Main.instrument2Symbol.get(position.instrument.getOrElse("")).foreach(symbol =>
                                context.actorSelection(s"../${MainActor.NAME}/symbol-$symbol") ! position
                            )
                        else
                            logger.warn(s"A position with wrong quantity: $position")
                    }}
                }
            )
    }

    override def receive: Receive = Main.clearThreadContextMap andThen _receive
}
