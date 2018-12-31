package home.util

trait SttpBackends {
    import scala.concurrent.Future
    import akka.stream.scaladsl.Source
    import akka.util.ByteString
    import com.typesafe.config.Config
    import com.softwaremill.sttp._

    private def getProxyHostAndPort(config: Config): Option[(String, Int)] = for {
            proxyHost <- if (config.hasPath("proxy.host")) Some(config.getString("proxy.host")) else None
            proxyPort <- if (config.hasPath("proxy.port")) Some(config.getInt("proxy.port")) else None
        } yield (proxyHost, proxyPort)

    def configureAkkaHttpBackend(config: Config): SttpBackend[Future, Source[ByteString, Any]] = {
        import com.softwaremill.sttp.akkahttp._

        getProxyHostAndPort(config)
                .fold(AkkaHttpBackend())(t => AkkaHttpBackend(options = SttpBackendOptions.httpProxy(t._1, t._2)))
    }

    def configureCoreJavaHttpBackend(config: Config): SttpBackend[Id, Nothing] = getProxyHostAndPort(config)
            .fold(HttpURLConnectionBackend())(t => HttpURLConnectionBackend(options = SttpBackendOptions.httpProxy(t._1, t._2)))
}
