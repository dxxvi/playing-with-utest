package home.sparkjava.model

import org.json4s._
import org.json4s.native.JsonMethods._
import org.json4s.native.Serialization

import scala.reflect.runtime.universe._
import scala.util.Try

object Fundamental {
    def deserialize(s: String): Fundamental = {
        def fromStringToOption[T: TypeTag](jValue: JValue, field: String): Option[T] = jValue \ field match {
            case JString(x) => typeOf[T] match {
                case t if t =:= typeOf[Double] => Try(x.toDouble).toOption.asInstanceOf[Option[T]]
                case t if t =:= typeOf[Int] => Try(x.toInt).toOption.asInstanceOf[Option[T]]
                case t if t =:= typeOf[String] => Some(x).asInstanceOf[Option[T]]
                case t if t =:= typeOf[Boolean] => Try(x.toBoolean).asInstanceOf[Option[T]]
            }
            case _ => None
        }

        val jValue = parse(s)
        Fundamental(
            fromStringToOption[Double](jValue, "average_volume"),
            fromStringToOption[Double](jValue, "average_volume_2_weeks"),
            fromStringToOption[String](jValue,"description"),
            fromStringToOption[Double](jValue, "dividend_yield"),
            fromStringToOption[Double](jValue, "high"),
            fromStringToOption[Double](jValue, "high_52_weeks"),
            fromStringToOption[Double](jValue, "low"),
            fromStringToOption[Double](jValue, "low_52_weeks"),
            fromStringToOption[Double](jValue, "open"),
            fromStringToOption[Double](jValue, "pe_ratio")
        )
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
                      pe_ratio: Option[Double]
                      )

