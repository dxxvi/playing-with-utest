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

    case class FundamentalResponse(uri: Uri, r: Response[List[(String, String, Double, Double)]])
    case class FundamentalReview(symbol: String)  // used when user wants to check the fundamental of a new symbol
}

class FundamentalActor(config: Config) extends Actor with Timers with Util {
    import FundamentalActor._
    import context.dispatcher

    val SERVER: String = config.getString("server")
    implicit val httpBackend: SttpBackend[Future, Source[ByteString, Any]] = configureAkkaHttpBackend(config)
    val errorProneStocks: Set[String] = config.getString("error-prone-stocks").split(",").toSet
    var flag: Boolean = false

    timers.startPeriodicTimer(Tick, Tick, 5.minutes)

/*
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
        case FundamentalReview(_) => logger.warn(s"Need to implement the FundamentalReview")
        case x => logger.warn(s"Don't know what to do with $x")
    }
*/
    val _receive: Receive = {
        case Tick =>
            Main.instrument2Symbol.values
                    .grouped(75)
                    .foreach(symbols => {
                        val uri = uri"${SERVER}quotes/historicals/?symbols=${symbols.mkString(",")}&interval=5minute"
                        sttp
                                .get(uri)
                                .response(asString.map(f))
                                .send()
                                .map(r => FundamentalResponse(uri, r)) pipeTo self
                    })
        case FundamentalResponse(uri, Response(rawErrorBody, code, statusText, _, _)) =>
            val N = None
            rawErrorBody fold (
                _ => logger.error(s"Error in getting fundamentals: $code $statusText, uri: $uri"),
                a => a.foreach(t => {
                    if (t._3 > Double.MinValue && t._4 < Double.MaxValue) {
                        val fu = Fundamental(N, N, N, N, Some(t._3), N, Some(t._4), N, N, N, t._2)
                        context.actorSelection(s"../${MainActor.NAME}/symbol-${t._1}") ! fu
                    }
                })
            )
    }

    override def receive: Receive = Main.clearThreadContextMap andThen _receive

    // s is like historicals-quotes.json /quotes/historicals/?symbols=AMD,TSLA&interval=5minute
    private def f(s: String): List[(String, String, Double, Double)] = {
        import org.json4s._
        import org.json4s.native.JsonMethods._

        /**
          * @param jValue
          * {
          *   "symbol": "AMD",
          *   "instrument": "https://api.robinhood.com/instruments/940fc3f5-1db5-4fed-b452-f3a2e4562b5f/",
          *   "historicals": [
          *     {
          *       "high_price": "25.180000",
          *       "low_price": "24.550000",
          *     },
          *     ...
          *   ]
          * }
          * @return (symbol, instrument, high_price, low_price)
          */
        def g(jValue: JValue): (Option[String], Option[String], Double, Double) = {
            val instrumentO = fromStringToOption[String](jValue, "instrument")
            val symbolO = fromStringToOption[String](jValue, "symbol")
            val historicalsJ = jValue \ "historicals"
            val x = for {
                instrument <- instrumentO
                symbol <- symbolO
                jVals <- Some(historicalsJ.asInstanceOf[JArray].arr) if historicalsJ.isInstanceOf[JArray]
            } yield (
                    symbol,
                    instrument,
                    jVals
                            .foldLeft((Double.MinValue, Double.MaxValue))((t, jVal) => {
                                val u = (fromStringToOption[Double](jVal, "high_price"), fromStringToOption[Double](jVal, "low_price"))
                                (
                                        if (u._1.exists(_ > t._1)) u._1.get else t._1,
                                        if (u._2.exists(_ < t._2)) u._2.get else t._2
                                )
                            })
            )
            if (x.isDefined) (Some(x.get._1), Some(x.get._2), x.get._3._1, x.get._3._2)
            else (None, None, Double.MinValue, Double.MaxValue)
        }

        parse(s) \ "results" match {
            case JArray(jValues) => jValues.map(g).collect {
                case (Some(symbol), Some(instrument), high_price, low_price) => (symbol, instrument, high_price, low_price)
            }
            case _ => Nil
        }
    }
}