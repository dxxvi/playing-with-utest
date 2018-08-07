package home.sparkjava.model

import home.sparkjava.Util
import org.json4s._
import org.json4s.native.JsonMethods._
import org.json4s.native.Serialization

object Fundamental extends Util {
    /**
      * @param s like in fundamentals.json
      */
    def deserialize(s: String): List[Fundamental] = {
        parse(s) \ "results" match {
            case JArray(jValues) =>
                jValues map { jValue => Fundamental(
                    fromStringToOption[Double](jValue, "average_volume"),
                    fromStringToOption[Double](jValue, "average_volume_2_weeks"),
                    fromStringToOption[String](jValue, "description"),
                    fromStringToOption[Double](jValue, "dividend_yield"),
                    fromStringToOption[Double](jValue, "high"),
                    fromStringToOption[Double](jValue, "high_52_weeks"),
                    fromStringToOption[Double](jValue, "low"),
                    fromStringToOption[Double](jValue, "low_52_weeks"),
                    fromStringToOption[Double](jValue, "open"),
                    fromStringToOption[Double](jValue, "pe_ratio"),
                    fromStringToOption[String](jValue, "instrument").getOrElse("~hm~")
                )}
            case _ => List[Fundamental]()
        }
    }

    def serialize(f: Fundamental): String = Serialization.write[Fundamental](f)(DefaultFormats)
}

case class Fundamental(
                      average_volume: Option[Double],
                      average_volume_2_weeks: Option[Double],
                      description: Option[String],
                      dividend_yield: Option[Double],
                      high: Option[Double],
                      high_52_weeks: Option[Double],
                      low: Option[Double],
                      low_52_weeks: Option[Double],
                      open: Option[Double],
                      pe_ratio: Option[Double],
                      instrument: String
                      )

