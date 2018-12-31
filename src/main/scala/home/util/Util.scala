package home.util

object Util extends SttpBackends {
    import com.typesafe.config.Config

    def getSymbolFromInstrumentHttpURLConnection(url: String, config: Config): String = {
        import com.softwaremill.sttp._
        import org.json4s._
        import org.json4s.native.JsonMethods._

        implicit val backend: SttpBackend[Id, Nothing] = configureCoreJavaHttpBackend(config)
        sttp.get(uri"$url").send().body match {
            case Right(jString) => (parse(jString) \ "symbol").asInstanceOf[JString].values
            case Left(s) => throw new RuntimeException(s)
        }
    }
}
