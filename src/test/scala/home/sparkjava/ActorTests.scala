package home.sparkjava

import scala.concurrent.duration._

import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory
import utest._

object ActorTests extends TestSuite with Util {
    val tests = Tests {
        "Test DefaultWatchListActor" - {
            val config = ConfigFactory.load()
            val actorSystem = ActorSystem("R")

            val dwlActor = actorSystem.actorOf(DefaultWatchListActor.props(config))
            dwlActor ! Tick

            Thread.sleep(4019)
            actorSystem.terminate()
        }
    }
}
