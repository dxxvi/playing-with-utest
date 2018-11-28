package home.sparkjava

import java.nio.file.{Files, Paths, StandardOpenOption}
import java.util.function.Consumer

import akka.actor.{Actor, ActorSystem, Props}
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.softwaremill.sttp._
import com.typesafe.config.{Config, ConfigFactory}
import home.TestUtil
import message.{AddSymbol, Tick}
import model._
import org.json4s._
import org.json4s.native.JsonMethods._
import utest._

import scala.annotation.tailrec
import scala.collection.concurrent.TrieMap
import scala.compat.java8.functionConverterImpls.AsJavaConsumer
import scala.concurrent.{Await, Future}
import scala.concurrent.duration._

object ActorTests extends TestSuite with Util with TestUtil {
    val tests = Tests {
        "Test DefaultWatchListActor" - {
            val config = ConfigFactory.load()
            val actorSystem = ActorSystem("R")

            actorSystem.actorOf(InstrumentActor.props(config), InstrumentActor.NAME)
            val dwlActor = actorSystem.actorOf(DefaultWatchListActor.props(config), DefaultWatchListActor.NAME)
            dwlActor ! Tick

            Thread.sleep(9019)

            println(s"Main.instrument2Symbol: ${Main.instrument2Symbol}")

            actorSystem.terminate()
        }

        "Test Everything" - {
            val config: Config = ConfigFactory.load()
            val actorSystem = ActorSystem("R")
            val mainActor = actorSystem.actorOf(MainActor.props(config), MainActor.NAME)
            val dwlActor = actorSystem.actorOf(DefaultWatchListActor.props(config), DefaultWatchListActor.NAME)
            actorSystem.actorOf(InstrumentActor.props(config), InstrumentActor.NAME)

            dwlActor ! Tick
            Thread.sleep(29482)

            mainActor ! AddSymbol("TESTING")
            dwlActor ! Tick
            Thread.sleep(9482)

            actorSystem.terminate()
        }

        "Test json4s" - {
            import org.json4s._
            import org.json4s.native.JsonMethods._
            import org.json4s.native.Serialization

            val c: collection.mutable.Set[String] = collection.mutable.HashSet[String]()
            c += "A"
            c += "B"
            println(Serialization.write(c)(DefaultFormats))

            val results = Quote.deserialize(readTextFileFromTestResource("robinhood", "quotes.json"))
            println(results)
        }

        "Test TypeSafe Config" - {
            import org.json4s._
            import org.json4s.native.JsonMethods._
            import com.softwaremill.sttp._

            val config = ConfigFactory.load()
            implicit val httpBackend: SttpBackend[Future, Source[ByteString, Any]] = configureAkkaHttpBackend(config)
            Main.buildStocksDB(config)
            val y: Future[Response[List[String]]] = sttp
                    .header("Authorization", config.getString("Authorization"))
                    .get(uri"https://api.robinhood.com/watchlists/Default/")
                    .response(asString.map(s => parse(s).asInstanceOf[JObject].values.get("results").fold(
                        List[String]()
                    ) {
                        case results: List[Map[String, _]] =>
                            results.map(m => m.get("instrument")).collect { case Some(x) => x.asInstanceOf[String] }
                        case _ => List[String]()
                    }))
                    .send()
            Await.result(y, 9.seconds).rawErrorBody.fold(
                _ => println("Error"),
                _.foreach(i => InstrumentActor.instrument2NameSymbol.get(i) match {
                    case Some((_, symbol)) => println(
                        s"""$symbol {
                           |  buy = 1
                           |  sell = 500
                           |}""".stripMargin)
                    case _ => println(s"strange instrument: $i")
                })
            )
        }

        "Get all tradeable stocks" - {
            val actorSystem = ActorSystem("R")
            val actor = actorSystem.actorOf(Props(new AllTradeableStocks))
            actor ! "https://api.robinhood.com/instruments/"

            Thread.sleep(82419)
            actorSystem.terminate()
        }

        "Test PositionActor" - {
            val actorSystem = ActorSystem("R")
            val config = ConfigFactory.load()
            val positionActor = actorSystem.actorOf(PositionActor.props(config), PositionActor.NAME)
            positionActor ! Tick

            Thread.sleep(82419)
            actorSystem.terminate()
        }

        "Test assign match id" - {
            import model.OrderElement
            def combineIds(s1: String, s2: String): String = if (s1 < s2) s"$s1-$s2" else s"$s2-$s1"

            def doBuySellMatch(o1: OrderElement, o2: OrderElement): Option[Boolean] = for {
                side1 <- o1.side
                side2 <- o2.side
                if side1 == "buy" && side2 == "sell"
                quantity1 <- o1.quantity
                quantity2 <- o2.quantity
                if quantity1 == quantity2
                average_price1 <- o1.average_price
                average_price2 <- o2.average_price
                if average_price1 < average_price2
            } yield true

            def assignMatchId(tbOrders: List[OrderElement]): List[OrderElement] = {
                @annotation.tailrec
                def f(withMatchIds: List[OrderElement], working: List[OrderElement]): List[OrderElement] = {
                    var _withMatchIds = List[OrderElement]()
                    var _working = List[OrderElement]()
                    var i = 0
                    var matchFound = false
                    while (i < working.length - 1 && !matchFound) {
                        if (doBuySellMatch(working(i), working(i + 1)).contains(true)) {
                            matchFound = true
                            val buySellT: (OrderElement, OrderElement) =
                                if (i + 2 < working.length && doBuySellMatch(working(i + 2), working(i + 1)).contains(true)) {
                                    if (working(i).average_price.get < working(i + 2).average_price.get)
                                        (working(i + 2), working(i + 1))
                                    else (working(i), working(i+1))
                                }
                                else (working(i), working(i + 1))

                            val matchId = combineIds(buySellT._1.id.get, buySellT._2.id.get)
                            val buy = buySellT._1.copy(matchId = Some(matchId))
                            val sell = buySellT._2.copy(matchId = Some(matchId))
                            _withMatchIds = withMatchIds :+ buy :+ sell
                            _working = working.filter(oe => !oe.id.contains(buy.id.get) && !oe.id.contains(sell.id.get))
                        }
                        else if (doBuySellMatch(working(i + 1), working(i)).contains(true)) {
                            matchFound = true
                            val buySellT: (OrderElement, OrderElement) =
                                if (i + 2 < working.length && doBuySellMatch(working(i+1), working(i+2)).contains(true)) {
                                    if (working(i).average_price.get < working(i+2).average_price.get) {
                                        (working(i+1), working(i))
                                    }
                                    else (working(i+1), working(i + 2))
                                }
                                else (working(i + 1), working(i))

                            val matchId = combineIds(buySellT._1.id.get, buySellT._2.id.get)
                            val buy = buySellT._1.copy(matchId = Some(matchId))
                            val sell = buySellT._2.copy(matchId = Some(matchId))
                            _withMatchIds = withMatchIds :+ buy :+ sell
                            _working = working.filter(oe => !oe.id.contains(buy.id.get) && !oe.id.contains(sell.id.get))
                        }
                        i += 1
                    }

                    if (matchFound) f(_withMatchIds, _working)
                    else {
                        val set: collection.mutable.SortedSet[OrderElement] =
                            collection.mutable.SortedSet[OrderElement]()(Ordering.by[OrderElement, String](_.created_at)(Main.timestampOrdering.reverse))
                        set ++= withMatchIds
                        set ++= working
                        set.toList
                    }
                }
                f(List[OrderElement](), tbOrders)
            }

            val N = None
            val S = Some("")
            val orders = assignMatchId(List[OrderElement](
                OrderElement("_", "2018-08-07T19:45:45.751474Z", N, Some("ID19"), N, N, "_", S, N, Some(3.4), Some("buy"),  Some(2), N),
                OrderElement("_", "2018-08-06T19:45:45.751474Z", N, Some("ID18"), N, N, "_", S, N, Some(3.7), Some("sell"), Some(2), N),
                OrderElement("_", "2018-08-05T19:45:45.751474Z", N, Some("ID17"), N, N, "_", S, N, Some(3.5), Some("buy"),  Some(2), N),
                OrderElement("_", "2018-08-04T19:45:45.751474Z", N, Some("ID16"), N, N, "_", S, N, Some(3.6), Some("sell"), Some(2), N)
            ))
            println(s"${orders.map(_.toString).mkString("\n")}")
        }

        "Test DailyQuote.deserialize" - {
            val map = DailyQuote.deserialize(readTextFileFromTestResource("robinhood", "quotes-daily.json"))
            println(map)
        }

        "Test Scala's mutable SortedSet" - {
            val N = None
            val S = Some("")
            val orders: collection.mutable.SortedSet[OrderElement] =
                collection.mutable.SortedSet[OrderElement]()(Ordering.by[OrderElement, String](_.created_at)(Main.timestampOrdering.reverse))
            orders += OrderElement("_", "2018-08-07T19:45:45.751474Z", N, Some("ID19"), N, N, "_", S, N, Some(3.4), Some("buy"),  Some(2), N)
            orders += OrderElement("_", "2018-08-06T19:45:45.751474Z", N, Some("ID18"), N, N, "_", S, N, Some(3.7), Some("sell"), Some(2), N)
            orders += OrderElement("_", "2018-08-05T19:45:45.751474Z", N, Some("ID17"), N, N, "_", S, N, Some(3.5), Some("buy"),  Some(2), N)
            orders += OrderElement("_", "2018-08-04T19:45:45.751474Z", N, Some("ID16"), N, N, "_", S, N, Some(3.6), Some("sell"), Some(2), N)
            orders -= OrderElement("_", "2018-08-06T19:45:45.751474Z", N, Some("1818"), N, N, "_", S, N, Some(3.7), Some("sell"), Some(2), N)
            orders += OrderElement("_", "2018-08-06T19:45:45.751474Z", N, Some("1818"), N, N, "_", S, N, Some(3.7), Some("sell"), Some(2), N)
            println(orders)
        }

        "Test lastRoundOrders" - {
            def g(orders: Seq[OrderElement]): List[OrderElement] = {
                @tailrec
                def f(givenSum: Int, currentSum: Int, building: List[OrderElement], remain: List[OrderElement]): List[OrderElement] = {
                    if (givenSum == currentSum || remain == Nil) building
                    else f(
                        givenSum,
                        currentSum + (if (remain.head.side.contains("buy")) remain.head.quantity.get else -remain.head.quantity.get),
                        building :+ remain.head,
                        remain.tail
                    )
                }

                f(33, 0, Nil, orders.toList)
            }
            val _orders = Seq[OrderElement]()
            println(g(_orders))
        }

        "Find low-volume stocks" - {
            import org.json4s._
            import org.json4s.native.JsonMethods._
            import com.softwaremill.sttp._
            import scala.collection.JavaConverters._

            var config = ConfigFactory.load()
            implicit val httpBackend: SttpBackend[Future, Source[ByteString, Any]] = configureAkkaHttpBackend(config)
            config = ConfigFactory.load("stock.conf")
            val goodSymbols = config.getConfig("soi").root().keySet().asScala.filter(symbol => {
                val fundamentals = s"https://api.robinhood.com/fundamentals/$symbol/"
                val y: Future[Response[JObject]] = sttp
                        .get(uri"$fundamentals")
                        .response(asString.map(s => parse(s).asInstanceOf[JObject]))
                        .send()
                var result = false
                Await.result(y, 9.seconds).rawErrorBody.fold(
                    _ => println("Error"),
                    jObject => jObject \ "average_volume_2_weeks" match {
                        case JString(x) if x.toDouble > 1567890 => result = true
                        case _ =>
                    }
                )
                result
            })
        }
    }
}

case class ResponseWrapper(r: Response[(Option[String], List[(String, String)])])

class AllTradeableStocks extends Actor with Util {
    import context.dispatcher
    import akka.pattern.pipe
    import scala.collection.JavaConverters._

    val list: collection.mutable.ArrayBuffer[(String, String)] = collection.mutable.ArrayBuffer[(String, String)]()
    val config: Config = ConfigFactory.load()
    val SERVER: String = config.getString("server")
    implicit val httpBackend: SttpBackend[Future, Source[ByteString, Any]] = configureAkkaHttpBackend(config)

    override def receive: Receive = {
        case url: String => sttp
                .get(uri"$url")
                .response(asString.map(extract))
                .send()
                .map(ResponseWrapper) pipeTo self
        case ResponseWrapper(Response(rawErrorBody, code, statusText, _, _)) => rawErrorBody.fold(
            a => println(s"Error: $code $statusText"),
            a => {
                Files.write(
                    Paths.get("/dev", "shm", "test.txt"),
                    a._2.map(t => f"""${t._1}%-5s = "${t._2}%s"""").asJava,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND
                )
                println(a._2.size)
                if (a._1.isDefined) self ! a._1.get
            }
        )
    }

    private def extract(s: String): (Option[String], List[(String, String)]) = {
        import org.json4s._
        import org.json4s.native.JsonMethods._
        val json = parse(s)
        val next: Option[String] = json \ "next" match {
            case JString(x) => Some(x)
            case _ => None
        }
        val stocks: List[(String, String)] = json \ "results" match {
            case JArray(jValues) => jValues collect {
                case x: JValue if isTradeable(x) =>
                    val symbolO = x \ "symbol" match {
                        case JString(symbol) => Some(symbol)
                    }
                    val urlO = x \ "url" match {
                        case JString(url) => Some(url)
                    }
                    (symbolO.get, urlO.get)
            }
            case _ => List[(String, String)]()
        }
        (next, stocks)
    }

    private def isTradeable(x: JValue): Boolean = {
        (x \ "tradeable" match {
            case JBool(tradeable) => tradeable
            case _ => false
        }) && (x \ "country" match {
            case JString(country) => country == "US"
            case _ => false
        }) && (x \ "state" match {
            case JString(state) => state == "active"
            case _ => false
        }) && (x \ "symbol").isInstanceOf[JString] && (x \ "url").isInstanceOf[JString]
    }
}