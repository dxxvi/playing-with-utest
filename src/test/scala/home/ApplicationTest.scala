package home

import com.softwaremill.sttp._
import com.typesafe.config.ConfigFactory
import home.util.{SttpBackends, Util}
import org.junit.jupiter.api.Assertions._
import org.junit.jupiter.api.{Disabled, Test}

import scala.util.{Failure, Success, Try}

@Disabled("This test is used only when the application is running without `akkaTimers` in the system environment (so " +
        "that the `timersx` is not effective.")
class ApplicationTest extends SttpBackends {
    implicit val backend: SttpBackend[Id, Nothing] = configureCoreJavaHttpBackend(ConfigFactory.load())
    val APP: String = "http://localhost:4567"

    @Test
    def test() {
        import org.json4s._
        import org.json4s.native.JsonMethods._

        var s = f(DefaultWatchListActor.NAME, "tick")
        assertTrue(s startsWith s"Sent ${DefaultWatchListActor.NAME} a Tick")

        var commaSeparatedSymbolString = ""
        Thread.sleep(3000)                       // so that the DefaultWatchListActor has time to talk to Robinhood
        s = f(DefaultWatchListActor.NAME, "debug")
        Util.fromJValueToOption[String](parse(s) \ "commaSeparatedSymbolString") match {
            case None => fail(s"Invalid response: $s")
            case Some(symbolString) =>
                commaSeparatedSymbolString = symbolString
                assertTrue(symbolString.split(",").length > 100, s"Too few symbols in $symbolString")
        }

        s = f("AMD", "debug")
        var jv: JValue = parse(s)
        assertTrue(Util.fromJValueToOption[String](jv \ "ltp").contains("NaN"), s"ltp in $s")
        assertTrue(Util.fromJValueToOption[String](jv \ "openPrice").contains("NaN"), s"openPrice in $s")
        assertTrue(Util.fromJValueToOption[String](jv \ "todayHigh").contains("NaN"), s"todayHigh in $s")
        assertTrue(Util.fromJValueToOption[String](jv \ "sentOrderHistoryRequest").contains("false"),
            s"sentOrderHistoryRequest in $s")

        s = f(QuoteActor.NAME, "debug")
        jv = parse(s)
        assertTrue(Util.fromJValueToOption[String](jv \ "symbolsNeedDailyQuote").contains(""),
            s"symbolsNeedDailyQuote in $s")

        s = f(QuoteActor.NAME, "tick")           // to fetch all quotes
        assertTrue(s startsWith s"Sent ${QuoteActor.NAME} a Tick")

        Thread.sleep(3000)                       // gives QuoteActor some time to fetch the last trade prices

        s = f("AMD", "debug")
        jv = parse(s)
        var ltp = Util.fromJValueToOption[Double](jv \ "ltp").get
        var todayLow  = Util.fromJValueToOption[Double](jv \ "todayLow").get
        var todayHigh = Util.fromJValueToOption[Double](jv \ "todayHigh").get
        var openPrice = Util.fromJValueToOption[String](jv \ "openPrice").get
        assertTrue(ltp == todayLow && ltp == todayHigh,
            s"ltp: $ltp, todayLow: $todayLow, todayHigh: $todayHigh in $s")
        assertTrue(openPrice == "NaN", s"openPrice in $s")

        jv = parse(f("ON", "debug"))
        ltp = Util.fromJValueToOption[Double](jv \ "ltp").get
        todayLow  = Util.fromJValueToOption[Double](jv \ "todayLow").get
        todayHigh = Util.fromJValueToOption[Double](jv \ "todayHigh").get
        openPrice = Util.fromJValueToOption[String](jv \ "openPrice").get
        assertTrue(ltp == todayLow && ltp == todayHigh,
            s"ltp: $ltp, todayLow: $todayLow, todayHigh: $todayHigh in $s")
        assertTrue(openPrice == "NaN", s"openPrice in $s")

        jv = parse(f("CY", "debug"))
        ltp = Util.fromJValueToOption[Double](jv \ "ltp").get
        todayLow  = Util.fromJValueToOption[Double](jv \ "todayLow").get
        todayHigh = Util.fromJValueToOption[Double](jv \ "todayHigh").get
        openPrice = Util.fromJValueToOption[String](jv \ "openPrice").get
        assertTrue(ltp == todayLow && ltp == todayHigh,
            s"ltp: $ltp, todayLow: $todayLow, todayHigh: $todayHigh in $s")
        assertTrue(openPrice == "NaN", s"openPrice in $s")

        s = f("AMD", "tick")                     // to request daily quotes
        assertTrue(s startsWith s"Sent StockActor AMD a Tick")

        s = f("CY", "tick")
        assertTrue(s startsWith s"Sent StockActor CY a Tick")

        s = f("ON", "tick")
        assertTrue(s startsWith s"Sent StockActor ON a Tick")

        Thread.sleep(2000)
        s = f(QuoteActor.NAME, "debug")
        jv = parse(s)
        val symbolsNeedDailyQuote: Option[String] = Util.fromJValueToOption[String](jv \ "symbolsNeedDailyQuote")
        val symbolsNeedTodayQuote: Option[String] = Util.fromJValueToOption[String](jv \ "symbolsNeedTodayQuote")
        assertTrue(symbolsNeedDailyQuote.isDefined &&
                symbolsNeedDailyQuote.get.contains("AMD") &&
                symbolsNeedDailyQuote.get.contains("CY") &&
                symbolsNeedDailyQuote.get.contains("ON") &&
                symbolsNeedTodayQuote.isDefined &&
                symbolsNeedTodayQuote.contains("AMD") &&
                symbolsNeedTodayQuote.contains("CY") &&
                symbolsNeedTodayQuote.contains("ON"),
            s"symbolsNeedDailyQuote in $s")

        s = f(QuoteActor.NAME, "tick")           // to fetch all quotes and daily quotes for 3 stocks above
        assertTrue(s startsWith s"Sent ${QuoteActor.NAME} a Tick")

        s = f(QuoteActor.NAME, "debug")
        jv = parse(s)
        assertTrue(Util.fromJValueToOption[String](jv \ "symbolsNeedDailyQuote").get.isEmpty, s)

        Thread.sleep(3000)
        s = f("AMD", "debug")
        jv = parse(s)
        assertTrue(Util.fromJValueToOption[Double](jv \ "CL49").isDefined, s"CL49 in $s")
        assertTrue(Util.fromJValueToOption[Double](jv \ "HL49").isDefined, s"HL49 in $s")
        assertTrue(Util.fromJValueToOption[Double](jv \ "OL49").isDefined, s"OL49 in $s")
        assertTrue(Util.fromJValueToOption[Double](jv \ "openPrice").isDefined, s"openPrice in $s")

        s = f(OrderActor.NAME, "tick")           // to fetch recent orders and positions for all stocks
        assertTrue(s startsWith s"Sent ${OrderActor.NAME} a Tick")
        Thread.sleep(3000)

        s = f("AMD", "debug")
        jv = parse(s)
        assertTrue(Util.fromJValueToOption[Double](jv \ "position").isDefined, s"position in $s")
        println(s"AMD: ${Util.fromJValueToOption[Double](jv \ "position")} shares")
        s = f("TSLA", "debug")
        jv = parse(s)
        assertTrue(Util.fromJValueToOption[Double](jv \ "position").isDefined, s"position in $s")
        println(s"TSLA: ${Util.fromJValueToOption[Double](jv \ "position")} shares")

        val accessToken = sttp.get(uri"$APP/accessToken").send().body match {
            case Right(x) => x
            case Left(x) => fail[String]("Unable to get access token: " + x)
        }

        s = sttp.auth.bearer(accessToken).get(uri"https://api.robinhood.com/orders/").send().body match {
            case Left(x) => fail("Unable to get recent orders from robinhood: " + x)
            case Right(x) => x
        }
        home.Main.addStocksToDatabase(ConfigFactory.load())
        val symbolsHavingRecentOrders: Set[String] = Util.extractSymbolAndOrder(s)
                .filter(_._2.state.toLowerCase.contains("fill"))
                .map(_._1)
                .toSet
        symbolsHavingRecentOrders
                .filter(symbol => ("," + commaSeparatedSymbolString + ",").contains("," + symbol + ","))
                .foreach(symbol => {
                    jv = parse(f(symbol, "debug"))
                    assertTrue(Util.fromJValueToOption[String](jv \ "orders").get contains "Order(")
                })
    }

    private def f(name: String, action: String): String = sttp.get(uri"$APP/$name/$action").send().body match {
        case Left(s) => s
        case Right(s) => s
    }
}
