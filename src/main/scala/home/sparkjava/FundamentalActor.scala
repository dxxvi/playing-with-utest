package home.sparkjava

import akka.actor.{Actor, Props, Timers}
import akka.pattern.pipe
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.typesafe.config.Config
import com.softwaremill.sttp._
import model.Fundamental
import message.Tick

import scala.concurrent.Future
import scala.concurrent.duration._

object FundamentalActor {
    val NAME = "fundamentalActor"
    def props(config: Config): Props = Props(new FundamentalActor(config))

    case class FundamentalResponse(r: Response[List[Fundamental]])
}

class FundamentalActor(config: Config) extends Actor with Timers with Util {
    import FundamentalActor._
    import context.dispatcher

    val SERVER: String = config.getString("server")
    implicit val httpBackend: SttpBackend[Future, Source[ByteString, Any]] = configureAkkaHttpBackend(config)

    timers.startPeriodicTimer(Tick, Tick, 39.seconds)

    val _receive: Receive = {
        case Tick => Main.instrument2Symbol.values.grouped(10).foreach { symbols =>
            if (Main.requestCount.get < 19) {
                sttp
                        .get(uri"${SERVER}fundamentals/?symbols=${symbols.mkString(",")}")
                        .response(asString.map(Fundamental.deserialize))
                        .send()
                        .map(FundamentalResponse) pipeTo self
                Main.requestCount.incrementAndGet()
            }
            else logger.debug(s"Too many (${Main.requestCount.get}) requests, " +
                    s"so no getting fundamentals for ${symbols.mkString(",")}")
        }
        case FundamentalResponse(Response(rawErrorBody, code, statusText, _, _)) =>
            Main.requestCount.decrementAndGet()
            rawErrorBody.fold(
                a => logger.error(s"Error in getting fundamentals: $code $statusText ${a.mkString}"),
                a => a.foreach(fu => Main.instrument2Symbol.get(fu.instrument) match {
                    case Some(symbol) => context.actorSelection(s"../${MainActor.NAME}/symbol-$symbol") ! fu
                    case None => logger.error(s"Got a fundamental w/ a strang instrument: $fu")
                })
            )
        case x => logger.warn(s"Don't know what to do with $x")
    }

    override def receive: Receive = Main.clearThreadContextMap andThen _receive


}