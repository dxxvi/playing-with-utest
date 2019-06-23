package home.util

import com.softwaremill.sttp._
import com.typesafe.config.Config

import scala.util.{Failure, Success}

trait AccessTokenUtil extends SttpBackendUtil with JsonUtil {
    /**
      * If accessToken is provided in system environment property (e.g. export accessToken=XXX) or system property
      * (e.g. java -DaccessToken=XXX ...), then it'll be used. Otherwise, we'll get it from Robinhood.
      */
    def retrieveAccessToken(config: Config): Either[String, String] = {
        import org.json4s._
        import org.json4s.JsonAST.{JObject, JString}
        import org.json4s.native.JsonMethods._
        import org.json4s.native.Serialization

        if (config.hasPath("accessToken")) {
            println(
                """
                  |+--------------------------------------+
                  || Use accessToken from system property |
                  |+--------------------------------------+""".stripMargin)
            return Right(config.getString("accessToken"))
        }

        sys.env.get("accessToken") match {
            case Some(accessToken) =>
                println(
                    """
                      |+--------------------------------------------------+
                      || Use accessToken from system environment property |
                      |+--------------------------------------------------+""".stripMargin)
                return Right(accessToken)
            case _ =>
        }

        println(
            """
              |********************************************************************************************************
              |* Using authorization.usename and authorization.encryptedPassword in system properties for accessToken *
              |********************************************************************************************************
            """.stripMargin)
        implicit val sttpBackend: SttpBackend[Id, Nothing] = configureCoreJavaHttpBackend(config)
        Encryption.decrypt(Encryption.getKey(config), config.getString("authorization.encryptedPassword")) match {
            case Success(password) =>
                val body = Serialization.write(JObject(
                    "username" -> JString(config.getString("authorization.username")),
                    "password" -> JString(password),
                    "grant_type" -> JString("password"),
                    "client_id" -> JString("c82SH0WZOsabOXGP2sxqcj34FxkvfnWRZBKlBjFS"),
                    "expires_in" -> JInt(86400),
                    "device_token" -> JString("6b8d6af2-68da-43b6-bc67-ad2cd3066045"),
                    "scope" -> JString("internal")
                ))(DefaultFormats)
                sttp
                        .header(HeaderNames.ContentType, MediaTypes.Json)
                        .body(body)
                        .post(uri"https://api.robinhood.com/oauth2/token/")
                        .send()
                        .body match {
                    case Right(jString) => fromJValueToOption[String](parse(jString) \ "access_token") match {
                        case Some(accessToken) =>
                            println(s"Access token: $accessToken")
                            Right(accessToken)
                        case _ => Left(s"No access_token field in $jString")
                    }
                    case x => x
                }
            case Failure(exception) => Left(exception.getMessage)
        }
    }
}
