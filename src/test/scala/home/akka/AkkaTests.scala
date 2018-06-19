package home.akka

import scala.reflect.runtime.universe._
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}
import com.typesafe.scalalogging.Logger
import home.Util
import utest._

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
    }
}
