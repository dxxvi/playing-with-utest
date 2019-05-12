package home

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

import akka.actor.ActorSystem
import akka.event.LoggingAdapter
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ContentType, ContentTypes, HttpEntity, HttpRequest, HttpResponse, StatusCodes}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.pattern.ask
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import akka.util.{ByteString, Timeout}
import com.softwaremill.sttp.{Id, SttpBackend}
import com.typesafe.config.ConfigFactory
import home.message.{Debug, M1, MakeOrder, Tick}
import home.model.{LastTradePrice, Order, Quote, Stats}
import home.util.{AccessTokenUtil, LoggingAdapterImpl, SttpBackendUtil}
import org.apache.tika.Tika
import org.json4s._
import org.json4s.native.Serialization

import scala.collection.LinearSeq
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._
import scala.io.StdIn
import scala.util.{Failure, Success}

object Main extends AccessTokenUtil with AppUtil with LastTradePriceUtil with OrderUtil with PositionUtil with QuoteUtil
        with SttpBackendUtil {
    implicit val log: LoggingAdapter = LoggingAdapterImpl

    case class HLLtpsTuple(high: Double /*todayHigh*/, low: Double /*todayLow*/, open: Double, previousClose: Double,
                           ltps: LinearSeq[LastTradePrice])

    def main(args: Array[String]): Unit = {
        implicit val actorSystem: ActorSystem = ActorSystem("R")
        implicit val materializer: ActorMaterializer = ActorMaterializer()
        implicit val timeout: Timeout = Timeout(5.seconds)

        val accessToken: String = getAccessTokenOrExit
        val credentialConfig = ConfigFactory.load("credentials.conf")
        implicit val be1: SttpBackend[Future, Source[ByteString, Any]] = configureAkkaHttpBackend(credentialConfig)
        implicit val be2: SttpBackend[Id, Nothing] = configureCoreJavaHttpBackend(credentialConfig)
        implicit val ec: ExecutionContext = actorSystem.dispatcher

        val tika: Tika = new Tika()

        val stockDatabase = StockDatabase.create(accessToken)

        val positions: Map[String /*instrument*/, (Double, String)] = Await.result(getAllPositions(accessToken), 9.seconds)

        val statsMap: Map[String /*symbol*/, Stats] = Await.result(getStats(accessToken, stockDatabase.allSymbols), 9.seconds)

        val webSocketActorRef = actorSystem.actorOf(WebSocketActor.props, WebSocketActor.NAME)

        val symbol2HLLtpsTuple: Map[String, HLLtpsTuple] =
            getSymbol2HLLtpsTuple(accessToken, stockDatabase.allSymbols)

        // create stock actors
        stockDatabase.iterate foreach { case (symbol, instrument) =>
            val hlLtpsTuple = symbol2HLLtpsTuple(symbol)
            val stats = statsMap(symbol)
            stats.high = hlLtpsTuple.high
            stats.low  = hlLtpsTuple.low
            stats.open = hlLtpsTuple.open
            stats.previousClose = hlLtpsTuple.previousClose
            val standardizedOrders: List[Order] = if (positions.getOrElse(instrument, (0, ""))._1 == 0) Nil else
                getAllStandardizedOrdersForInstrument(accessToken, instrument) filter (o =>
                    o.state != "cancelled" && o.state != "failed" && o.state != "rejected")
            actorSystem.actorOf(
                StockActor.props(accessToken, symbol, stats, standardizedOrders, webSocketActorRef, hlLtpsTuple.ltps),
                symbol
            )
        }

        val heartbeatActor = actorSystem.actorOf(
            HeartbeatActor.props(accessToken, stockDatabase.allSymbols, stockDatabase),
            HeartbeatActor.NAME
        )

        val route: Route =
            path("tick") {
                heartbeatActor ! Tick
                complete(HttpEntity(ContentTypes.`text/plain(UTF-8)`,
                    s"Send a tick at ${LocalDateTime.now.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)}"))
            } ~
            path("m1") {
                onComplete(webSocketActorRef ? M1.All) {
                    case Success(x: List[JObject]) => complete {
                        HttpEntity(ContentTypes.`application/json`, Serialization.write(JArray(x))(DefaultFormats))
                    }
                    case Success(x) => complete {
                        HttpResponse(status = StatusCodes.InternalServerError,
                            entity = HttpEntity(ContentTypes.`text/plain(UTF-8)`, s"webSocketActor returns a Success[${x.getClass}]"))
                    }
                    case Failure(ex) => complete {
                        HttpResponse(status = StatusCodes.InternalServerError,
                            entity = HttpEntity(ContentTypes.`text/plain(UTF-8)`, s"webSocketActor returns a Failure: ${ex.getMessage}"))
                    }
                }
            } ~
            path("clear-hash") {
                actorSystem.actorSelection("/user/*") ! M1.ClearHash
                complete(HttpEntity(ContentTypes.`application/json`,
                    s"""{"detail":"Sent M1.ClearHash to everybody at ${LocalDateTime.now.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)}"}"""))
            } ~
            path("cancel" / Segment) { orderId =>  // cancel an order
                complete(HttpEntity(ContentTypes.`application/json`,
                    s"""{"detail":"${cancelOrder(accessToken, orderId)}"}"""))
            } ~
            path("debug" / Segment) { actorName =>
                onComplete(actorSystem.actorSelection(s"/user/$actorName") ? Debug) {
                    case Success(jObject: JObject) => complete {
                        HttpEntity(ContentTypes.`application/json`, Serialization.write(jObject)(DefaultFormats))
                    }
                    case Success(x) => complete {
                        HttpResponse(status = StatusCodes.InternalServerError,
                            entity = HttpEntity(ContentTypes.`text/plain(UTF-8)`, s"Actor $actorName returns a Success[${x.getClass}]"))
                    }
                    case Failure(ex) => complete {
                        HttpResponse(status = StatusCodes.InternalServerError,
                            entity = HttpEntity(ContentTypes.`text/plain(UTF-8)`, s"Actor $actorName returns a Failure: ${ex.getMessage}"))
                    }
                }
            } ~
            path("order" / Segment / Segment / IntNumber / DoubleNumber ) { (symbol, side, quantity, price) =>
                onComplete(actorSystem.actorSelection(s"/user/${symbol.toUpperCase}") ? MakeOrder(side, quantity, price)) {
                    case Success(_) => complete(HttpEntity(ContentTypes.`application/json`, "{}"))
                    case Failure(ex) => complete {
                        HttpResponse(status = StatusCodes.InternalServerError,
                            entity = HttpEntity(ContentTypes.`application/json`, s"""{"detail":"${ex.getMessage}"}"""))
                    }
                }
            } ~ // order/amd/buy/2/29.04
            get {
                entity(as[HttpRequest]) { requestData =>
                    complete {
                        val fileName = requestData.uri.path.toString match {
                            case "/" | "" => "index.html"
                            case x        => x
                        }
                        var inputStream = classOf[StockActor].getResourceAsStream(s"/static/$fileName")
                        val bytes = inputStream.readAllBytes()
                        inputStream.close()
                        inputStream = classOf[StockActor].getResourceAsStream(s"/static/$fileName")
                        val mime = tika.detect(inputStream, fileName)
                        inputStream.close()
                        val contentType = ContentType.parse(mime) match {
                            case Right(x) => x
                            case Left(_) =>
                                log.error("Unable to determine the content type from {}", mime)
                                ContentTypes.`text/plain(UTF-8)`
                        }

                        HttpResponse(StatusCodes.OK, entity = HttpEntity(contentType, bytes))
                    }
                }
            }

        val bindingFuture = Http().bindAndHandle(route, "0.0.0.0", 4567)
        println("Server online at http://localhost:4567/\nPress RETURN to stop...")
        StdIn.readLine()
        bindingFuture
                .flatMap(_.unbind())
                .onComplete(_ => actorSystem.terminate())
    }

    private def getAccessTokenOrExit: String = {
        val atEither = retrieveAccessToken(ConfigFactory.load("credentials.conf"))
        if (atEither.isLeft) {
            log.error("Unable to get accessToken: {}", atEither.left.get)
            System.exit(-1)
        }
        atEither.right.get
    }

    private def getSymbol2HLLtpsTuple(accessToken: String, symbols: List[String])
                                     (implicit be: SttpBackend[Future, Source[ByteString, Any]],
                                               ec: ExecutionContext):
    Map[String, HLLtpsTuple] = {
        val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        val tupleList = Await.result(get5minQuotes(accessToken, symbols), 9.seconds)
        val symbol2LastTradePrice: Map[String, LastTradePrice] =
            Await.result(getLastTradePrices(accessToken, symbols), 9.seconds).toStream
                    .map(ltp => ltp.symbol -> ltp)
                    .toMap
        tupleList.toStream
                .map {
                    case (symbol, _, _, quotes) =>
                        var open = Double.NaN
                        val ltp = symbol2LastTradePrice(symbol)
                        val lastTradePrices = quotes
                                .collect {
                                    case Quote(beginsAt, _open, _, high, low, _) if beginsAt startsWith today =>
                                        if (open.isNaN) open = _open
                                        LastTradePrice((high + low)/2, ltp.previousClose, symbol, ltp.instrument,
                                            addSeconds(beginsAt, 9))
                                } :+ ltp
                        val (todayHigh, todayLow) = quotes.foldLeft((Double.MinValue, Double.MaxValue))((b, q) => {
                            val high = if (q.beginsAt startsWith today) math.max(b._1, q.high) else b._1
                            val low = if (q.beginsAt startsWith today) math.min(b._2, q.low) else b._2
                            (high, low)
                        })
                        if (lastTradePrices.isEmpty) {
                            log.error("No data to get the open price for today, so exit.")
                            System.exit(-1)
                        }
                        symbol -> HLLtpsTuple(todayHigh, todayLow, open, lastTradePrices.head.previousClose, lastTradePrices)
                }
                .toMap
    }
}
