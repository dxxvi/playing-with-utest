package home.sparkjava

import java.util.concurrent.atomic.AtomicInteger

import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory

import scala.collection.concurrent.TrieMap

object Main extends App {
    val instrument2Symbol: TrieMap[String, String] = TrieMap()
    val requestCount = new AtomicInteger()
    val config = ConfigFactory.load()
    val actorSystem = ActorSystem("R")

    // compare 2 timestamps of the format yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'. The '.SSSSSS' is optional.
    val timestampOrdering: Ordering[String] = Ordering.comparatorToOrdering[String]((a: String, b: String) => {
        val aa = a.split("[-T:Z]")
        val bb = b.split("[-T:Z]")
        (aa.iterator zip bb.iterator) // reason to use iterator: to make it lazy
                .map { case (a0, b0) => a0.toDouble.compareTo(b0.toDouble) }
                .find(_ != 0)
                .getOrElse(0)
    })

}
