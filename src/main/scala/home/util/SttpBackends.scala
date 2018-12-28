package home.util

trait SttpBackends {
    import scala.concurrent.Future
    import akka.stream.scaladsl.Source
    import akka.util.ByteString
    import com.softwaremill.sttp.SttpBackend
    import com.typesafe.config.Config

    def configureAkkaHttpBackend(config: Config): SttpBackend[Future, Source[ByteString, Any]] = {
        import com.softwaremill.sttp.akkahttp._
        import com.softwaremill.sttp.SttpBackendOptions

        val option: Option[(String, Int)] = for {
            proxyHost <- if (config.hasPath("proxy.host")) Some(config.getString("proxy.host")) else None
            proxyPort <- if (config.hasPath("proxy.port")) Some(config.getInt("proxy.port")) else None
        } yield (proxyHost, proxyPort)

        option.fold(AkkaHttpBackend())(t => AkkaHttpBackend(options = SttpBackendOptions.httpProxy(t._1, t._2)))
    }
}
