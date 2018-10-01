package home.sparkjava

import java.nio.file.{Files, Paths, StandardOpenOption}
import java.time.Instant

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
                    case OrderElement(_, created_at, _, _, Some(cumulative_quantity), _, _, _, Some(price), _, Some("buy"), _, _)
                        if !hasSell && isToday(created_at) && (ltp > 1.007*price) && (now - lastTimeSell > 15) =>
                        ("sell", cumulative_quantity, (ltp*100).round.toDouble / 100)
                    case OrderElement(_, created_at, _, _, Some(cumulative_quantity), _, _, _, Some(price), _, Some("buy"), _, _)
                        if !hasSell && !isToday(created_at) && (ltp > 1.01*price) && fu.high.exists(ltp > .992*_) && (now - lastTimeSell > 15)  =>
                        ("sell", cumulative_quantity, (ltp*100).round.toDouble / 100)
                    case OrderElement(_, created_at, _, _, Some(cumulative_quantity), _, _, _, Some(price), _, Some("sell"), _, _)
                        if !hasBuy && isToday(created_at) && (ltp < .993*price) && (now - lastTimeBuy > 15) =>
                        ("buy", cumulative_quantity, (ltp*100).round.toDouble / 100)
                    case OrderElement(_, created_at, _, _, Some(cumulative_quantity), _, _, _, Some(price), _, Some("sell"), _, _)
                        if !hasBuy && !isToday(created_at) && (ltp < .99*price) && fu.low.exists(ltp < 1.008*_) && (now - lastTimeBuy > 15) =>
                        ("buy", cumulative_quantity, (ltp*100).round.toDouble / 100)
                    case OrderElement(_, created_at, _, _, Some(cumulative_quantity), _, _, _, Some(price), _, Some("buy"), _, None)
                        if !hasBuy && isToday(created_at) && (ltp < .991*price) && (now - lastTimeBuy > 15) =>
                        ("buy", cumulative_quantity, (ltp*100).round.toDouble / 100)
                    case OrderElement(_, created_at, _, _, Some(cumulative_quantity), _, _, _, Some(price), _, Some("buy"), _, None)
                        if !hasBuy && !isToday(created_at) && (ltp < .985*price) && fu.low.exists(ltp < 1.005*_) && (ltp < estimatedLow) && (now - lastTimeBuy > 15) =>
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
                new OrderElement("2018-09-13T13:48:41", "qlsk-g563s",  0,  "confirmed", 15.9,  "sell", None),
                new OrderElement("2018-09-12T16:43:08", "1jdp-1ggzlu", 2,  "filled",    15.27, "buy",  None),
                new OrderElement("2018-09-07T19:34:26", "1l3h-t990c8", 2,  "filled",    15.74, "buy",  None),
                new OrderElement("2018-09-07T19:26:08", "21o8-3jrsf8", 1,  "filled",    15.81, "buy",  None),
                new OrderElement("2018-09-05T14:15:30", "2e9i-f3tmde", 1,  "filled",    17.01, "buy",  Some("d")),
                new OrderElement("2018-08-31T20:04:42", "x9dw-n7z54",  1,  "filled",    17.21, "sell", Some("d")),
                new OrderElement("2018-08-31T17:58:09", "1iva-s92sux", 2,  "filled",    17.01, "sell", Some("7w")),
                new OrderElement("2018-08-31T15:09:33", "22ut-yqhloy", 4,  "filled",    16.87, "sell", Some("dv")),
                new OrderElement("2018-08-29T16:14:19", "1z7i-w9irsv", 4,  "filled",    16.88, "buy",  Some("2m")),
                new OrderElement("2018-08-27T15:04:54", "1xpl-xkaxmn", 2,  "filled",    17.01, "buy",  Some("2t")),
                new OrderElement("2018-08-27T14:21:13", "2qq5-qzdnwo", 2,  "filled",    17.11, "sell", Some("2t")),
                new OrderElement("2018-08-27T14:16:07", "1tw0-2j5cfe", 4,  "filled",    17.08, "sell", Some("2m")),
                new OrderElement("2018-08-22T16:47:43", "1mx7-fro0lj", 3,  "filled",    16.72, "sell", Some("s9")),
                new OrderElement("2018-08-20T20:36:45", "10lh-nu2pnf", 3,  "filled",    16.27, "buy",  Some("s9")),
                new OrderElement("2018-08-20T19:40:38", "13js-cycaza", 8,  "filled",    16.31, "sell", Some("k3")),
                new OrderElement("2018-07-27T18:51:20", "beda-5x6kq",  8,  "filled",    17.93, "buy",  Some("r3")),
                new OrderElement("2018-07-27T15:54:20", "2lgc-30u56x", 4,  "filled",    18.23, "buy",  Some("pz")),
                new OrderElement("2018-07-27T14:25:22", "2mhk-7rhalk", 4,  "filled",    18.37, "sell", Some("pz")),
                new OrderElement("2018-07-27T14:11:40", "2mu8-af2vmq", 8,  "filled",    18.31, "sell", Some("r3")),
                new OrderElement("2018-07-27T14:09:33", "t3it-scfcj",  16, "filled",    18.27, "sell", Some("ta")),
                new OrderElement("2018-07-27T14:07:57", "1i2u-86x8bn", 20, "filled",    18.19, "sell", Some("v1")),
                new OrderElement("2018-07-24T15:33:46", "1c16-lavjlb", 20, "filled",    17.1,  "buy",  Some("oj")),
                new OrderElement("2018-07-23T16:47:43", "2auq-z7s9ss", 20, "filled",    17.17, "sell", Some("oj")),
                new OrderElement("2018-07-19T19:13:48", "2kc2-jrdiqx", 25, "filled",    17.12, "sell", Some("ez")),
                new OrderElement("2018-07-17T14:24:22", "uav6-nndoi",  32, "filled",    16.8,  "sell", Some("ak")),
                new OrderElement("2018-07-11T14:11:14", "aysy-tztjs",  10, "filled",    16.4,  "buy",  Some("6m")),
                new OrderElement("2018-07-10T14:08:15", "g5in-xyhr9",  10, "filled",    16.65, "sell", Some("6m")),
                new OrderElement("2018-06-26T15:28:41", "1rwa-0oa061", 10, "filled",    16.39, "buy",  Some("w5")),
                new OrderElement("2018-06-07T15:05:19", "24p4-3w7w16", 10, "filled",    16.82, "buy",  Some("qu")),
                new OrderElement("2018-06-06T17:29:16", "1x0s-73mx3g", 10, "filled",    16.99, "sell", Some("qu")),
                new OrderElement("2018-06-06T17:22:24", "20x3-xuvq0q", 10, "filled",    16.93, "sell", Some("w5")),
                new OrderElement("2018-05-22T18:35:24", "2g3p-wbe7va", 32, "filled",    16.36, "buy",  Some("41")),
                new OrderElement("2018-05-22T17:39:41", "2dq5-ugpgxl", 32, "filled",    16.46, "sell", Some("41")),
                new OrderElement("2018-05-09T13:56:21", "p6dj-hbwoe",  64, "filled",    16.08, "sell", Some("63")),
                new OrderElement("2018-05-04T16:16:23", "18ys-ujccr4", 64, "filled",    15.42, "sell", Some("zu")),
                new OrderElement("2018-05-03T18:19:06", "29f7-qskb97", 64, "filled",    15.16, "sell", Some("jo")),
                new OrderElement("2018-05-02T14:26:06", "24mb-lioke1", 64, "filled",    15.09, "sell", Some("kt")),
                new OrderElement("2018-04-30T19:21:42", "1htv-llh2f6", 64, "filled",    14.6,  "buy",  Some("4")),
                new OrderElement("2018-04-30T16:22:18", "1b9v-j7enga", 64, "filled",    14.69, "sell", Some("4")),
                new OrderElement("2018-04-30T16:02:15", "e65z-8rkaz",  64, "filled",    14.65, "buy",  Some("x5")),
                new OrderElement("2018-04-30T15:42:29", "139f-gwtquh", 64, "filled",    14.88, "sell", Some("x5")),
                new OrderElement("2018-04-30T15:42:03", "10bn-kdcsb3", 64, "filled",    14.75, "buy",  Some("kt")),
                new OrderElement("2018-04-30T14:49:48", "21xn-dsojge", 64, "filled",    14.86, "buy",  Some("jo")),
                new OrderElement("2018-04-30T14:29:16", "arzl-kmusd",  64, "filled",    14.97, "buy",  Some("zu")),
                new OrderElement("2018-04-27T18:41:09", "93zd-m7s2d",  64, "filled",    15.17, "buy",  Some("63")),
                new OrderElement("2018-04-27T15:55:33", "17ih-29jai",  32, "filled",    15.22, "buy",  Some("ak")),
                new OrderElement("2018-04-27T15:35:33", "2gvb-vh8ofp", 25, "filled",    15.34, "buy",  Some("ez")),
                new OrderElement("2018-04-27T15:32:01", "1jhl-3q42sv", 20, "filled",    15.39, "buy",  Some("v1")),
                new OrderElement("2018-04-27T15:31:27", "8557-nyul8",  16, "filled",    15.43, "buy",  Some("ta")),
                new OrderElement("2018-04-24T17:24:06", "1lez-4oux7d", 8,  "filled",    15.8,  "buy",  Some("k3")),
                new OrderElement("2018-04-24T16:30:17", "m8kl-cry4r",  4,  "filled",    15.86, "buy",  Some("dv")),
                new OrderElement("2018-04-24T16:29:50", "1qf4-7c8wba", 2,  "filled",    15.9,  "buy",  Some("7w")),
                new OrderElement("2018-04-24T14:36:15", "25kq-mtjkg3", 1,  "filled",    16,    "buy",  None),
                new OrderElement("2018-04-12T15:34:37", "c9vv-ynrno",  20, "filled",    17.64, "sell", Some("91")),
                new OrderElement("2018-04-12T15:19:54", "1mch-ewsj2u", 30, "filled",    17.56, "sell", Some("f6")),
                new OrderElement("2018-04-12T15:11:28", "256b-kb7v99", 30, "filled",    17.53, "sell", Some("tr")),
                new OrderElement("2018-04-11T16:00:18", "q173-0p635",  10, "filled",    17.12, "sell", Some("t")),
                new OrderElement("2018-04-11T15:59:08", "1qhx-2y1eoj", 20, "filled",    17.09, "sell", Some("9c")),
                new OrderElement("2018-04-09T19:58:27", "6fa8-50q92",  20, "filled",    16.24, "buy",  Some("qd")),
                new OrderElement("2018-04-09T15:00:39", "2bl6-sx4k06", 20, "filled",    16.61, "sell", Some("qd")),
                new OrderElement("2018-04-02T13:34:10", "1ea2-zkgr6a", 20, "filled",    16.07, "buy",  Some("9c")),
                new OrderElement("2018-03-28T13:38:18", "2et5-o846we", 10, "filled",    16.77, "buy",  Some("t")),
                new OrderElement("2018-03-27T19:53:13", "daga-h7hrr",  30, "filled",    17.14, "buy",  Some("tr")),
                new OrderElement("2018-03-27T19:41:52", "21ag-s8auaz", 30, "filled",    17.09, "buy",  Some("2j")),
                new OrderElement("2018-03-27T19:41:28", "1isz-acrl2u", 30, "filled",    17.16, "sell", Some("2j")),
                new OrderElement("2018-03-27T19:39:47", "16vg-v4fawc", 30, "filled",    17.12, "buy",  Some("f6")),
                new OrderElement("2018-03-27T19:07:48", "8p7p-y8qk6",  20, "filled",    17.24, "buy",  Some("91")),
                new OrderElement("2018-03-27T18:59:13", "2gt4-0o2084", 12, "filled",    17.35, "buy",  None),
                new OrderElement("2018-03-27T18:39:22", "245n-84z6wt", 6,  "filled",    17.42, "buy",  None),
                new OrderElement("2018-03-27T18:08:30", "nlwp-cq26s",  2,  "filled",    17.5,  "buy",  None),
                new OrderElement("2018-03-19T15:46:38", "2jzy-66dyyp", 1,  "filled",    17.58, "buy",  None),
                new OrderElement("2018-03-19T15:45:46", "g0bf-cnv5v",  1,  "filled",    17.64, "buy",  None),
                new OrderElement("2018-03-19T15:05:06", "1kg2-oka5h0", 1,  "filled",    17.76, "buy",  None)
            )
            fu = Fundamental(N, N, N, N, Some(15.68), N, Some(15.25), N, N, N, "")
            var decision = shouldBuySell(orderElements, 15.43)
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