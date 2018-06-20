package home.sparkjava

import java.net.InetSocketAddress

import akka.actor.ActorSystem
import akka.http.scaladsl.ClientTransport
import akka.http.scaladsl.settings.{ClientConnectionSettings, ConnectionPoolSettings}
import com.typesafe.config.Config
import com.typesafe.scalalogging.Logger
import home.sparkjava.model._
import home.sparkjava.model.OrderProtocol._
import home.sparkjava.model.PositionProtocol._
import home.sparkjava.model.QuoteProtocol._
import home.sparkjava.model.FundamentalProtocol._
import spray.json._

import scala.reflect.runtime.universe._

trait Util {
    def getConnectionPoolSettings(config: Config, system: ActorSystem): ConnectionPoolSettings = {
        val proxyHost: Option[String] = if (config.hasPath("proxy.host")) Some(config.getString("proxy.host")) else None
        val proxyPort: Option[Int] = if (config.hasPath("proxy.port")) Some(config.getInt("proxy.port")) else None
        val clientTransport: Option[ClientTransport] = for {
            _proxyHost <- proxyHost
            _proxyPort <- proxyPort
        } yield ClientTransport.httpsProxy(InetSocketAddress.createUnresolved(_proxyHost, _proxyPort))
        val settings = ConnectionPoolSettings(system).withConnectionSettings(ClientConnectionSettings(system))
        clientTransport match {
            case Some(ct) => settings.withTransport(ct)
            case _ => settings
        }
    }

    def getFundamentals(json: String): Vector[Fundamental] = json.parseJson.asJsObject.fields.get("results") match {
        case Some(jsValue) => jsValue match {
            case x: JsArray => x.elements.map(_.convertTo[Fundamental])
            case _ => throw new RuntimeException(s"Fields results is not an array in $json")
        }
        case _ => throw new RuntimeException(s"No results field in the json $json")
    }

    def getOrders(json: String): Vector[Order] = json.parseJson.asJsObject.fields.get("results") match {
        case Some(jsValue) => jsValue match {
            case x: JsArray => x.elements.map(_.convertTo[Order])
            case _ => throw new RuntimeException(s"Fields results is not an array in $json")
        }
        case _ => throw new RuntimeException(s"No results field in the json $json")
    }

    def getQuotes(json: String): Vector[Quote] = json.parseJson.asJsObject().fields.get("results") match {
        case Some(jsValue) => jsValue match {
            case x: JsArray => x.elements.collect { case x: JsValue if x != JsNull => x.convertTo[Quote] }
            case _ => throw new RuntimeException(s"Field results is not an array in $json")
        }
        case _ => throw new RuntimeException(s"No field results in $json")
    }

    def getPositions(json: String): Vector[model.Position] = json.parseJson.asJsObject.fields.get("results") match {
        case Some(jsValue) => jsValue match {
            case x: JsArray => x.elements.map(_.convertTo[model.Position])
            case _ => throw new RuntimeException(s"Field results is not an array in $json")
        }
        case _ => throw new RuntimeException(s"No field results in $json")
    }

    def getFieldValue[T: TypeTag](field: String)
                                 (implicit fields: Map[String, JsValue], prettyJson: String, logger: Logger): T =
        fields.get(field) match {
            case x: Some[JsValue] => typeOf[T] match {
                case t if t =:= typeOf[String] => x.get match {
                    case y: JsString => y.value.asInstanceOf[T]
                    case JsNull => "NULL".asInstanceOf[T]
                    case _ =>
                        logger.error(s"Field $field in $prettyJson is not a string.")
                        defaultValue[String]().asInstanceOf[T]
                }
                case t if t =:= typeOf[Int] => x.get match {
                    case y: JsNumber => y.value.intValue().asInstanceOf[T]
                    case y: JsString => y.value.toInt.asInstanceOf[T]
                    case JsNull => Int.MinValue.asInstanceOf[T]
                    case _ =>
                        logger.error(s"Field $field in $prettyJson is not an integer.")
                        defaultValue[Int]().asInstanceOf[T]
                }
                case t if t =:= typeOf[Boolean] => x.get match {
                    case y: JsBoolean => y.value.asInstanceOf[T]
                    case _ =>
                        logger.error(s"Field $field in $prettyJson is not a boolean.")
                        defaultValue[Boolean]().asInstanceOf[T]
                }
                case t if t =:= typeOf[Double] => x.get match {
                    case y: JsNumber => y.value.doubleValue().asInstanceOf[T]
                    case y: JsString => y.value.toDouble.asInstanceOf[T]
                    case JsNull => Double.NaN.asInstanceOf[T]
                    case _ =>
                        logger.error(s"Field $field in $prettyJson is not a double.")
                        defaultValue[Double]().asInstanceOf[T]
                }
            }
            case _ =>
                logger.error(s"No field $field in $prettyJson")
                defaultValue[T]()
        }

    private def defaultValue[T: TypeTag](): T = typeOf[T] match {
        case t if t =:= typeOf[String] => "string_default_value".asInstanceOf[T]
        case t if t =:= typeOf[Int] => 0.asInstanceOf[T]
        case t if t =:= typeOf[Double] => Double.NaN.asInstanceOf[T]
        case t if t =:= typeOf[Boolean] => false.asInstanceOf[T]
        case _ => throw new RuntimeException(s"don't know the default value for type ${typeOf[T]}")
    }
}
