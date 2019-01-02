package home.util

trait SttpBackends {
    import scala.concurrent.Future
    import akka.stream.scaladsl.Source
    import akka.util.ByteString
    import com.typesafe.config.Config
    import com.softwaremill.sttp._

    private var akkaHttpBackend: Option[SttpBackend[Future, Source[ByteString, Any]]] = None
    private var coreJavaHttpBackend: Option[SttpBackend[Id, Nothing]] = None

    private def getProxyHostAndPort(config: Config): Option[(String, Int)] = for {
            proxyHost <- if (config.hasPath("proxy.host")) Some(config.getString("proxy.host")) else None
            proxyPort <- if (config.hasPath("proxy.port")) Some(config.getInt("proxy.port")) else None
        } yield (proxyHost, proxyPort)

    def configureAkkaHttpBackend(config: Config): SttpBackend[Future, Source[ByteString, Any]] = {
        import com.softwaremill.sttp.akkahttp._

        if (akkaHttpBackend.isEmpty)
            akkaHttpBackend = Some(getProxyHostAndPort(config)
                .fold(AkkaHttpBackend())(t => AkkaHttpBackend(options = SttpBackendOptions.httpProxy(t._1, t._2))))

        akkaHttpBackend.get
    }

    def configureCoreJavaHttpBackend(config: Config): SttpBackend[Id, Nothing] = {
        if (coreJavaHttpBackend.isEmpty)
            coreJavaHttpBackend = Some(getProxyHostAndPort(config)
                .fold(HttpURLConnectionBackend())(t => HttpURLConnectionBackend(options = SttpBackendOptions.httpProxy(t._1, t._2))))

        coreJavaHttpBackend.get
    }
}
