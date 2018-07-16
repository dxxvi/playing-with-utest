package home.akka

import scala.reflect.runtime.universe._
import akka.actor.{Actor, ActorLogging, ActorSystem, Props, Timers}
import akka.event.Logging
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}
import com.typesafe.scalalogging.Logger
import home.Util
import home.sparkjava.Tick
import utest._

import scala.concurrent.duration._
import scala.util.Random

object AkkaTests extends TestSuite {
    val logger: Logger = Logger[Util]

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
    }
}

object Log4jActor {
    def props(symbol: String, interval: FiniteDuration): Props = Props(new Log4jActor(symbol, interval))
}

class Log4jActor(symbol: String, interval: FiniteDuration) extends Actor /*with ActorLogging*/ with Timers {
    val log = Logging(context.system, s"${classOf[Log4jActor].getName}.$symbol")

    timers.startPeriodicTimer(symbol, symbol, interval)

    override def receive: Receive = {
        case _ => log.debug(s"Hi! ${System.currentTimeMillis() % 9999}")
    }
}
