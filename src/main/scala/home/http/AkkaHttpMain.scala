package home.http

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.stream.{ActorMaterializer, Materializer}

import scala.concurrent.{ExecutionContext, Future}
import scala.io.StdIn

object AkkaHttpMain {
    def main(args: Array[String]) {
        implicit val actorSystem: ActorSystem = ActorSystem("a-h-ex")
        implicit val materializer: Materializer = ActorMaterializer()
        implicit val executionContext: ExecutionContext = actorSystem.dispatcher

        val route =
            path("hello") {
                complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, "<i>Hello World!</i>"))
            }

        val bindingFuture: Future[Http.ServerBinding] = Http().bindAndHandle(route, "localhost", 5678)
        StdIn.readLine()
        bindingFuture.flatMap(_.unbind()).onComplete(_ => actorSystem.terminate())

    }
}
