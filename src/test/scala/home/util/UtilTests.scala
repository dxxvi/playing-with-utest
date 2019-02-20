package home.util

import java.io.FileOutputStream

import com.typesafe.config.{Config, ConfigFactory}
import home.Main
import home.QuoteActor.DailyQuote
import org.junit.jupiter.api.Assertions._
import org.junit.jupiter.api.{Disabled, Test}

import scala.util.{Failure, Success}

/*
@Disabled("This test class can run in the IDE only where -Dauthorization.username=x " +
        "-Dauthorization.encryptedPassword=\"x\" is specified in the VM options and " +
        "key=x is specified in the Environment variables")
*/
class UtilTests extends SttpBackends {
    @Test
    def testGetSymbolFromInstrumentHttpURLConnection() {
        val config: Config = ConfigFactory.load()
        if (Main.accessToken.length < 9) testRetrieveAccessToken()

        val goodInstrumentUrl = "https://api.robinhood.com/instruments/dad8fa2c-1e8d-4cb9-b354-1f0b91a4193e/"
        Util.getSymbolFromInstrumentHttpURLConnection(goodInstrumentUrl, config) match {
            case Success(symbol) => assertEquals("ON", symbol, s"$goodInstrumentUrl should be for ON")
            case Failure(ex) => fail(ex)
        }

        val badInstrumentUrl1 = "https://api.robinhood.com/instruments/dad8fa2c-1e8d-4cb9-b354-1f0b91a4193ex/"
        Util.getSymbolFromInstrumentHttpURLConnection(badInstrumentUrl1, config) match {
            case Success(s) => fail(s"Never happen $s")
            case Failure(ex) => println(s"This is expected: $ex")
        }

        val badInstrumentUrl2 = "https://a.r.c/instruments/dad8fa2c-1e8d-4cb9-b354-1f0b91a4193e/"
        Util.getSymbolFromInstrumentHttpURLConnection(badInstrumentUrl2, config) match {
            case Success(s) => fail(s"Never happen $s")
            case Failure(ex) => println(s"This is expected: $ex")
        }
    }

    @Test
    def testGetDailyQuoteHttpURLConnection() {
        import home.QuoteActor.DailyQuote

        val config = ConfigFactory.load()
        if (Main.accessToken.length < 9) testRetrieveAccessToken()
        val list = Util.getDailyQuoteHttpURLConnection(Set("AMD", "TSLA"), config)

        assertEquals(2, list.size)

        val amdOption: Option[(String, List[DailyQuote])] = list.find(_._1 == "AMD")
        assertTrue(amdOption.nonEmpty)
        assertTrue(amdOption.get._2.size > 241, "Not enough AMD daily quotes")

        val tslaOption: Option[(String, List[DailyQuote])] = list.find(_._1 == "TSLA")
        assertTrue(tslaOption.nonEmpty)
        assertTrue(tslaOption.get._2.size > 241, "Not enough TSLA daily quotes")
    }

    @Test
    def testExtractSymbolAndOrder() {
        import com.softwaremill.sttp._

        val config = ConfigFactory.load()
        if (Main.accessToken.length < 9) testRetrieveAccessToken()
        implicit val backend: SttpBackend[Id, Nothing] = Util.configureCoreJavaHttpBackend(config)
        val SERVER = config.getString("server")

        home.Main.addStocksToDatabase(config)

        sttp.auth.bearer(Main.accessToken).get(uri"$SERVER/orders/").send().body match {
            case Right(js) =>
                val map: Map[String, List[home.StockActor.Order]] = Util.extractSymbolAndOrder(js)
                assertEquals(100, map.values.map(_.size).sum, "Should get 100 orders")
                val allOrders = map.values.foldLeft(List.empty[home.StockActor.Order])((list1, list2) => list1 ++ list2)
                assertTrue(!allOrders.exists(o => o.averagePrice.isNaN || o.price.isNaN),
                    "No order should have average_price or price of Double.NaN")
            case Left(s) => throw new RuntimeException(s)
        }
    }

    @Test
    def testRetrieveAccessToken() {
        val config = ConfigFactory.load()
        Util.retrieveAccessToken(config) match {
            case Right(accessToken) =>
                Main.accessToken = accessToken
                println(accessToken)
            case Left(s) => fail(s)
        }
    }

    @Disabled
    @Test
    def testWriteOrderHistoryToFile() {
        val config = ConfigFactory.load()
        if (Main.accessToken.length < 9) testRetrieveAccessToken()
        home.Main.addStocksToDatabase(config)
        Main.watchedSymbols = Util.retrieveWatchedSymbols(config)

        Util.writeOrderHistoryToFile(config)
    }

    @Disabled
    @Test
    def generateExcel2() {
        import com.softwaremill.sttp._
        import org.apache.poi.ss.usermodel._
        import org.apache.poi.xssf.usermodel._
        import org.json4s
        import org.json4s.native.JsonMethods._

        val config = ConfigFactory.load()

        implicit val backend: SttpBackend[Id, Nothing] = configureCoreJavaHttpBackend(config)

        Main.addStocksToDatabase(config)

        Util.retrieveAccessToken(config) match {
            case Right(x) => home.Main.accessToken = x
            case Left(errorMessage) =>
                println(s"Error: $errorMessage")
                System.exit(-1)
        }

        val workBook = new XSSFWorkbook()

//        Util.retrieveWatchedSymbols(config).grouped(75)
        List("AAPL", "AMD", "MU", "CY", "MSFT").grouped(75).foreach(symbols => {
            Util.get5minsQuoteHttpURLConnection(symbols.toSet, config).foreach((t: (String, List[DailyQuote])) => {
                val sheet = workBook.createSheet(t._1)
                val row = sheet.createRow(0)
                var cell = row.createCell(0); cell.setCellType(CellType.BLANK)
                cell = row.createCell(1); cell.setCellType(CellType.STRING); cell.setCellValue("Open")
                cell = row.createCell(2); cell.setCellType(CellType.STRING); cell.setCellValue("High")
                cell = row.createCell(3); cell.setCellType(CellType.STRING); cell.setCellValue("Low")
                cell = row.createCell(4); cell.setCellType(CellType.STRING); cell.setCellValue("Close")
                cell = row.createCell(5); cell.setCellType(CellType.STRING); cell.setCellValue("Volume")
                cell = row.createCell(6); cell.setCellType(CellType.STRING); cell.setCellValue("EMA")
                cell = row.createCell(7); cell.setCellType(CellType.STRING); cell.setCellValue("Avg")
                cell = row.createCell(8); cell.setCellType(CellType.STRING); cell.setCellValue("Number")

                t._2.zipWithIndex.foreach((tu: (home.QuoteActor.DailyQuote, Int)) => {
                    val dq = tu._1
                    val r = sheet.createRow(tu._2 + 1)
                    var c = r.createCell(0); c.setCellType(CellType.STRING); c.setCellValue(dq.beginsAt)            // A
                    c = r.createCell(1); c.setCellType(CellType.NUMERIC); c.setCellValue(f"${dq.openPrice}%4.4f".toDouble)  // B
                    c = r.createCell(2); c.setCellType(CellType.NUMERIC); c.setCellValue(f"${dq.highPrice}%4.4f".toDouble)  // C
                    c = r.createCell(3); c.setCellType(CellType.NUMERIC); c.setCellValue(f"${dq.lowPrice}%4.4f".toDouble)   // D
                    c = r.createCell(4); c.setCellType(CellType.NUMERIC); c.setCellValue(f"${dq.closePrice}%4.4f".toDouble) // E
                    c = r.createCell(5); c.setCellType(CellType.NUMERIC); c.setCellValue(dq.volume)                         // F
                    c = r.createCell(6); c.setCellType(CellType.FORMULA)                                                    // G
                    if (tu._2 == 0) c.setCellFormula(s"(C2 + D2) / 2")
                    else c.setCellFormula(s"0.2*(C${tu._2+2}+D${tu._2+2})/2 + (1-0.2)*G${tu._2 + 1}")
                    c = r.createCell(7); c.setCellType(CellType.FORMULA); c.setCellFormula(s"(C${tu._2+2} + D${tu._2+2}) / 2")                                                 // H
                    c = r.createCell(8); c.setCellType(CellType.NUMERIC); c.setCellValue(tu._2 % 78)                                                 // H
                })
            })
        })

        val outputStream = new FileOutputStream("/dev/shm/test.xlsx")
        workBook.write(outputStream)
        outputStream.close()
    }
}
