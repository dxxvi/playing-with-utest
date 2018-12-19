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

    case class FundamentalResponse(uri: Uri, r: Response[List[(String, String, Double, Double, Double)]])
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

    val _receive: Receive = {
        case Tick =>
            Main.instrument2Symbol.values
                    .grouped(75)
                    .foreach(symbols => {
                        val syms = symbols.mkString(",")
                        val uri = uri"${SERVER}quotes/historicals/?symbols=$syms&interval=5minute"
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
                        val name = InstrumentActor.instrument2NameSymbol.get(t._2).map(_._1).getOrElse("")
                        val fu = Fundamental(N, N, N, N, Some(t._4), N, Some(t._5), N, Some(t._3), N, t._2, name, 0, 0)
                        context.actorSelection(s"../${MainActor.NAME}/symbol-${t._1}") ! fu
                    }
                })
            )
        case x => logger.warn(s"Don't know what to do with $x")
    }

    override def receive: Receive = Main.clearThreadContextMap andThen _receive

    /**
     * s is like historicals-quotes.json /quotes/historicals/?symbols=AMD,TSLA&interval=5minute
     * @return List[(symbol, instrument, open, high_price, low_price)]
     */
    private def f(s: String): List[(String, String, Double, Double, Double)] = {
        import org.json4s._
        import org.json4s.native.JsonMethods._

        /**
          * @param jValue
          * {
          *   "symbol": "AMD",
          *   "instrument": "https://api.robinhood.com/instruments/940fc3f5-1db5-4fed-b452-f3a2e4562b5f/",
          *   "open_price": "24.770000",
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
        def g(jValue: JValue): (Option[String], Option[String], Double, Double, Double) = {
            val instrumentO = fromStringToOption[String](jValue, "instrument")
            val symbolO = fromStringToOption[String](jValue, "symbol")
            val openO = fromStringToOption[Double](jValue, "open_price")
            val historicalsJ = jValue \ "historicals"
            val x = for {
                instrument <- instrumentO
                symbol <- symbolO
                open <- openO
                jVals <- Some(historicalsJ.asInstanceOf[JArray].arr) if historicalsJ.isInstanceOf[JArray]
            } yield (
                    symbol,
                    instrument,
                    open,
                    jVals
                            .foldLeft((Double.MinValue, Double.MaxValue))((t, jVal) => {
                                val u = (fromStringToOption[Double](jVal, "high_price"), fromStringToOption[Double](jVal, "low_price"))
                                (
                                        if (u._1.exists(_ > t._1)) u._1.get else t._1,
                                        if (u._2.exists(_ < t._2)) u._2.get else t._2
                                )
                            })
            )
            if (x.isDefined) (Some(x.get._1), Some(x.get._2), x.get._3, x.get._4._1, x.get._4._2)
            else (None, None, Double.MinValue, Double.MinValue, Double.MaxValue)
        }

        parse(s) \ "results" match {
            case JArray(jValues) => jValues.map(g).collect {
                case (Some(symbol), Some(instrument), open, high_price, low_price) =>
                    (symbol, instrument, open, high_price, low_price)
            }
            case _ => Nil
        }
    }
}