package home

import java.io.FileOutputStream
import java.math.BigInteger

import akka.event.LoggingAdapter
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.softwaremill.sttp._
import com.typesafe.config.ConfigFactory
import home.model.{Order, Quote}
import home.util.{AccessTokenUtil, LoggingAdapterImpl, SttpBackendUtil}
import org.apache.poi.ss.usermodel.{BorderStyle, CellType, HorizontalAlignment, VerticalAlignment}
import org.apache.poi.ss.util.{CellRangeAddress, RegionUtil}
import org.apache.poi.xssf.usermodel.{XSSFCell, XSSFCellStyle, XSSFRow, XSSFSheet, XSSFWorkbook}

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

object GenerateExcel extends OrderUtil with AccessTokenUtil with PositionUtil with WatchedListUtil with QuoteUtil
        with SttpBackendUtil {
    def main(args: Array[String]): Unit = {
        val config = ConfigFactory.load("credentials.conf")

        val accessToken = retrieveAccessToken().get
        implicit val backend1: SttpBackend[Future, Source[ByteString, Any]] = configureAkkaHttpBackend(config)
        implicit val backend2: SttpBackend[Id, Nothing] = configureCoreJavaHttpBackend(config)
        implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.global
        implicit val log: LoggingAdapter = LoggingAdapterImpl

        val stockDatabase = StockDatabase.create(accessToken)

        var wb = new XSSFWorkbook()

        val positions: Map[String /*instrument*/, (Double /*quantity*/, String /*account*/)] =
            Await.result(getAllPositions(accessToken), 5.seconds)
        positions
                .collect {
                    case (instrument, (quantity, _)) if quantity > 0 =>
                        val orders = getAllStandardizedOrdersForInstrument(accessToken, instrument)
                        val effectiveOrders =
                            getEffectiveOrders(quantity, orders, log).filter(o => !o.state.contains("confirmed"))
                        val symbol = stockDatabase.findSymbol(instrument)
                        (symbol, effectiveOrders)
                }
                .toList
                .sortBy(_._1)
                .foreach { case (symbol, effectiveOrders) =>
                    val sheet = wb.createSheet(symbol)
                    var row = sheet.createRow(0)
                    row.createCell(0).setCellValue("created_at") // A
                    row.createCell(1).setCellValue("id")         // B
                    row.createCell(2).setCellValue("side")       // C
                    row.createCell(3).setCellValue("price")      // D
                    row.createCell(4).setCellValue("state")      // E
                    row.createCell(5).setCellValue("quantity")   // F
                    row.createCell(6).setCellValue("matchId")    // G

                    var i = 1
                    effectiveOrders foreach { case Order(createdAt, id, side, _, price, state, _, quantity, _, matchId) =>
                        val r = sheet.createRow(i)
                        var c = r.createCell(0); c.setCellType(CellType.STRING); c.setCellValue(createdAt.replace("T", " ").replace("Z", ""))
                        c = r.createCell(1); c.setCellType(CellType.STRING); c.setCellValue(id)
                        c = r.createCell(2); c.setCellType(CellType.STRING); c.setCellValue(side)
                        c = r.createCell(3); c.setCellType(CellType.NUMERIC); c.setCellValue(f"$price%.4f".toDouble)
                        c = r.createCell(4); c.setCellType(CellType.STRING); c.setCellValue(state)
                        c = r.createCell(5); c.setCellType(CellType.NUMERIC); c.setCellValue(f"$quantity%.0f".toInt)
                        c = r.createCell(6); c.setCellType(CellType.STRING); c.setCellValue(matchId.getOrElse(""))
                        c = r.createCell(7); c.setCellType(CellType.STRING)
                        c.setCellValue(matchId match {
                            case None => ""
                            case Some(uuid) => shortenUuid(uuid)
                        })                                                                                                               // H
                        r.createCell(8).setCellFormula(s"""IF(C${i + 1}="buy",F${i + 1},-F${i + 1})""")                     // I
                        r.createCell(9).setCellFormula(s"""IF(C${i + 1}="buy",-D${i + 1}*F${i + 1},D${i + 1}*F${i + 1})""") // J
                        i += 1
                    }
                    row = sheet.getRow(0)
                    row.createCell(10).setCellFormula(s"sum(I2:I$i)")
                    row.createCell(11).setCellFormula(s"sum(J2:J$i)/K1")

                    (0 to 7).foreach(sheet.autoSizeColumn(_, true))
                }

        var fos = new FileOutputStream("orders.xlsx")
        wb.write(fos)
        fos.close()

        wb = new XSSFWorkbook()

        val watchedInstruments = Await.result(retrieveWatchedInstruments(accessToken), 5.seconds)
        val watchedSymbols = watchedInstruments.map(stockDatabase.findSymbol)
        val dailyQuotes: List[(String /* symbol */, String, String, List[Quote])] =
            Await.result(getDailyQuotes(accessToken, watchedSymbols), 19.seconds).sortBy(_._1)
        dailyQuotes foreach { case (symbol, _, _, quotes) =>
            val sheet = wb.createSheet(symbol)
            val row = sheet.createRow(0)
            var cell = row.createCell(0); cell.setCellType(CellType.BLANK)
            cell = row.createCell(1); cell.setCellType(CellType.STRING); cell.setCellValue("Open")        // B
            cell = row.createCell(2); cell.setCellType(CellType.STRING); cell.setCellValue("High")        // C
            cell = row.createCell(3); cell.setCellType(CellType.STRING); cell.setCellValue("Low")         // D
            cell = row.createCell(4); cell.setCellType(CellType.STRING); cell.setCellValue("Close")       // E
            cell = row.createCell(5); cell.setCellType(CellType.STRING); cell.setCellValue("Hi-Low")      // F
            cell = row.createCell(6); cell.setCellType(CellType.STRING); cell.setCellValue("Hi-Open")     // G
            cell = row.createCell(7); cell.setCellType(CellType.STRING); cell.setCellValue("Open-Low")    // H
            cell = row.createCell(8); cell.setCellType(CellType.STRING); cell.setCellValue("Hi-Close")    // I
            cell = row.createCell(9); cell.setCellType(CellType.STRING); cell.setCellValue("Close-Low")   // J
            cell = row.createCell(10); cell.setCellType(CellType.STRING); cell.setCellValue("Hi-PrevC")   // K
            cell = row.createCell(11); cell.setCellType(CellType.STRING); cell.setCellValue("PrevC-Low")  // L

            quotes.reverse.zipWithIndex foreach { case (q, i) =>
                val r = sheet.createRow(i + 1)
                createCell(r, 0, CellType.STRING).setCellValue(q.beginsAt.substring(2, 10))  // A
                createCell(r, 1, CellType.NUMERIC).setCellValue(f"${q.open}%4.4f".toDouble)  // B
                createCell(r, 2, CellType.NUMERIC).setCellValue(f"${q.high}%4.4f".toDouble)  // C
                createCell(r, 3, CellType.NUMERIC).setCellValue(f"${q.low}%4.4f".toDouble)   // D
                createCell(r, 4, CellType.NUMERIC).setCellValue(f"${q.close}%4.4f".toDouble) // E
                var c = r.createCell(5); c.setCellFormula(s"C${i + 2}-D${i + 2}")
                c = r.createCell(6); c.setCellFormula(s"C${i + 2}-B${i + 2}")
                c = r.createCell(7); c.setCellFormula(s"B${i + 2}-D${i + 2}")
                c = r.createCell(8); c.setCellFormula(s"C${i + 2}-E${i + 2}")
                c = r.createCell(9); c.setCellFormula(s"E${i + 2}-D${i + 2}")
                c = r.createCell(10); c.setCellFormula(s"C${i + 2}-E${i + 3}")
                c = r.createCell(11); c.setCellFormula(s"E${i + 3}-D${i + 2}")                                         // L
            }

            val cellStyle1 = cell.getCellStyle.clone().asInstanceOf[XSSFCellStyle]
            cellStyle1.setAlignment(HorizontalAlignment.CENTER)
            cellStyle1.setVerticalAlignment(VerticalAlignment.CENTER)
            cellStyle1.setRotation(90)

            setCellF(sheet, 24, 34, 13, "Open - Low", cellStyle1)
            setCellF(sheet, 12, 22, 13, "High - Open", cellStyle1)
            setCellF(sheet, 0, 10, 13, "High - Low", cellStyle1)
            setCellF(sheet, 24, 34, 20, "Close - Low", cellStyle1)
            setCellF(sheet, 12, 22, 20, "Previous Close - Low", cellStyle1)
            setCellF(sheet, 0, 10, 20, "High - Previous Close", cellStyle1)

            Seq("Percentile", "1 month", "3 months", "6 months", "1 year").zipWithIndex foreach (u =>
                Seq((0, 14 + u._2), (0, 21 + u._2), (12, 14 + u._2), (12, 21 + u._2), (24, 14 + u._2), (24, 21 + u._2)) foreach { v =>
                    sheet.getRow(v._1).createCell(v._2).setCellValue(u._1)
                }
            )
            for (ir <- Seq(1, 13, 25); ic <- Seq(14, 21); i <- 0 to 9) {
                val c = sheet.getRow(ir + i).createCell(ic)
                c.setCellType(CellType.NUMERIC)
                c.setCellValue(if (i == 0) .99 else 1 - .05*i)
            }
            for {
                u <- Seq(("F", 1, 15, "O"), ("G", 13, 15, "O"), ("H", 25, 15, "O"), ("K", 1, 22, "V"), ("L", 13, 22, "V"), ("J", 25, 22, "V"))
                v <- Seq (23, 63, 127, 252).zipWithIndex
                i <- 0 to 9
            } {
                val c = sheet.getRow(u._2 + i).createCell(u._3 + v._2)
                c.setCellFormula(s"PERCENTILE($$${u._1}$$2:$$${u._1}$$${v._1}, ${u._4}${u._2 + i + 1})")
            }

            (0 to 25).filter(i => i != 12 && i != 19).foreach(sheet.autoSizeColumn(_, true))
        }

        fos = new FileOutputStream("stocks.xlsx")
        wb.write(fos)
        fos.close()

        wb = new XSSFWorkbook()

        val fiveMinQuotes: List[(String /* symbol */, String, String, List[Quote])] =
            Await.result(get5minQuotes(accessToken, watchedSymbols), 19.seconds).sortBy(_._1)

        fiveMinQuotes foreach { case (symbol, _, _, quotes) =>
            val sheet = wb.createSheet(symbol)
            val row = sheet.createRow(0)
            createCell(row, 0, CellType.NUMERIC).setCellValue(0.2)
            createCell(row, 1, CellType.STRING).setCellValue("Open")    // B
            createCell(row, 2, CellType.STRING).setCellValue("High")    // C
            createCell(row, 3, CellType.STRING).setCellValue("Low")     // D
            createCell(row, 4, CellType.STRING).setCellValue("Close")   // E
            createCell(row, 5, CellType.STRING).setCellValue("Avg")     // F
            createCell(row, 6, CellType.STRING).setCellValue("EMA")     // G

            var nextDate = ""
            quotes.reverse.zipWithIndex foreach { case (q, i) =>
                val r = sheet.createRow(i + 1)
                val datetime = q.beginsAt.substring(5).replace("Z", "").replace("T", " ")
                val date = datetime.substring(0, 5)
                createCell(r, 0, CellType.STRING).setCellValue(datetime)                     // A
                createCell(r, 1, CellType.NUMERIC).setCellValue(f"${q.open}%4.4f".toDouble)  // B
                createCell(r, 2, CellType.NUMERIC).setCellValue(f"${q.high}%4.4f".toDouble)  // C
                createCell(r, 3, CellType.NUMERIC).setCellValue(f"${q.low}%4.4f".toDouble)   // D
                createCell(r, 4, CellType.NUMERIC).setCellValue(f"${q.close}%4.4f".toDouble) // E
                val c = r.createCell(5); c.setCellFormula(s"(C${i + 2}+D${i + 2})/2")       // F
                if (nextDate != "" && nextDate != date) {
                    // TODO there's a problem here: we're working on row i+1 but we go back to row i to modify some cell
                    sheet.getRow(i).getCell(6).setCellFormula(s"F${i + 1}")
                }
                r.createCell(6)
                        .setCellFormula(s"IF(ISBLANK(G${i+3}), F${i+2}, A1 * F${i+2} + (1-A1) * G${i+3})")
                nextDate = date
            }
        }

        fos = new FileOutputStream("stocks-5min.xlsx")
        wb.write(fos)
        fos.close()

        System.exit(0)                 // TODO because of Akka, this main method cannot exit?
    }

    private def createCell(row: XSSFRow, columIndex: Int, cellType: CellType): XSSFCell = {
        val c = row.createCell(columIndex)
        c.setCellType(cellType)
        c
    }

    private def setCellF(sheet: XSSFSheet, firstRow: Int, lastRow: Int, col: Int, value: String,
                         cellStyle: XSSFCellStyle): Unit = {
        val row = sheet.getRow(firstRow)
        val cell = row.createCell(col)
        cell.setCellValue(value)
        cell.setCellStyle(cellStyle)
        val hlCellRangeAddress = new CellRangeAddress(firstRow, lastRow, col, col)
        sheet.addMergedRegion(hlCellRangeAddress)
        RegionUtil.setBorderLeft(BorderStyle.MEDIUM, hlCellRangeAddress, sheet)
    }

    /**
     * @param uuid can be any hex string with '-' inside
     * @return that hex number % (36*36) then converted to a string [0-9a-z]{2}
     */
    private def shortenUuid(uuid: String): String = {
        val x = new BigInteger(uuid.replaceAll("-", ""), 16).mod(new BigInteger(36*36 + "")).intValue()
        Integer.toString(x, 36)
    }
}
