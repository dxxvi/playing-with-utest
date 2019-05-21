package home

import akka.actor.{Actor, PoisonPill, Props}
import akka.util.ByteString

object MyActor {
    case object Done

    def props(): Props = Props(new MyActor)
}

class MyActor extends Actor {
    import MyActor._

    var s: String = ""
    override def receive: Receive = {
        case b: ByteString =>
            s = s + b.utf8String
            println(".")
        case Done =>
            println(s)
            self ! PoisonPill
    }
}
