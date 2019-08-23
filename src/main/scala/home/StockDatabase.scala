package home

import java.nio.file.{Files, Path, StandardOpenOption}

import akka.event.LoggingAdapter
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.softwaremill.sttp._
import org.json4s._
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

        val instruments: Try[List[String]] = Try(Await.result(retrieveWatchedInstruments(accessToken), 9.seconds))
        if (instruments.isFailure) {
            log.error(instruments.failed.get, "Unable to get watched instruments.")
            System.exit(-1)
        }

        val instrumentsNotInStockDatabase = instruments.get.toSet diff stockDatabaseJObject.values.keySet
        val newInstrumentJValueList = fetchSynchronous(instrumentsNotInStockDatabase, be2) match {
            case Left(errorString) =>
                log.error(errorString + "\nExit.")
                System.exit(-1)
                Nil
            case Right(jArray) =>
                jArray.arr.map(jv => ((jv \ "id").asInstanceOf[JString].s, jv))
        }

        Files.write(
            stockDatabaseFilePath,
            Serialization
                    .writePretty(JObject(newInstrumentJValueList))
                    .getBytes,
            StandardOpenOption.CREATE, StandardOpenOption.APPEND
        )

        val existingSymbolInstrumentList = stockDatabaseJObject.values.toList.map { case (instrument, _any) =>
            ((_any.asInstanceOf[JObject] \ "symbol").asInstanceOf[JString].values, instrument)
        }

        val newSymbolInstrumentList = newInstrumentJValueList.map { case (instrument, jv) => (
                (jv.asInstanceOf[JObject] \ "symbol").asInstanceOf[JString].s,
                instrument
        )}

        new StockDatabase((existingSymbolInstrumentList ++ newSymbolInstrumentList).toMap)
    }

    private def fetchSynchronous(instruments: Set[String],
                                 _backend: SttpBackend[Id, Nothing]): Either[String, JArray] = {
        implicit val backend: SttpBackend[Id, Nothing] = _backend
        var text = ""
        print(s"You have ${instruments.size} watched symbols, processing: ")
        val seed: Either[String, JArray] = Right(JArray(Nil))
        instruments
                .grouped(13)
                .map("https://api.robinhood.com/instruments/?ids=" + _.mkString(","))
                .map(url => {
                    Thread.sleep(3000)
                    sttp.get(uri"$url").send().body match {
                        case Left(x) => Left(x)
                        case Right(s) =>
                            if (text.nonEmpty) print("\b" * text.length)
                            val jArrayEither = Right((parse(s) \ "results").asInstanceOf[JArray])
                            val numberOfProcessedSymbols = jArrayEither.getOrElse(JArray(Nil)).arr.length +
                                    (if (text.nonEmpty) text.toInt else 0)
                            text = numberOfProcessedSymbols.toString
                            print(text)
                            jArrayEither
                    }
                }) // Iterator[Either[String, JArray]]
                .foldLeft(seed)((_seed, either) => {
                    _seed match {
                        case Left(x) => Left(x)
                        case Right(jArray) => either match {
                            case Left(x) => Left(x)
                            case Right(anotherJArray) => Right(JArray(anotherJArray.arr ++ jArray.arr))
                        }
                    }
                })
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