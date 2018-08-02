package home.sparkjava

import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.softwaremill.sttp._
import com.typesafe.config.Config
import org.apache.logging.log4j.scala.Logger

import scala.concurrent.Future
import scala.reflect.runtime.universe._

trait Util {
    protected val logger: Logger = Logger(getClass)

    def configureAkkaHttpBackend(config: Config): SttpBackend[Future, Source[ByteString, Any]] = {
        import com.softwaremill.sttp.akkahttp._

        val o = for {
            proxyHost <- if (config.hasPath("proxy.host")) Some(config.getString("proxy.host")) else None
            proxyPort <- if (config.hasPath("proxy.port")) Some(config.getInt("proxy.port")) else None
        } yield (proxyHost, proxyPort)

        o.fold(AkkaHttpBackend())(t => AkkaHttpBackend(options = SttpBackendOptions.httpProxy(t._1, t._2)))
    }
}
