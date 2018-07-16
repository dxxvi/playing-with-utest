package home.akka

import scala.reflect.runtime.universe._
import akka.actor.{Actor, ActorSystem, Props, Timers}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}
import home.Util
import org.apache.logging.log4j.ThreadContext
import org.apache.logging.log4j.scala.{Logger, Logging}
import utest._

import _root_.scala.concurrent.duration._
import _root_.scala.util.Random

object AkkaTests extends TestSuite {
    val logger: Logger = Logger(classOf[Util])

    val tests = Tests {
        "akka stream" - {
            implicit val actorSystem: ActorSystem = ActorSystem("NumberSystem")
            implicit val materializer: ActorMaterializer = ActorMaterializer()
            val source = Source(4 to 19)
            val sink = Sink.foreach[Int](t => logger.debug(s"from sink $t"))
            source.runForeach(i => logger.debug(s"from source $i"))

/*
            val runnableGraph = source.to(sink)
            runnableGraph.run()
*/
            Thread.sleep(5000)
        }

        "akka log4j2" - {
            implicit val actorSystem: ActorSystem = ActorSystem("Log4j2System")
            implicit val materializer: ActorMaterializer = ActorMaterializer()

            val random = new Random
            Seq("AMD", "HTZ", "ON", "CY", "TSLA", "RRD").foreach { symbol =>
                actorSystem.actorOf(Log4jActor.props(symbol, (2345 + random.nextInt(6)*678).millis), symbol)
            }
            Thread.sleep(41982)
            actorSystem.terminate()
        }

        "Composing PartialFunction" - {
            val f1: Actor.Receive = {
                case x => println(s"f1 is called with $x")
            }
            val f2: PartialFunction[Any, Any] = {
                case x =>
                    println("f2 is called")
                    x
            }
            (f1 compose f2)("...")
        }
    }
}

object Log4jActor {
    def props(symbol: String, interval: FiniteDuration): Props = Props(new Log4jActor(symbol, interval))
}

class Log4jActor(symbol: String, interval: FiniteDuration) extends Actor with Timers with Logging {
    timers.startPeriodicTimer(symbol, symbol, interval)

    override def receive: Receive = {
        case _ =>
            ThreadContext.put("symbol", symbol)
            logger.debug(s"Hi $symbol! ${System.currentTimeMillis() % 9999}")
    }
}
