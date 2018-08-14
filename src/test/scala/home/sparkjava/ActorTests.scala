package home.sparkjava

import java.nio.file.{Files, Paths, StandardOpenOption}

import akka.actor.{Actor, ActorSystem, Props}
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.softwaremill.sttp._
import com.typesafe.config.{Config, ConfigFactory}
import home.TestUtil
import message.{AddSymbol, Tick}
import model.{Fundamental, Quote}
import org.json4s.{JBool, JString, JValue}
import utest._

import scala.collection.concurrent.TrieMap
import scala.concurrent.Future
import scala.concurrent.duration._

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
            import org.json4s.native.Serialization

            val c: collection.mutable.Set[String] = collection.mutable.HashSet[String]()
            c += "A"
            c += "B"
            println(Serialization.write(c)(DefaultFormats))

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
        }

        "Get all tradeable stocks" - {
            val actorSystem = ActorSystem("R")
            val actor = actorSystem.actorOf(Props(new AllTradeableStocks))
            actor ! "https://api.robinhood.com/instruments/"

            Thread.sleep(82419)
            actorSystem.terminate()
        }

        "Test PositionActor" - {
            val actorSystem = ActorSystem("R")
            val config = ConfigFactory.load()
            val positionActor = actorSystem.actorOf(PositionActor.props(config), PositionActor.NAME)
            positionActor ! Tick

            Thread.sleep(82419)
            actorSystem.terminate()
        }
    }
}

case class ResponseWrapper(r: Response[(Option[String], List[(String, String)])])

class AllTradeableStocks extends Actor with Util {
    import context.dispatcher
    import akka.pattern.pipe
    import scala.collection.JavaConverters._

    val list: collection.mutable.ArrayBuffer[(String, String)] = collection.mutable.ArrayBuffer[(String, String)]()
    val config: Config = ConfigFactory.load()
    val SERVER: String = config.getString("server")
    implicit val httpBackend: SttpBackend[Future, Source[ByteString, Any]] = configureAkkaHttpBackend(config)

    override def receive: Receive = {
        case url: String => sttp
                .get(uri"$url")
                .response(asString.map(extract))
                .send()
                .map(ResponseWrapper) pipeTo self
        case ResponseWrapper(Response(rawErrorBody, code, statusText, _, _)) => rawErrorBody.fold(
            a => println(s"Error: $code $statusText"),
            a => {
                Files.write(
                    Paths.get("/dev", "shm", "test.txt"),
                    a._2.map(t => f"""${t._1}%-5s = "${t._2}%s"""").asJava,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND
                )
                println(a._2.size)
                if (a._1.isDefined) self ! a._1.get
            }
        )
    }

    private def extract(s: String): (Option[String], List[(String, String)]) = {
        import org.json4s._
        import org.json4s.native.JsonMethods._
        val json = parse(s)
        val next: Option[String] = json \ "next" match {
            case JString(x) => Some(x)
            case _ => None
        }
        val stocks: List[(String, String)] = json \ "results" match {
            case JArray(jValues) => jValues collect {
                case x: JValue if isTradeable(x) =>
                    val symbolO = x \ "symbol" match {
                        case JString(symbol) => Some(symbol)
                    }
                    val urlO = x \ "url" match {
                        case JString(url) => Some(url)
                    }
                    (symbolO.get, urlO.get)
            }
            case _ => List[(String, String)]()
        }
        (next, stocks)
    }

    private def isTradeable(x: JValue): Boolean = {
        (x \ "tradeable" match {
            case JBool(tradeable) => tradeable
            case _ => false
        }) && (x \ "country" match {
            case JString(country) => country == "US"
            case _ => false
        }) && (x \ "state" match {
            case JString(state) => state == "active"
            case _ => false
        }) && (x \ "symbol").isInstanceOf[JString] && (x \ "url").isInstanceOf[JString]
    }
}