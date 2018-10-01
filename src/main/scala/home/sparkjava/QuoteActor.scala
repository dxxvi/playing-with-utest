package home.sparkjava

import java.util.concurrent.TimeUnit

import akka.actor.{Actor, Props, Timers}
import akka.pattern.pipe
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.softwaremill.sttp._
import com.typesafe.config.Config
import home.sparkjava.message.{DailyQuoteReturn, GetDailyQuote, Tick}
import home.sparkjava.model.{DailyQuote, Quote}
import org.apache.logging.log4j.ThreadContext

import scala.concurrent.Future
import scala.concurrent.duration._

object QuoteActor {
    val NAME = "quoteActor"

    case class QuoteResponse(r: Response[List[Quote]])
    case class DailyQuoteResponse(r: Response[Map[String, List[DailyQuote]]])

    def props(config: Config): Props = Props(new QuoteActor(config))
}

class QuoteActor(config: Config) extends Actor with Timers with Util {
    import QuoteActor._
    import context.dispatcher

    val SERVER: String = config.getString("server")
    val authorization: String = if (config.hasPath("Authorization")) config.getString("Authorization") else "No token"
    implicit val httpBackend: SttpBackend[Future, Source[ByteString, Any]] = configureAkkaHttpBackend(config)
    var getDailyQuote = GetDailyQuote(Nil, System.currentTimeMillis / 1000)

    timers.startPeriodicTimer(Tick, Tick, 4.seconds)

    val _receive: Receive = {
        case Tick =>
            if (Main.instrument2Symbol.nonEmpty) {
                sttp
                        .get(uri"${SERVER}quotes/?symbols=${Main.instrument2Symbol.values.mkString(",")}")
                        .response(asString.map(Quote.deserialize))
                        .send()
                        .map(QuoteResponse) pipeTo self
//                logger.debug(s"Sent request to get quotes of ${Main.instrument2Symbol.size} symbols.")
            }
            val now = System.currentTimeMillis / 1000
            if (getDailyQuote.symbols.nonEmpty && now - getDailyQuote.ts > 15) {
                logger.debug(s"Getting daily quotes for ${getDailyQuote.symbols.size} symbols: ${getDailyQuote.symbols.mkString(" ")}")
                getDailyQuote.symbols.grouped(75) foreach { symbols =>
                    sttp
                            .get(uri"${SERVER}quotes/historicals/?interval=day&span=year&symbols=${symbols.mkString(",")}")
                            .response(asString.map(DailyQuote.deserialize))
                            .send()
                            .map(DailyQuoteResponse) pipeTo self
                    TimeUnit.SECONDS.sleep(2)
                }
                getDailyQuote = GetDailyQuote(Nil, now)
            }
        case QuoteResponse(Response(rawErrorBody, code, statusText, _, _)) => rawErrorBody fold (
                _ => logger.error(s"Error in getting quotes: $code $statusText"),
                a => {
//                    logger.debug(s"Received quotes for ${a.size} symbol. ${a.count(usableQuote)} is usable")
                    a foreach (q => if (usableQuote(q))
                        context.actorSelection(s"../${MainActor.NAME}/symbol-${q.symbol.get}") ! q
                    )
                }
        )
        case DailyQuoteResponse(Response(rawErrorBody, code, statusText, _, _)) => rawErrorBody fold (
                _ => logger.error(s"Error in getting daily quotes: $code $statusText"),
                a => {
                    a foreach(t => {
                        context.actorSelection(s"../${MainActor.NAME}/symbol-${t._1}") !
                                DailyQuoteReturn(t._2.filter(dq => dq.begins_at.isDefined && dq.low_price.isDefined && dq.high_price.isDefined))
                    })
                }
        )
        case GetDailyQuote(symbols, _) => getDailyQuote = GetDailyQuote(getDailyQuote.symbols ++ symbols, getDailyQuote.ts)
    }

    override def receive: Receive = sideEffect andThen _receive
    private def sideEffect: PartialFunction[Any, Any] = { case x => ThreadContext.put("symbol", QuoteActor.NAME); x }

    private def usableQuote(q: Quote): Boolean =
        q.symbol.isDefined && q.last_trade_price.isDefined && q.instrument.isDefined
}
