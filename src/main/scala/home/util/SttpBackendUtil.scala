package home.util

import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.softwaremill.sttp._
import com.typesafe.config.Config

import scala.concurrent.Future

trait SttpBackendUtil {
    def configureAkkaHttpBackend(config: Config): SttpBackend[Future, Source[ByteString, Any]] = {
        import com.softwaremill.sttp.akkahttp._

        if (!config.hasPath("proxy.host") || !config.hasPath("proxy.port")) AkkaHttpBackend()
        else if (!config.hasPath("proxy.username") || !config.hasPath("proxy.password"))
            AkkaHttpBackend(SttpBackendOptions.httpProxy(config.getString("proxy.host"), config.getInt("proxy.port")))
        else AkkaHttpBackend(
            SttpBackendOptions.httpProxy(
                config.getString("proxy.host"), config.getInt("proxy.port"),
                config.getString("proxy.username"), config.getString("proxy.password")
            )
        )
    }

    def configureCoreJavaHttpBackend(config: Config): SttpBackend[Id, Nothing] = {
        if (!config.hasPath("proxy.host") || !config.hasPath("proxy.port")) HttpURLConnectionBackend()
        else if (!config.hasPath("proxy.username") || !config.hasPath("proxy.password"))
            HttpURLConnectionBackend(SttpBackendOptions.httpProxy(config.getString("proxy.host"), config.getInt("proxy.port")))
        else HttpURLConnectionBackend(
            SttpBackendOptions.httpProxy(
                config.getString("proxy.host"), config.getInt("proxy.port"),
                config.getString("proxy.username"), config.getString("proxy.password")
            )
        )
    }
}
