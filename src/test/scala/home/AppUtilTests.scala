package home

import org.junit.jupiter.api.{Assertions, Test}

class AppUtilTests extends AppUtil {
    @Test
    def testCompareCreatedAts(): Unit = {
        val ca1 = "2019-04-19T23:07:06.123456Z"
        val ca2 = "2019-04-19T23:07:06Z"
        val ca3 = "2019-04-19T03:07:06Z"
        val ca4 = "2019-04-04T23:57:56Z"
        Assertions.assertEquals(0, compareCreatedAts(ca1, ca1))
        Assertions.assertTrue(compareCreatedAts(ca1, ca2) > 0, s"ca1 $ca1 ca2 $ca2")
        Assertions.assertTrue(compareCreatedAts(ca4, ca3) < 0, s"ca1 $ca4 ca2 $ca3")
    }

    @Test
    def test(): Unit = {
        import home.model.Order
        import org.json4s._
        import org.json4s.JsonDSL._
        import org.json4s.native.Serialization
        import org.json4s.native.JsonMethods._

        implicit val defaultFormats: DefaultFormats = DefaultFormats
        val o1 = Order("2019-03-11T01:02:03.4567Z", "id1", "buy", "instru", 1.2, "confirmed", 0, 1, "2019-03-11T02:03:04.8901Z")
        val o2 = Order("2019-04-19T01:02:03.4567Z", "id2", "sell", "instru", 1.2, "confirmed", 0, 1, "2019-03-11T02:03:04.8901Z")
        val s = Serialization.write(List(o1, o2))
        println(s)

        val jObject = ("orders" -> "primitives only?") ~ ("a" -> 1d)
        println(compact(render(jObject)))

        val ltp = 12.1259
        println(f"$ltp%.2f".toDouble)

        import java.time.LocalDate
        import java.time.format.DateTimeFormatter
        println(LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE))

        val inputStream = classOf[StockActor].getResourceAsStream("/static/index.html")
        inputStream.close()

        println("2019-04-19T02:03:04a123456Z".replaceAll("\\.?[0-9]{0,6}Z$", ""))

        println(List(1.1, 5.5, 2.2).sorted)
    }
}
