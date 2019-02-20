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

        Set("AMD", "CY", "ON", "TSLA", "CVX", "TSLA").foreach(symbol =>
            sttp
                    .get(uri"$APP/$symbol/debug")
                    .send()
                    .body match {
                case Left(s) => fail(s)
                case Right(s) =>
                    val jv = parse(s)
                    val ltp = Util.fromJValueToOption[Double](jv \ "ltp")
                    assertTrue(ltp.isDefined && !ltp.get.isNaN, s"Error: no ltp for $symbol")
                    val position = Util.fromJValueToOption[Double](jv \ "position")
                    assertTrue(position.isDefined && !position.get.isNaN, s"Error: no position for $symbol")
                    if (symbol != "CVX") {
                        val ordersString = Util.fromJValueToOption[String](jv \ "orders")
                        assertTrue(ordersString.isDefined && ordersString.get.nonEmpty, s"Error: no order for $symbol")
                    }
                    val openPrice = Util.fromJValueToOption[Double](jv \ "openPrice")
                    assertTrue(openPrice.get.isNaN, s"openPrice should be empty in $s")
                    val todayLow = Util.fromJValueToOption[Double](jv \ "todayLow")
                    assertTrue(todayLow.get.isNaN, s"todayLow should be empty in $s")
                    val todayHigh = Util.fromJValueToOption[Double](jv \ "todayHigh")
                    assertTrue(todayHigh.get.isNaN, s"todayHigh should be empty in $s")
            }
        )

        sttp.get(uri"$APP/quote/tick").send().body match {
            case Left(s) => fail(s)
            case Right(s) =>
                Thread.sleep(5000) // wait for QuoteActor to get last trade prices and interval quotes for all stocks
                Set("AMD", "CY", "ON", "TSLA", "CVX", "TSLA").foreach(symbol =>
                    sttp.get(uri"$APP/$symbol/debug").send().body match {
                        case Left(x) => fail(x)
                        case Right(x) =>
                            val jv = parse(x)
                            val ltp = Util.fromJValueToOption[Double](jv \ "ltp")
                            assertTrue(ltp.isDefined && !ltp.get.isNaN, s"Error: no ltp for $symbol")
                            val openPrice = Util.fromJValueToOption[Double](jv \ "openPrice")
                            assertFalse(openPrice.get.isNaN, s"openPrice should be there in $x")
                            val todayLow = Util.fromJValueToOption[Double](jv \ "todayLow")
                            assertFalse(todayLow.get.isNaN, s"todayLow should be there in $x")
                            val todayHigh = Util.fromJValueToOption[Double](jv \ "todayHigh")
                            assertFalse(todayHigh.get.isNaN, s"todayHigh should be there in $x")
                    }
                )
        }
    }

    /**
      * Use this in robinhood website to count how many successful transactions there are for a stock:
      * window._var1 = Array.from(document.querySelectorAll('h2')).filter(h2 => h2.childElementCount === 0 && h2.textContent === 'Older')[0].nextElementSibling
      * Array.from(window._var1.querySelectorAll('h3')).filter(h3 => h3.childElementCount === 0 && h3.textContent.indexOf('$') >= 0)
      */
    @Test
    def test2() {
        import org.json4s._
        import org.json4s.native.JsonMethods._

        val symbol = "AXP"

/*
        sttp.get(uri"$APP/$symbol/debug").send().body match {
            case Left(s) => fail(s)
            case Right(s) =>
                val orders = Util.fromJValueToOption[String](parse(s) \ "orders")
                assertTrue(orders.get.split("\n").length > 8)
        }
*/

        sttp.get(uri"$APP/$symbol/debug/CLEAR_ORDERS").send().body
        Thread.sleep(1000)

        sttp.get(uri"$APP/$symbol/debug").send().body match {
            case Left(s) => fail(s)
            case Right(s) =>
                val orders = Util.fromJValueToOption[String](parse(s) \ "orders")
                assertTrue(orders.get == "")
        }

        val lines = Array(
            """{"updated_at":"2018-09-06T22:00:14.095475Z","id":"30ab407e-524f-47a9-8c26-ea84ec211234","cumulative_quantity":"3.0","state":"cancelled","price":"321.82","created_at":"2018-09-06T22:59:13.095475Z","side":"buy","average_price":null,"quantity":"4.0"}""",
            """{"updated_at":"2018-09-06T22:00:14.095475Z","id":"30ab407e-524f-47a9-8c26-ea84ec211234","cumulative_quantity":"0.0","state":"confirmed","price":"321.82","created_at":"2018-09-06T22:58:12.095475Z","side":"buy","average_price":null,"quantity":"4.0"}""",
            """{"updated_at":"2018-09-06T22:00:14.095475Z","id":"990c407e-524f-47a9-8c26-ea84ec21be96","cumulative_quantity":"0.0","state":"cancelled","price":"321.82","created_at":"2018-09-06T21:59:13.095475Z","side":"buy","average_price":null,"quantity":"1.0"}""",
            """{"updated_at":"2018-09-06T22:00:14.095475Z","id":"990c407e-524f-47a9-8c26-ea84ec21be96","cumulative_quantity":"0.0","state":"confirmed","price":"321.82","created_at":"2018-09-06T20:58:12.095475Z","side":"buy","average_price":null,"quantity":"1.0"}""",
            """{"updated_at":"2018-08-16T16:18:02.888584Z","id":"1a7efbd9-9215-402c-a98c-b9b418b127cc","cumulative_quantity":"2.0","state":"filled","price":"321.82","created_at":"2018-08-16T16:13:09.412296Z","side":"sell","average_price":"102.97","quantity":"2.0"}""",
            """{"updated_at":"2018-08-16T16:18:02.888584Z","id":"1a7efbd9-9215-402c-a98c-b9b418b127cc","cumulative_quantity":"2.0","state":"confirmed","price":"321.82","created_at":"2018-08-16T15:12:08.412296Z","side":"sell","average_price":"102.97","quantity":"2.0"}""",
            """{"updated_at":"2018-07-31T19:46:17.635605Z","id":"eb8d3c7c-cf9c-4b73-a839-282d8a9ace6f","cumulative_quantity":"1.0","state":"filled","price":"321.82","created_at":"2018-07-31T19:39:39.401987Z","side":"buy","average_price":"99.55","quantity":"1.0"}""",
            """{"updated_at":"2018-07-31T19:46:17.635605Z","id":"eb8d3c7c-cf9c-4b73-a839-282d8a9ace6f","cumulative_quantity":"1.0","state":"confirmed","price":"321.82","created_at":"2018-07-31T18:38:38.401987Z","side":"buy","average_price":"99.55","quantity":"1.0"}""",
            """{"updated_at":"2018-07-31T16:32:14.860509Z","id":"5a47ef91-9fed-43fc-9b6f-6f6525ac8a70","cumulative_quantity":"1.0","state":"filled","price":"321.82","created_at":"2018-07-31T13:39:52.393562Z","side":"buy","average_price":"99.89","quantity":"1.0"}""",
            """{"updated_at":"2018-07-31T16:32:14.860509Z","id":"5a47ef91-9fed-43fc-9b6f-6f6525ac8a70","cumulative_quantity":"1.0","state":"confirmed","price":"321.82","created_at":"2018-07-31T12:38:51.393562Z","side":"buy","average_price":"99.89","quantity":"1.0"}"""
        ).reverse

        sttp
                .body(s"""{"position":0,"orders":[${lines(0)}]}""")
                .post(uri"$APP/$symbol/position-orderList")
                .send()
                .body match {
            case Left(s) => fail(s)
            case Right(s) => if (s != "{}") println(s)
        }
        Thread.sleep(1000)
        sttp.get(uri"$APP/$symbol/debug").send().body match {
            case Left(s) => fail(s)
            case Right(s) =>
                val orders = Util.fromJValueToOption[String](parse(s) \ "orders")
                assertTrue(orders.get.split("\n").length == 1)
        }

        sttp
                .body(s"""{"position":1,"orders":[${lines(1)}]}""")
                .post(uri"$APP/$symbol/position-orderList")
                .send()
                .body match {
            case Left(s) => fail(s)
            case Right(s) => if (s != "{}") println(s)
        }
        Thread.sleep(1000)
        sttp.get(uri"$APP/$symbol/debug").send().body match {
            case Left(s) => fail(s)
            case Right(s) =>
                val orders = Util.fromJValueToOption[String](parse(s) \ "orders")
                assertTrue(orders.get.split("\n").length == 1)
        }

        sttp
                .body(s"""{"position":1,"orders":[${lines(2)}]}""")
                .post(uri"$APP/$symbol/position-orderList")
                .send()
                .body match {
            case Left(s) => fail(s)
            case Right(s) => if (s != "{}") println(s)
        }
        Thread.sleep(1000)
        sttp.get(uri"$APP/$symbol/debug").send().body match {
            case Left(s) => fail(s)
            case Right(s) =>
                val orders = Util.fromJValueToOption[String](parse(s) \ "orders")
                assertTrue(orders.get.split("\n").length == 2)
        }

        sttp
                .body(s"""{"position":2,"orders":[${lines(3)}]}""")
                .post(uri"$APP/$symbol/position-orderList")
                .send()
                .body match {
            case Left(s) => fail(s)
            case Right(s) => if (s != "{}") println(s)
        }
        Thread.sleep(1000)
        sttp.get(uri"$APP/$symbol/debug").send().body match {
            case Left(s) => fail(s)
            case Right(s) =>
                val orders = Util.fromJValueToOption[String](parse(s) \ "orders")
                assertTrue(orders.get.split("\n").length == 2)
        }

        sttp
                .body(s"""{"position":2,"orders":[${lines(4)}]}""")
                .post(uri"$APP/$symbol/position-orderList")
                .send()
                .body match {
            case Left(s) => fail(s)
            case Right(s) => if (s != "{}") println(s)
        }
        Thread.sleep(1000)
        sttp.get(uri"$APP/$symbol/debug").send().body match {
            case Left(s) => fail(s)
            case Right(s) =>
                val orders = Util.fromJValueToOption[String](parse(s) \ "orders")
                assertTrue(orders.get.split("\n").length == 3)
        }

        sttp
                .body(s"""{"position":4,"orders":[${lines(5)}]}""")
                .post(uri"$APP/$symbol/position-orderList")
                .send()
                .body match {
            case Left(s) => fail(s)
            case Right(s) => if (s != "{}") println(s)
        }
        Thread.sleep(1000)
        sttp.get(uri"$APP/$symbol/debug").send().body match {
            case Left(s) => fail(s)
            case Right(s) =>
                val orders = Util.fromJValueToOption[String](parse(s) \ "orders")
                assertTrue(orders.get.split("\n").length == 3)
        }

        sttp
                .body(s"""{"position":4,"orders":[${lines(6)}]}""")
                .post(uri"$APP/$symbol/position-orderList")
                .send()
                .body match {
            case Left(s) => fail(s)
            case Right(s) => if (s != "{}") println(s)
        }
        Thread.sleep(1000)
        sttp.get(uri"$APP/$symbol/debug").send().body match {
            case Left(s) => fail(s)
            case Right(s) =>
                val orders = Util.fromJValueToOption[String](parse(s) \ "orders")
                assertTrue(orders.get.split("\n").length == 4)
        }

        sttp
                .body(s"""{"position":4,"orders":[${lines(7)}]}""")
                .post(uri"$APP/$symbol/position-orderList")
                .send()
                .body match {
            case Left(s) => fail(s)
            case Right(s) => if (s != "{}") println(s)
        }
        Thread.sleep(1000)
        sttp.get(uri"$APP/$symbol/debug").send().body match {
            case Left(s) => fail(s)
            case Right(s) =>
                val orders = Util.fromJValueToOption[String](parse(s) \ "orders")
                assertTrue(orders.get.split("\n").length == 3)
        }

        sttp
                .body(s"""{"position":4,"orders":[${lines(8)}]}""")
                .post(uri"$APP/$symbol/position-orderList")
                .send()
                .body match {
            case Left(s) => fail(s)
            case Right(s) => if (s != "{}") println(s)
        }
        Thread.sleep(1000)
        sttp.get(uri"$APP/$symbol/debug").send().body match {
            case Left(s) => fail(s)
            case Right(s) =>
                val orders = Util.fromJValueToOption[String](parse(s) \ "orders")
                assertTrue(orders.get.split("\n").length == 4)
        }

        sttp
                .body(s"""{"position":7,"orders":[${lines(9)}]}""")
                .post(uri"$APP/$symbol/position-orderList")
                .send()
                .body match {
            case Left(s) => fail(s)
            case Right(s) => if (s != "{}") println(s)
        }
        Thread.sleep(1000)
        sttp.get(uri"$APP/$symbol/debug").send().body match {
            case Left(s) => fail(s)
            case Right(s) =>
                val orders = Util.fromJValueToOption[String](parse(s) \ "orders")
                assertTrue(orders.get.split("\n").length == 4)
        }
    }
}
