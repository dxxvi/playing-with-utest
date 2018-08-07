package home.sparkjava

import akka.actor.{Actor, Props, Timers}
import akka.pattern.pipe
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.softwaremill.sttp._
import com.typesafe.config.Config
import home.sparkjava.model.Position
import message.Tick

import scala.concurrent.Future
import scala.concurrent.duration._

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

    timers.startPeriodicTimer(Tick, Tick, 4.seconds)

    val _receive: Receive = {
        case Tick =>
            sttp.header("Authorization", authorization)
                .get(uri"${SERVER}positions/")
                .response(asString.map(Position.deserialize))
                .send()
                .map(PositionResponse) pipeTo self
            Main.requestCount.incrementAndGet()
        case PositionResponse(Response(rawErrorBody, code, statusText, _, _)) =>
            Main.requestCount.decrementAndGet()
            rawErrorBody.fold(
                a => logger.error(s"Error in getting positions: $code $statusText ${a.mkString}"),
                a => a foreach { position => {
                    if (position.quantity.isDefined)
                        Main.instrument2Symbol.get(position.instrument.getOrElse("")).foreach(symbol =>
                            context.actorSelection(s"../${MainActor.NAME}/symbol-$symbol") ! position
                        )
                }}
            )
    }

    override def receive: Receive = Main.clearThreadContextMap andThen _receive
}
