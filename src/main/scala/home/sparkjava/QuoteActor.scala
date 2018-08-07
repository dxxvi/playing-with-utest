package home.sparkjava

import akka.actor.{Actor, Props, Timers}
import akka.pattern.pipe
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.softwaremill.sttp._
import com.typesafe.config.Config
import home.sparkjava.message.Tick
import home.sparkjava.model.Quote

import scala.concurrent.Future
import scala.concurrent.duration._

object QuoteActor {
    val NAME = "quoteActor"

    case class QuoteResponse(r: Response[List[Quote]])

    def props(config: Config): Props = Props(new QuoteActor(config))
}

class QuoteActor(config: Config) extends Actor with Timers with Util {
    import QuoteActor._
    import context.dispatcher

    val SERVER: String = config.getString("server")
    val authorization: String = if (config.hasPath("Authorization")) config.getString("Authorization") else "No token"
    implicit val httpBackend: SttpBackend[Future, Source[ByteString, Any]] = configureAkkaHttpBackend(config)

    timers.startPeriodicTimer(Tick, Tick, 4.seconds)

    val _receive: Receive = {
        case Tick =>
            if (Main.instrument2Symbol.nonEmpty)
                sttp
                    .get(uri"${SERVER}quotes/?symbols=${Main.instrument2Symbol.values.mkString(",")}")
                    .response(asString.map(Quote.deserialize))
                    .send()
                    .map(QuoteResponse) pipeTo self
        case QuoteResponse(Response(rawErrorBody, code, statusText, _, _)) => rawErrorBody fold (
                a => logger.error(s"Error in getting quotes: $code $statusText ${a.mkString}"),
                a => a foreach (q => if (q.symbol.isDefined && q.last_trade_price.isDefined)
                    context.actorSelection(s"../${MainActor.NAME}/symbol-${q.symbol.get}") ! q
                )
        )
    }

    override def receive: Receive = Main.clearThreadContextMap andThen _receive
}
