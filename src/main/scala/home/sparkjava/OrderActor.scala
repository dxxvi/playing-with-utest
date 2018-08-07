package home.sparkjava

import akka.actor.{Actor, Props, Timers}
import akka.pattern.pipe
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.softwaremill.sttp.SttpBackend
import com.typesafe.config.Config
import home.sparkjava.message.Tick

import scala.concurrent.Future
import scala.concurrent.duration._

object OrderActor {
    val NAME = "orderActor"

    case class Buy(symbol: String, quantity: Int, price: Double)
    case class Sell(symbol: String, quantity: Int, price: Double)
    case class Cancel(orderId: String)

    def props(config: Config): Props = Props(new OrderActor(config))
}

class OrderActor(config: Config) extends Actor with Timers with Util {
    import OrderActor._
    import context.dispatcher

    val SERVER: String = config.getString("server")
    val authorization: String = if (config.hasPath("Authorization")) config.getString("Authorization") else "No token"
    implicit val httpBackend: SttpBackend[Future, Source[ByteString, Any]] = configureAkkaHttpBackend(config)

    timers.startPeriodicTimer(Tick, Tick, 4.seconds)

    val _receive: Receive = ???

    override def receive: Receive = Main.clearThreadContextMap andThen _receive
}
