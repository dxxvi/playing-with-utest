package home.util

import org.json4s._

import scala.reflect.runtime.universe._
import scala.util.{Success, Try}

trait JsonUtil {
    /**
      * Convert a json4s JValue to an Option of any given type
      */
    def fromJValueToOption[T: TypeTag](jValue: JValue): Option[T] = jValue match {
        case JString(x) => typeOf[T] match {
            case t if t =:= typeOf[String] => Some(x).asInstanceOf[Option[T]]
            case t if t =:= typeOf[Int] =>  Try(x.toInt) match {
                case Success(i) => Some(i).asInstanceOf[Option[T]]
                case _ => None.asInstanceOf[Option[T]]
            }
            case t if t =:= typeOf[Long] => Try(x.toLong) match {
                case Success(l) => Some(l).asInstanceOf[Option[T]]
                case _ => None.asInstanceOf[Option[T]]
            }
            case t if t =:= typeOf[Double] => Try(x.toDouble) match {
                case Success(d) => Some(d).asInstanceOf[Option[T]]
                case _ => None.asInstanceOf[Option[T]]
            }
            case t if t =:= typeOf[Boolean] => Try(x.toBoolean) match {
                case Success(b) => Some(b).asInstanceOf[Option[T]]
                case _ => None.asInstanceOf[Option[T]]
            }
            case _ => None.asInstanceOf[Option[T]]
        }
        case JInt(num) => typeOf[T] match {
            case t if t =:= typeOf[Int] => Some(num.toInt).asInstanceOf[Option[T]]
            case t if t =:= typeOf[Long] => Some(num.toLong).asInstanceOf[Option[T]]
            case t if t =:= typeOf[Double] => Some(num.toDouble).asInstanceOf[Option[T]]
            case _ => None.asInstanceOf[Option[T]]
        }
        case JLong(num) => typeOf[T] match {
            case t if t =:= typeOf[Int] => Some(num.toInt).asInstanceOf[Option[T]]
            case t if t =:= typeOf[Long] => Some(num.toLong).asInstanceOf[Option[T]]
            case t if t =:= typeOf[Double] => Some(num.toDouble).asInstanceOf[Option[T]]
            case _ => None.asInstanceOf[Option[T]]
        }
        case JDouble(num) => typeOf[T] match {
            case t if t =:= typeOf[Int] => Some(num.toInt).asInstanceOf[Option[T]]
            case t if t =:= typeOf[Long] => Some(num.toLong).asInstanceOf[Option[T]]
            case t if t =:= typeOf[Double] => Some(num.toDouble).asInstanceOf[Option[T]]
            case _ => None.asInstanceOf[Option[T]]
        }
        case JBool(bool) => typeOf[T] match {
            case t if t =:= typeOf[Boolean] => Some(bool).asInstanceOf[Option[T]]
            case _ => None.asInstanceOf[Option[T]]
        }
        case JNull => typeOf[T] match {
            case t if t =:= typeOf[Double] => Some(Double.NaN).asInstanceOf[Option[T]]
            case _ => None.asInstanceOf[Option[T]]
        }
        case _ => None
    }
}
