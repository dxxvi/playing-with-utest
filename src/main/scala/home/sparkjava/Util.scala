package home.sparkjava

import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.softwaremill.sttp._
import com.typesafe.config.Config
import org.apache.logging.log4j.scala.Logger
import org.json4s._

import scala.concurrent.Future
import scala.reflect.runtime.universe._
import scala.util.Try

trait Util {
    protected val logger: Logger = Logger(getClass)

    // we need this because sttp's uri"..." doesn't work correctly
    def encodeUrl(s: String): String = s.replace(":", "%" + "3A").replace("/", "%" + "2F")

    def isDow(symbol: String): Boolean = Main.dowStocks.contains(symbol)

    def configureAkkaHttpBackend(config: Config): SttpBackend[Future, Source[ByteString, Any]] = {
        import com.softwaremill.sttp.akkahttp._

        val o = for {
            proxyHost <- if (config.hasPath("proxy.host")) Some(config.getString("proxy.host")) else None
            proxyPort <- if (config.hasPath("proxy.port")) Some(config.getInt("proxy.port")) else None
        } yield (proxyHost, proxyPort)

        o.fold(AkkaHttpBackend())(t => AkkaHttpBackend(options = SttpBackendOptions.httpProxy(t._1, t._2)))
    }

    def fromStringToOption[T: TypeTag](jValue: JValue, field: String): Option[T] = jValue \ field match {
        case JString(x) => typeOf[T] match {
            case t if t =:= typeOf[Double] => Try(x.toDouble).toOption.asInstanceOf[Option[T]]
            case t if t =:= typeOf[Int] => Try(x.toInt).toOption.asInstanceOf[Option[T]]
            case t if t =:= typeOf[String] => Some(x).asInstanceOf[Option[T]]
            case t if t =:= typeOf[Boolean] => Try(x.toBoolean).asInstanceOf[Option[T]]
        }
        case x => None
    }

    // T is String, Int, Long or Boolean only.
    def fromToOption[T: TypeTag](jValue: JValue, field: String): Option[T] = jValue \ field match {
        case JString(x) => typeOf[T] match {
            case t if t =:= typeOf[String] => Some(x).asInstanceOf[Option[T]]
        }
        case JInt(num) => typeOf[T] match {
            case t if t =:= typeOf[Int] => Some(num.toInt).asInstanceOf[Option[T]]
            case t if t =:= typeOf[Long] => Some(num.toLong).asInstanceOf[Option[T]]
        }
        case JLong(num) => typeOf[T] match {
            case t if t =:= typeOf[Int] => Some(num.toInt).asInstanceOf[Option[T]]
            case t if t =:= typeOf[Long] => Some(num.toLong).asInstanceOf[Option[T]]
        }
        case JBool(bool) => typeOf[T] match {
            case t if t =:= typeOf[Boolean] => Some(bool).asInstanceOf[Option[T]]
        }
        case _ => None
    }
}
