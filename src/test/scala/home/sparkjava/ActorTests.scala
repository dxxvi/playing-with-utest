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

            actorSystem.actorOf(InstrumentActor.props(config), InstrumentActor.NAME)
            val dwlActor = actorSystem.actorOf(DefaultWatchListActor.props(config), DefaultWatchListActor.NAME)
            dwlActor ! Tick

            Thread.sleep(9019)
            actorSystem.terminate()
        }
    }
}
