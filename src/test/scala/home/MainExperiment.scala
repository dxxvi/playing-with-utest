package home

import akka.actor.ActorSystem
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.ActorMaterializer
import akka.util.Timeout

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext

object MainExperiment {
    def main(args: Array[String]): Unit = {
        implicit val actorSystem: ActorSystem = ActorSystem("R")
        implicit val ec: ExecutionContext = actorSystem.dispatcher
        implicit val materializer: ActorMaterializer = ActorMaterializer()
        implicit val timeout: Timeout = Timeout(5.seconds)

        var accessToken: String = "NO_ACCESS_TOKEN_YET"

        val route: Route =
            path("accessToken") {
                post {
                    
                }
            }
    }
}
