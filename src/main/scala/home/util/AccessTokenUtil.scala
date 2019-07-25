package home.util

trait AccessTokenUtil {
    /**
      * If accessToken is provided in system environment property (e.g. export accessToken=XXX) or system property
      * (e.g. java -DaccessToken=XXX ...), then it'll be used.
      */
    def retrieveAccessToken(): Option[String] =
        sys.env.get("accessToken") match {
            case x @ Some(_) => x
            case _ =>
                println(
                    """|+-----------------------------------------------------------------------+
                       || If you already have the accessToken, run export accessToken=... first |
                       |+-----------------------------------------------------------------------+""".stripMargin)
                None
        }
}
