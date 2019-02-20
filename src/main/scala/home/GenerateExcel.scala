package home

import java.io.FileOutputStream

import com.softwaremill.sttp._
import com.typesafe.config.{Config, ConfigFactory}
import home.QuoteActor.DailyQuote
import home.util.{SttpBackends, Util}
import org.apache.poi.ss.usermodel._
import org.apache.poi.xssf.usermodel._

object GenerateExcel extends SttpBackends {
    def main(args: Array[String]): Unit = {
        val config = ConfigFactory.load()

        implicit val backend: SttpBackend[Id, Nothing] = configureCoreJavaHttpBackend(config)

        Main.addStocksToDatabase(config)

        Util.retrieveAccessToken(config) match {
            case Right(x) => home.Main.accessToken = x
            case Left(errorMessage) =>
                println(
                    s"""
                       |To get access token:
                       |  http https://api.robinhood.com/oauth2/token/ username=... password=... grant_type=password client_id=c82SH0WZOsabOXGP2sxqcj34FxkvfnWRZBKlBjFS
                       |Then run:
                       |  java -DaccessToken=... -jar excel.jar
                       |Import stocks.xlsx in the current directory to Google Spreadsheets.
                     """.stripMargin)
                System.exit(-1)
        }

        val workBook = new XSSFWorkbook()

        Util.retrieveWatchedSymbols(config).grouped(75).foreach(symbols => {
            Util.getDailyQuoteHttpURLConnection(symbols.toSet, config).foreach((t: (String, List[DailyQuote])) => {
                val sheet = workBook.createSheet(t._1)
                val row = sheet.createRow(0)
                var cell = row.createCell(0); cell.setCellType(CellType.BLANK)
                cell = row.createCell(1); cell.setCellType(CellType.STRING); cell.setCellValue("Open")
                cell = row.createCell(2); cell.setCellType(CellType.STRING); cell.setCellValue("High")
                cell = row.createCell(3); cell.setCellType(CellType.STRING); cell.setCellValue("Low")
                cell = row.createCell(4); cell.setCellType(CellType.STRING); cell.setCellValue("Close")
                cell = row.createCell(5); cell.setCellType(CellType.STRING); cell.setCellValue("High-Low")
                cell = row.createCell(6); cell.setCellType(CellType.STRING); cell.setCellValue("High-Open")
                cell = row.createCell(7); cell.setCellType(CellType.STRING); cell.setCellValue("Open-Low")
                cell = row.createCell(8); cell.setCellType(CellType.STRING); cell.setCellValue("High-Close")
                cell = row.createCell(9); cell.setCellType(CellType.STRING); cell.setCellValue("Close-Low")
                cell = row.createCell(10); cell.setCellType(CellType.STRING); cell.setCellValue("High-PreviousClose")
                cell = row.createCell(11); cell.setCellType(CellType.STRING); cell.setCellValue("PreviousClose-Low")
                cell = row.createCell(12); cell.setCellType(CellType.STRING); cell.setCellValue("Volume")

                t._2.reverse.zipWithIndex.foreach((tu: (home.QuoteActor.DailyQuote, Int)) => {
                    val dq = tu._1
                    val r = sheet.createRow(tu._2 + 1)
                    var c = r.createCell(0); c.setCellType(CellType.STRING); c.setCellValue(dq.beginsAt.substring(0, 10)) // A
                    c = r.createCell(1); c.setCellType(CellType.NUMERIC); c.setCellValue(f"${dq.openPrice}%4.4f".toDouble)        // B
                    c = r.createCell(2); c.setCellType(CellType.NUMERIC); c.setCellValue(f"${dq.highPrice}%4.4f".toDouble)        // C
                    c = r.createCell(3); c.setCellType(CellType.NUMERIC); c.setCellValue(f"${dq.lowPrice}%4.4f".toDouble)         // D
                    c = r.createCell(4); c.setCellType(CellType.NUMERIC); c.setCellValue(f"${dq.closePrice}%4.4f".toDouble)       // E
                    c = r.createCell(5); c.setCellType(CellType.FORMULA); c.setCellFormula(s"C${tu._2 + 2}-D${tu._2 + 2}")
                    c = r.createCell(6); c.setCellType(CellType.FORMULA); c.setCellFormula(s"C${tu._2 + 2}-B${tu._2 + 2}")
                    c = r.createCell(7); c.setCellType(CellType.FORMULA); c.setCellFormula(s"B${tu._2 + 2}-D${tu._2 + 2}")
                    c = r.createCell(8); c.setCellType(CellType.FORMULA); c.setCellFormula(s"C${tu._2 + 2}-E${tu._2 + 2}")
                    c = r.createCell(9); c.setCellType(CellType.FORMULA); c.setCellFormula(s"E${tu._2 + 2}-D${tu._2 + 2}")
                    c = r.createCell(12); c.setCellType(CellType.NUMERIC); c.setCellValue(dq.volume)
                    c = r.createCell(10); c.setCellType(CellType.FORMULA); c.setCellFormula(s"C${tu._2 + 2}-E${tu._2 + 3}")
                    c = r.createCell(11); c.setCellType(CellType.FORMULA); c.setCellFormula(s"E${tu._2 + 3}-D${tu._2 + 2}")
                })
            })
        })

        val outputStream = new FileOutputStream("stocks.xlsx")
        workBook.write(outputStream)
        outputStream.close()
    }
}
