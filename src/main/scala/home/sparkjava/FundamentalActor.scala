package home.sparkjava

import concurrent.duration._
import akka.actor.{Actor, Props, Timers}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse, StatusCodes, Uri}
import akka.http.scaladsl.settings.ConnectionPoolSettings
import akka.stream.{ActorMaterializer, ActorMaterializerSettings}
import akka.util.ByteString
import com.typesafe.config.Config
import home.sparkjava.model.HistoricalQuoteProtocol.HistoricalQuoteJsonFormat
import model.Fundamental
import org.apache.logging.log4j.ThreadContext
import org.apache.logging.log4j.scala.Logging

import scala.util.{Failure, Success}

object FundamentalActor {
    val NAME = "fundamentalActor"
    def props(config: Config): Props = Props(new FundamentalActor(config))
}

class FundamentalActor(config: Config) extends Actor with Timers with Logging with Util {
    import akka.pattern.pipe
    import context.dispatcher
    import FundamentalActor._
    import spray.json._
    import model.FundamentalProtocol._

    implicit val materializer: ActorMaterializer = ActorMaterializer(ActorMaterializerSettings(context.system))


    val SERVER: String = config.getString("server")
    val settings: ConnectionPoolSettings = getConnectionPoolSettings(config, context.system)
    val http = Http(context.system)

    timers.startPeriodicTimer(Tick, Tick, 19824.millis)
    var debug = false

    val _receive: Receive = {
        case Tick if Main.instrument2Symbol.nonEmpty =>
            Main.instrument2Symbol.values.grouped(10).foreach { symbols =>
                val uri = Uri(SERVER + s"fundamentals/?symbols=${symbols.mkString(",")}")
                http.singleRequest(HttpRequest(uri = uri), settings = settings) pipeTo self
            }
        case Tick =>  // do nothing
        case HttpResponse(StatusCodes.OK, _, entity, _) =>
            entity.dataBytes.runFold(ByteString(""))(_ ++ _).foreach { body =>
                getFundamentals(body.utf8String).foreach { fundamental =>
                    Main.instrument2Symbol.get(fundamental.instrument).foreach { symbol =>
                        context.actorSelection(s"../${MainActor.NAME}/symbol-$symbol") ! fundamental
                    }
                }
            }
        case HttpResponse(statusCode, _, entity, _) =>
            entity.dataBytes.runFold(ByteString(""))(_ ++ _).foreach { body =>
                logger.error(s"Error in getting fundamentals: $statusCode, body: ${body.utf8String}")
            }
        case "DEBUG_ON" => debug = true
        case "DEBUG_OFF" => debug = false
        case symbol: String =>
            logger.debug(s"Receive '$symbol', check if you see me a lot.")
            val fundamentalResponseFuture =
                http.singleRequest(HttpRequest(uri = Uri(SERVER + s"fundamentals/$symbol/")), settings = settings)
            val httpRequest = HttpRequest(uri = Uri(SERVER + s"quotes/historicals/$symbol/?interval=5minute&span=week"))
            val quoteResponseFuture = http.singleRequest(httpRequest, settings = settings)
            val responseFuture = for {
                fundamentalResponse <- fundamentalResponseFuture
                quoteResponse <- quoteResponseFuture
            } yield (fundamentalResponse, quoteResponse)
            responseFuture onComplete {
                case Success((HttpResponse(fStatusCode, _, fEntity, _), HttpResponse(qStatusCode, _, qEntity, _))) =>
                    if (debug) logger.debug(s"Fundamental response: $fStatusCode; Historical quote response: $qStatusCode")
                    if (fStatusCode == StatusCodes.OK && qStatusCode == StatusCodes.OK) {
                        fEntity.dataBytes.runFold(ByteString(""))(_ ++ _).foreach { fBody =>
                            qEntity.dataBytes.runFold(ByteString(""))(_ ++ _).foreach { qBody =>
                                val map = Map[String, JsValue](
                                    "fundamental" -> fBody.utf8String.parseJson,
                                    "quotes" -> JsArray(getHistoricalQuotes(qBody.utf8String)
                                            .map(hq => HistoricalQuoteJsonFormat.write(hq)))
                                )
                                context.actorSelection(s"../${WebSocketActor.NAME}") ! s"FUNDAMENTAL_REVIEW: $symbol: ${map.toJson.compactPrint}"
                            }
                        }
                    }
                    else {
                        logger.error(s"Unable to get fundamental or historical quotes for $symbol")
                        fEntity.dataBytes.runFold(ByteString(""))(_ ++ _).foreach { body =>
                            logger.error(s"Fundamental: $fStatusCode - ${body.utf8String}")
                        }
                        qEntity.dataBytes.runFold(ByteString(""))(_ ++ _).foreach { body =>
                            logger.error(s"Historical quotes: $qStatusCode - ${body.utf8String}")
                        }
                    }
                case Failure(exception) =>
                    logger.error(s"Unable to get fundamental or weekly quotes for $symbol: $exception")
            }
        case x => logger.debug(s"Don't know what to do with $x yet")
    }

    val sideEffect: PartialFunction[Any, Any] = {
        case x =>
            ThreadContext.clearMap()
            x
    }

    override def receive: Receive = sideEffect andThen _receive
}
