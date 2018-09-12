package home.sparkjava

import java.nio.file.{Files, Paths, StandardOpenOption}

import akka.actor.{Actor, ActorSystem, Props}
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.softwaremill.sttp._
import com.typesafe.config.{Config, ConfigFactory}
import home.TestUtil
import message.{AddSymbol, Tick}
import model._
import org.json4s._
import org.json4s.native.Serialization
import utest._

import scala.annotation.tailrec
import scala.collection.concurrent.TrieMap
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

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
            import collection.JavaConverters._
            import org.json4s._
            import org.json4s.native.JsonMethods._

            val config = ConfigFactory.load()
            val trieMap = TrieMap[String, String]()
            trieMap ++= config.getConfig("dow").entrySet().asScala.map(e => (e.getKey, e.getValue.unwrapped().asInstanceOf[String]))
            println(trieMap)
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
                            collection.mutable.SortedSet[OrderElement]()(Ordering.by[OrderElement, String](_.created_at.get)(Main.timestampOrdering.reverse))
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
                OrderElement(S, Some("2018-08-07T19:45:45.751474Z"), N, Some("ID19"), N, N, N, S, N, Some(3.4), Some("buy"),  Some(2), N),
                OrderElement(S, Some("2018-08-06T19:45:45.751474Z"), N, Some("ID18"), N, N, N, S, N, Some(3.7), Some("sell"), Some(2), N),
                OrderElement(S, Some("2018-08-05T19:45:45.751474Z"), N, Some("ID17"), N, N, N, S, N, Some(3.5), Some("buy"),  Some(2), N),
                OrderElement(S, Some("2018-08-04T19:45:45.751474Z"), N, Some("ID16"), N, N, N, S, N, Some(3.6), Some("sell"), Some(2), N)
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
                collection.mutable.SortedSet[OrderElement]()(Ordering.by[OrderElement, String](_.created_at.get)(Main.timestampOrdering.reverse))
            orders += OrderElement(S, Some("2018-08-07T19:45:45.751474Z"), N, Some("ID19"), N, N, N, S, N, Some(3.4), Some("buy"),  Some(2), N)
            orders += OrderElement(S, Some("2018-08-06T19:45:45.751474Z"), N, Some("ID18"), N, N, N, S, N, Some(3.7), Some("sell"), Some(2), N)
            orders += OrderElement(S, Some("2018-08-05T19:45:45.751474Z"), N, Some("ID17"), N, N, N, S, N, Some(3.5), Some("buy"),  Some(2), N)
            orders += OrderElement(S, Some("2018-08-04T19:45:45.751474Z"), N, Some("ID16"), N, N, N, S, N, Some(3.6), Some("sell"), Some(2), N)

            orders -= OrderElement(S, Some("2018-08-06T19:45:45.751474Z"), N, Some("1818"), N, N, N, S, N, Some(3.7), Some("sell"), Some(2), N)
            orders += OrderElement(S, Some("2018-08-06T19:45:45.751474Z"), N, Some("1818"), N, N, N, S, N, Some(3.7), Some("sell"), Some(2), N)
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

        "Test shouldBuySell" - {
            val N = None
            val lastTimeSell = 0
            val lastTimeBuy  = 0
            val estimatedLow: Double = 0
            var fu: Fundamental = Fundamental(N, N, N, N, N, N, N, N, N, N, "")

            def isToday(s: String): Boolean = s.startsWith("2018-09-05")

            // returns (action, quantity, price)
            def shouldBuySell(oes: List[OrderElement], ltp: Double /* last trade price */): Option[(String, Int, Double)] = {
                val hasBuy  = oes.exists(oe => oe.state.exists(_.contains("confirmed")) && oe.side.contains("buy"))
                val hasSell = oes.exists(oe => oe.state.exists(_.contains("confirmed")) && oe.side.contains("sell"))
                val now = System.currentTimeMillis / 1000
                val decisionFunction: PartialFunction[OrderElement, (String, Int, Double)] = {
                    case OrderElement(_, Some(created_at), _, _, Some(cumulative_quantity), _, _, _, Some(price), _, Some("buy"), _, _)
                        if isToday(created_at) && (ltp > 1.007*price) && (now - lastTimeSell > 15) && !hasSell =>
                        ("sell", cumulative_quantity, (ltp*100).round.toDouble / 100)
                    case OrderElement(_, Some(created_at), _, _, Some(cumulative_quantity), _, _, _, Some(price), _, Some("buy"), _, _)
                        if !isToday(created_at) && (ltp > 1.01*price) && (now - lastTimeSell > 15) && !hasSell =>
                        ("sell", cumulative_quantity, (ltp*100).round.toDouble / 100)
                    case OrderElement(_, Some(created_at), _, _, Some(cumulative_quantity), _, _, _, Some(price), _, Some("sell"), _, _)
                        if isToday(created_at) && (ltp < .992*price) && (now - lastTimeBuy > 15) && !hasBuy =>
                        ("buy", cumulative_quantity, (ltp*100).round.toDouble / 100)
                    case OrderElement(_, Some(created_at), _, _, Some(cumulative_quantity), _, _, _, Some(price), _, Some("sell"), _, _)
                        if !isToday(created_at) && (ltp < .99*price) && (now - lastTimeBuy > 15) && !hasBuy =>
                        ("buy", cumulative_quantity, (ltp*100).round.toDouble / 100)
                    case OrderElement(_, Some(created_at), _, _, Some(cumulative_quantity), _, _, _, Some(price), _, Some("buy"), _, None)
                        if isToday(created_at) && (ltp < .987*price) && fu.low.exists(ltp < 1.005*_) && (ltp < estimatedLow) && (now - lastTimeBuy > 15) && !hasBuy =>
                        ("buy", cumulative_quantity, (ltp*100).round.toDouble / 100)
                    case OrderElement(_, Some(created_at), _, _, Some(cumulative_quantity), _, _, _, Some(price), _, Some("buy"), _, None)
                        if !isToday(created_at) && (ltp < .985*price) && fu.low.exists(ltp < 1.005*_) && (ltp < estimatedLow) && (now - lastTimeBuy > 15) && !hasBuy =>
                        ("buy", cumulative_quantity, (ltp*100).round.toDouble / 100)
                }
                val filledOEs = oes.filter(_.state.contains("filled"))
                val decision1: Option[(String, Int, Double)] = filledOEs.headOption collect decisionFunction
                val decision2 = filledOEs.dropWhile(_.matchId.isDefined).headOption collect decisionFunction
                if (decision1.isEmpty) decision2
                else if (decision2.isEmpty) decision1
                else for {
                    d1 <- decision1
                    d2 <- decision2
                } yield if (d1._1 == "sell") d1 else d2
            }

            var orderElements = List(
//                new OrderElement("2018-09-07T13:48:49", "a9n9-6fc12",  0,  "confirmed", 4.6,  "sell", None),
                new OrderElement("2018-08-31T15:59:35", "22md-1n6io0", 8,  "filled",    4.45, "buy",  Some("3c")),
                new OrderElement("2018-08-31T14:47:22", "4x3r-jb5qn",  8,  "filled",    4.5,  "sell", Some("3c")),
                new OrderElement("2018-07-24T15:32:41", "kapg-zql4x",  8,  "filled",    4.65, "sell", Some("3c")),
                new OrderElement("2018-07-23T15:12:25", "d7rx-6tru9",  8,  "filled",    4.45, "buy",  Some("n5")),
                new OrderElement("2018-07-13T17:23:54", "xtsv-uml00",  8,  "filled",    4.65, "sell", Some("n5")),
                new OrderElement("2018-07-10T14:06:43", "4jn7-wqv9n",  8,  "filled",    4.3,  "buy",  Some("3c")),
                new OrderElement("2018-07-09T14:14:43", "1ynn-2ec4m",  8,  "filled",    4.45, "buy",  None),
                new OrderElement("2018-06-04T14:26:59", "1gqh-x62sqv", 16, "filled",    4.8,  "sell", Some("lp")),
                new OrderElement("2018-05-17T14:21:48", "19ff-x9hg6y", 32, "filled",    4.7,  "sell", Some("ji")),
                new OrderElement("2018-05-10T19:59:47", "c540-idkof",  32, "filled",    4.5,  "buy",  Some("ji")),
                new OrderElement("2018-05-10T19:51:46", "2ftr-2cx010", 16, "filled",    4.55, "buy",  Some("lp")),
                new OrderElement("2018-05-10T19:37:25", "2288-5ayu10", 8,  "filled",    4.6,  "buy",  None),
                new OrderElement("2018-05-10T17:35:05", "b3xa-o2lo9",  4,  "filled",    4.65, "buy",  None),
                new OrderElement("2018-05-10T15:33:35", "31k8-iazu6",  4,  "filled",    4.7,  "buy",  None),
                new OrderElement("2018-05-10T13:49:16", "f3r3-l033p",  4,  "filled",    4.75, "buy",  None),
                new OrderElement("2018-05-04T13:33:39", "2emn-b42exw", 2,  "filled",    4.85, "buy",  None),
                new OrderElement("2018-05-03T18:49:04", "2bne-zoskcl", 2,  "filled",    4.9,  "buy",  None),
                new OrderElement("2018-05-03T17:50:54", "jtfn-po1ua",  2,  "filled",    4.95, "buy",  None),
                new OrderElement("2018-05-03T17:50:29", "22iz-gpove5", 2,  "filled",    5,    "buy",  None),
                new OrderElement("2018-05-03T14:56:26", "o1a1-ejem9",  1,  "filled",    5.05, "buy",  None),
                new OrderElement("2018-05-03T14:48:52", "1lqa-mj4gau", 1,  "filled",    5.1,  "buy",  None),
                new OrderElement("2018-05-03T14:48:34", "2l0s-2k7fgw", 1,  "filled",    5.15, "buy",  None),
                new OrderElement("2018-04-13T19:02:50", "1xcr-s3waeo", 1,  "filled",    5.25, "buy",  None)
            )
            fu = Fundamental(N, N, N, N, Some(4.8), N, Some(4.3), N, N, N, "")
            var decision = shouldBuySell(orderElements, 4.4)
            println(decision)
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