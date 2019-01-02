package home

import akka.actor.{Actor, Timers}
import com.typesafe.config.Config
import home.util.SttpBackends

object PositionActor {
    import akka.actor.Props

    val NAME: String = "position"
    val AUTHORIZATION: String = "Authorization"

    sealed trait PositionSealedTrait
    case object Tick extends PositionSealedTrait

}

class PositionActor(config: Config) extends Actor with Timers with SttpBackends {
    import PositionActor._

    override def receive: Receive = {
        case Tick =>                             // get positions
    }
}
