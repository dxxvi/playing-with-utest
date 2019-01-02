package home

import akka.actor.{Actor, Timers}
import com.typesafe.config.Config
import home.util.SttpBackends

object OrderActor {
    import akka.actor.Props

    val NAME: String = "order"
    val AUTHORIZATION: String = "Authorization"

    sealed trait OrderSealedTrait
    case object Tick extends OrderSealedTrait
    case class OrderHistoryRequest(symbol: String) extends OrderSealedTrait

    def props(config: Config): Props = Props(new OrderActor(config))
}

class OrderActor(config: Config) extends Actor with Timers with SttpBackends {
    import OrderActor._
    import context.dispatcher
    import scala.concurrent.duration._
    import akka.event._
    import akka.pattern.pipe
    import com.softwaremill.sttp._
    import org.json4s._
    import org.json4s.native.JsonMethods._
    import home.util.Util

    implicit val logSource: LogSource[AnyRef] = (t: AnyRef) => NAME
    val log: LoggingAdapter = Logging(context.system, this)

    val SERVER: String = config.getString("server")
    val authorization: String = if (config.hasPath(AUTHORIZATION)) config.getString(AUTHORIZATION) else "No-token"

    val recentOrdersRequest = sttp
            .header(AUTHORIZATION, authorization)
            .get(uri"${SERVER}orders/")
            .response(asString.map(s => s))

    timers.startPeriodicTimer(Tick, Tick, 4019.millis)

    override def receive: Receive = {
        case Tick =>
    }


}
