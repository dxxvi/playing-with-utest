package home.sparkjava

import scala.concurrent.duration._
import akka.actor.ActorSystem
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.typesafe.config.{Config, ConfigFactory}
import home.TestUtil
import message.{AddSymbol, Tick}
import model.{Fundamental, Quote}
import utest._

import scala.collection.concurrent.TrieMap
import scala.concurrent.Future

object ActorTests extends TestSuite with Util with TestUtil {
    val tests = Tests {
        "Test DefaultWatchListActor" - {
            val config = ConfigFactory.load()
            val actorSystem = ActorSystem("R")

            actorSystem.actorOf(InstrumentActor.props(config), InstrumentActor.NAME)
            val dwlActor = actorSystem.actorOf(DefaultWatchListActor.props(config), DefaultWatchListActor.NAME)
            dwlActor ! Tick

            Thread.sleep(9019)

            println(s"Main.instrument2Symbol: ${Main.instrument2Symbol}")

            actorSystem.terminate()
        }

        "Test Everything" - {
            val config: Config = ConfigFactory.load()
            val actorSystem = ActorSystem("R")
            val mainActor = actorSystem.actorOf(MainActor.props(config), MainActor.NAME)
            val dwlActor = actorSystem.actorOf(DefaultWatchListActor.props(config), DefaultWatchListActor.NAME)
            actorSystem.actorOf(InstrumentActor.props(config), InstrumentActor.NAME)

            dwlActor ! Tick
            Thread.sleep(29482)

            mainActor ! AddSymbol("TESTING")
            dwlActor ! Tick
            Thread.sleep(9482)

            actorSystem.terminate()
        }

        "Test json4s" - {
            import org.json4s._
            import org.json4s.native.JsonMethods._

            val results = Quote.deserialize(readTextFileFromTestResource("robinhood", "quotes.json"))
            println(results)
        }

        "Test TypeSafe Config" - {
            import collection.JavaConverters._
            import org.json4s._
            import org.json4s.native.JsonMethods._

            val config = ConfigFactory.load()
            val trieMap = TrieMap[String, String]()
            trieMap ++= config.getConfig("dow").entrySet().asScala.map(e => (e.getKey, e.getValue.unwrapped().asInstanceOf[String]))
            println(trieMap)

            parse(readTextFileFromTestResource("robinhood", "dow-stocks-mapping.json")) \ "results" match {
                case JArray(jValues) => jValues foreach { jValue => {
                    println(s"""${jValue \ "symbol"} = "${jValue \ "instrument"}"""")
                }}
                case _ => println("hm ...")
            }
        }
    }
}
