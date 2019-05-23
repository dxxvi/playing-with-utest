package home

import akka.actor.{Actor, ActorRef, PoisonPill, Props}
import akka.util.ByteString

object MyActor {
    case object Done

    def props(fileActor: ActorRef): Props = Props(new MyActor(fileActor))
}

class MyActor(fileActor: ActorRef) extends Actor {
    import MyActor._
    import org.json4s._
    import org.json4s.native.JsonMethods._

    var s: String = ""
    override def receive: Receive = {
        case b: ByteString => s = s + b.utf8String
        case Done =>
            parse(s) \ "results" match {
                case x: JArray => fileActor ! x
                case _ => println(s"results in $s is not an array")
            }
            self ! PoisonPill
    }
}
