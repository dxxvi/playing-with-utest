package home

import java.nio.file.{Files, Path, StandardOpenOption}

import akka.event.LoggingAdapter
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.softwaremill.sttp._
import org.json4s.DefaultFormats
import org.json4s.JsonAST.JObject
import org.json4s.native.JsonMethods._
import org.json4s.native.Serialization

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.Try

object StockDatabase extends WatchedListUtil {
    def create(accessToken: String)
              (implicit be1: SttpBackend[Future, Source[ByteString, Any]],
                        be2: SttpBackend[Id, Nothing],
                        ec: ExecutionContext,
                        log: LoggingAdapter): StockDatabase = {
        implicit val defaultFormats: DefaultFormats = DefaultFormats
        /**
          * This file is a json {
          *   "instrument-1": { ... the json body Robinhood returns ... }, ...
          * }
          */
        val stockDatabaseFilePath = Path.of("StockDatabase.json")
        val stockDatabaseJObject = if (Files.exists(stockDatabaseFilePath)) {
            parse(Files.readString(stockDatabaseFilePath)).asInstanceOf[JObject]
        } else {
            JObject()
        }

        val instruments = Try(Await.result(retrieveWatchedInstruments(accessToken), 9.seconds))
        if (instruments.isFailure) {
            log.error(instruments.failed.get, "Unable to get watched instruments.")
            System.exit(-1)
        }

        var text = ""
        print(s"You have ${instruments.get.length} watched symbols, processing: ")
        // TODO https://api.robinhood.com/instruments/?ids=id1,id2,id3,... works. It's not important to implement this
        //  because we already cached it in StockDatabase.json
        val instrumentJStringTuples: List[(String, String)] = instruments.get.zipWithIndex
                .map { case (instrument, i) =>
                    if (text.nonEmpty) print("\b" * text.length)
                    text = s"${i + 1}"
                    print(text)
                    val body = stockDatabaseJObject findField { case (fieldName, _) => fieldName == instrument } match {
                        case Some(field) => Right(Serialization.write(field._2))
                        case _ =>
                            val _body =
                                fetchSynchronous(s"https://api.robinhood.com/instruments/$instrument/", be2, log)
                            Thread.sleep(3000)
                            _body
                    }
                    (instrument, body)
                }
                .collect {
                    /*
                     * {
                         "bloomberg_unique": "EQ0000000046910575",
                         "country": "US",
                         "day_trade_ratio": "0.2500",
                         "fundamentals": "https://api.robinhood.com/fundamentals/DOW/",
                         "id": "776d31c1-e278-4476-9d03-9e7125fe946c",
                         "list_date": "2019-04-02",
                         "maintenance_ratio": "1.0000",
                         "margin_initial_ratio": "1.0000",
                         "market": "https://api.robinhood.com/markets/XNYS/",
                         "min_tick_size": null,
                         "name": "Dow Inc.",
                         "quote": "https://api.robinhood.com/quotes/DOW/",
                         "rhs_tradability": "tradable",
                         "simple_name": "Dow",
                         "splits": "https://api.robinhood.com/instruments/776d31c1-e278-4476-9d03-9e7125fe946c/splits/",
                         "state": "active",
                         "symbol": "DOW",
                         "tradability": "tradable",
                         "tradable_chain_id": "d840ddab-7980-43ac-a9d1-33d4d5d7e590",
                         "tradeable": true,
                         "type": "stock",
                         "url": "https://api.robinhood.com/instruments/776d31c1-e278-4476-9d03-9e7125fe946c/"
                       }
                     */
                    case (instrument, Right(jString)) => (instrument, jString)
                }
        Files.write(
            stockDatabaseFilePath,
            Serialization.writePretty(JObject(instrumentJStringTuples.map(t => (t._1, parse(t._2))))).getBytes(),
            StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING
        )
        val symbolInstrumentTuples: List[(String, String)] = instrumentJStringTuples
                .map { case (instrument, jString) =>
                    (instrument, fromJValueToOption[String](parse(jString) \ "symbol"))
                }
                .collect { case (instrument, Some(symbol)) => (symbol, instrument) }

        println()
        new StockDatabase(symbolInstrumentTuples.toMap)
    }

    private def fetchSynchronous(url: String,
                                 _backend: SttpBackend[Id, Nothing],
                                 log: LoggingAdapter): Either[String, String] = {
        implicit val backend: SttpBackend[Id, Nothing] = _backend
        sttp.get(uri"$url").send().body
    }
}

/**
  * @param symbol2instrument e.g. "DOW" -> "776d31c1-e278-4476-9d03-9e7125fe946c"
  */
class StockDatabase(symbol2instrument: Map[String, String]) {
    private val instrument2symbol: Map[String, String] = symbol2instrument.toList
        .map { case (symbol, instrument) => (instrument, symbol) }
        .toMap

    def findInstrument(symbol: String): String = symbol2instrument.getOrElse(symbol, s"NO-INSTRUMENT-FOR-$symbol")

    def findSymbol(instrument: String): String = instrument2symbol.getOrElse(instrument, s"NO-SYMBOL-FOR-$instrument")

    def iterate: List[(String /* symbol */, String /* instrument */)] = symbol2instrument.toList

    def allSymbols: List[String] = symbol2instrument.keySet.toList

    def allInstruments: List[String] = instrument2symbol.keySet.toList

    def isEmpty: Boolean = symbol2instrument.isEmpty

    def nonEmpty: Boolean = symbol2instrument.nonEmpty
}
