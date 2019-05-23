package home

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ContentTypes, HttpEntity}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Sink
import akka.util.Timeout

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.io.StdIn

object Robinhood {
    def main(args: Array[String]): Unit = {
        implicit val actorSystem: ActorSystem = ActorSystem("R")
        implicit val materializer: ActorMaterializer = ActorMaterializer()
        implicit val timeout: Timeout = Timeout(5.seconds)
        implicit val ec: ExecutionContext = actorSystem.dispatcher

        val fileActor = actorSystem.actorOf(FileActor.props())

        val route: Route =
            path("quotes") {
                post {
                    extractRequestEntity { entity =>
                        val actorRef = actorSystem.actorOf(MyActor.props(fileActor))
                        entity.getDataBytes().runWith(Sink.actorRef(actorRef, MyActor.Done), materializer)
                        complete(HttpEntity(ContentTypes.`application/json`, "{}"))
                    }
                }
            }

        val bindingFuture = Http().bindAndHandle(route, "0.0.0.0", 4567)
        println("Server online at http://localhost:4567/\nPress RETURN to stop...")
        StdIn.readLine()
        bindingFuture
                .flatMap(_.unbind())
                .onComplete(_ => actorSystem.terminate())
    }
}
