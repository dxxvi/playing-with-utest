package home

import java.nio.file.{Files, Path, StandardOpenOption}

import akka.actor.{Actor, Props}

object FileActor {
    def props(): Props = Props(new FileActor)
}
class FileActor extends Actor {
    import org.json4s._
    import org.json4s.native.Serialization

    val map: scala.collection.mutable.Map[String /*symbol*/, String /*updated_at*/] = collection.mutable.Map.empty
    override def receive: Receive = {
        case x: JArray =>
            x.arr foreach { jValue =>
                val y = jValue removeField (_._1 == "instrument")
                val symbol = y \ "symbol" match {
                    case JString(a) => a
                    case _ => "NO_SYMBOL"
                }
                val updatedAt = y \ "updated_at" match {
                    case JString(a) => a
                    case _ => "NO_UPDATED_AT"
                }
                val previousUpdatedAt = map.getOrElse(symbol, "")
                if (previousUpdatedAt != updatedAt) {
                    map.put(symbol, updatedAt)
                    Files.write(
                        Path.of(".", s"$symbol.txt"),
                        Serialization.write(y)(DefaultFormats).getBytes(),
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND
                    )
                }
            }
    }
}
