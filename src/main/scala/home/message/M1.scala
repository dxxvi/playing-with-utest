package home.message

import org.json4s.JObject

object M1 {
    case object All                    // WebSocketActor will send back the buffer then clear it
    case object ClearHash              // Sent to StockActor's to clear their m1hashes
}

case class M1(jObject: JObject)