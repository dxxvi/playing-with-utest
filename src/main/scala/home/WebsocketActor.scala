package home

import akka.actor.Actor
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, GraphDSL, Sink, Source}

object WebsocketActor {
    sealed trait WebsocketSealedTrait
    case object GetWebsocketFlow extends WebsocketSealedTrait
}

class WebsocketActor extends Actor {
    import WebsocketActor._
    import context.dispatcher
    import akka.http.scaladsl.model.ws._
    import scala.concurrent.Future
    import scala.concurrent.duration._

    implicit val actorMaterializer: ActorMaterializer = ActorMaterializer()

    override def receive: Receive = {
        case GetWebsocketFlow =>

    }
}
