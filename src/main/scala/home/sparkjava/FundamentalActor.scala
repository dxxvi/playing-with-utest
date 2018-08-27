package home.sparkjava

import java.util

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

    case class FundamentalResponse(uri: Uri, r: Response[List[Fundamental]])
}

class FundamentalActor(config: Config) extends Actor with Timers with Util {
    import FundamentalActor._
    import context.dispatcher

    val SERVER: String = config.getString("server")
    implicit val httpBackend: SttpBackend[Future, Source[ByteString, Any]] = configureAkkaHttpBackend(config)
    val errorProneStocks: Set[String] = config.getString("error-prone-stocks").split(",").toSet
    var flag: Boolean = false

    timers.startPeriodicTimer(Tick, Tick, 15.seconds)

    val _receive: Receive = {
        case Tick =>
            flag = !flag
            if (flag)
                Main.instrument2Symbol.values
                        .filter(s => !errorProneStocks.contains(s))
                        .grouped(10)
                        .foreach { symbols => {
                            val uri: Uri = uri"${SERVER}fundamentals/?symbols=${symbols.mkString(",")}"
                            sttp
                                    .get(uri)
                                    .response(asString.map(Fundamental.deserialize))
                                    .send()
                                    .map(r => FundamentalResponse(uri, r)) pipeTo self
                        }}
            else
                Main.instrument2Symbol.values
                        .filter(s => errorProneStocks.contains(s))
                        .foreach { symbol => {
                            val uri: Uri = uri"${SERVER}fundamentals/?symbols=$symbol"
                            sttp
                                    .get(uri)
                                    .response(asString.map(Fundamental.deserialize))
                                    .send()
                                    .map(r => FundamentalResponse(uri, r)) pipeTo self
                        }}

        case FundamentalResponse(uri, Response(rawErrorBody, code, statusText, _, _)) =>
            rawErrorBody fold (
                a => logger.error(s"Error in getting fundamentals: $code $statusText, uri: $uri"),
                a => a.foreach(fu => Main.instrument2Symbol.get(fu.instrument) match {
                    case Some(symbol) => context.actorSelection(s"../${MainActor.NAME}/symbol-$symbol") ! fu
                    case None => logger.error(s"Got a fundamental w/ a strange instrument: $fu")
                })
            )
        case x => logger.warn(s"Don't know what to do with $x")
    }

    override def receive: Receive = Main.clearThreadContextMap andThen _receive


}