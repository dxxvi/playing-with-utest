package safari

import com.softwaremill.sttp._
import com.typesafe.config.ConfigFactory
import home.util.SttpBackendUtil
import org.json4s._
import org.json4s.native.JsonMethods._
import org.json4s.native.Serialization
import org.junit.jupiter.api.Test

class BooksByPublicationDateTests extends SttpBackendUtil with home.util.JsonUtil {
    @Test
    def test(): Unit = {
        implicit val sttpBackend: SttpBackend[Id, Nothing] = configureCoreJavaHttpBackend(ConfigFactory.load())

        val cookie = "sessionid=z2t62b1167n28tpneocrpltbu2yp0sly; csrfsafari=cxxzel7OtgLfMQPW1vyhofcIuFypkkXQa4TxaBuTBm7hWsZguwqwOK9e4jyUUm4L; BrowserCookie=44e3d3f8-2d79-4f6d-954c-dbbd46b79471; groot_sessionid=6l4vkm2aa9l8ws6km0vb5ybcwyc2vn0y; orm-jwt=eyJhbGciOiAiUlMyNTYifQ.eyJhY2N0cyI6IFsiZTQ4MTBkZDAtYjJiMy00ODg3LWFiMDQtZTVmMDA2OWVhZmM2Il0sICJlaWRzIjogeyJoZXJvbiI6ICI0ZDljZDE0Ni1kYWM0LTRhOWEtOWNlMi1hZGMyMmFiMmFlNjkifSwgImV4cCI6IDE1NjA5MjA2OTIsICJpbmRpdmlkdWFsIjogdHJ1ZSwgInBlcm1zIjogeyJhY2FkbSI6ICJ2IiwgImNuZnJjIjogInYiLCAiY3NzdGQiOiAidiIsICJlcHVicyI6ICJwIiwgImxycHRoIjogInAiLCAibHZ0cmciOiAicCIsICJvcmlvbCI6ICJwIiwgInBseWxzIjogInAiLCAidXNycGYiOiAiZXYiLCAidmlkZW8iOiAicCJ9LCAic3ViIjogImYwZWNjZDUxLTFjMzEtNDg2OS1iZDIxLTMyY2Q4MzRkZjAzNSJ9.kweKJ8HQkXuNcreessQWKJ0VPLF1J4RrKRJPkzmE5QzGOesbeJ5XjQdGvt-XOOZgDL_GziqSePz-hIlyo2HmT31EIpHQyIKEtII2scA_R0wu2cVBWHRj3YnrPBX2QnJjx0W8x5D-DlAwQqzltEeMaEoGHwf00lf3WtZJBH8fUrM; orm-rt=75205bd812bc454da905e18e9c2b4ee1; logged_in=y; salesforce_id=bca1a0425f41d8e8c92623c7258df6ad"
        val url = "https://learning.oreilly.com/api/v2/search/?query=*&extended_publisher_data=true&highlight=true&include_assessments=false&include_case_studies=false&include_courses=false&include_orioles=false&include_playlists=false&include_collections=false&is_academic_institution_account=false&formats=book&sort=publication_date&page="
        val results = (4 to 90).toList flatMap (page => f(page, url, cookie))
        val jArray = JArray(results map result2JObject)
        println(Serialization.write(jArray)(DefaultFormats))
    }

    private def f(page: Int, url: String, cookie: String)(implicit backend: SttpBackend[Id, Nothing]): List[Result] = {
        println(s"Working with page $page")
        val results = sttp.header("Cookie", cookie).get(uri"$url$page").send().body match {
            case Right(jString) =>
                parse(jString) \ "results" match {
                    case JArray(jValues) => jValues map jValue2Result filter (_.title != "")
                    case _ => Nil
                }
            case _ => Nil
        }
        results
    }

    private def jValue2Result(jValue: JValue): Result = {
        val title = fromJValueToOption[String](jValue \ "title").getOrElse("")
//        val description = fromJValueToOption[String](jValue \ "description").getOrElse("no description")
        val isbn = fromJValueToOption[String](jValue \ "isbn").getOrElse("no isbn")
        val issued = fromJValueToOption[String](jValue \ "issued").getOrElse("0000-00-00")
        val coverUrl = fromJValueToOption[String](jValue \ "cover_url")
                .getOrElse("https://duckduckgo.com/assets/logo_homepage_mobile.normal.v107.svg")
        val content: List[String] = jValue \ "highlights" match {
            case x: JObject =>
                x \ "content" match {
                    case JArray(jvs) => jvs collect { case JString(line) => line }
                    case _ => Nil
                }
            case _ => Nil
        }
        val authors: List[String] = jValue \ "authors" match {
            case JArray(jvs) => jvs collect { case JString(author) => author }
            case _ => Nil
        }
        val publishers: List[String] = jValue \ "publishers" match {
            case JArray(jvs) => jvs collect { case JString(publisher) => publisher }
            case _ => Nil
        }
        Result(authors, isbn, issued, publishers, title, coverUrl, content)
    }

    private def result2JObject(r: Result): JObject = JObject(
        "authors"     -> JArray(r.authors map JString),
//        "description" -> JString(r.description),
        "isbn"        -> JString(r.isbn),
        "issued"      -> JString(r.issued),
        "publishers"  -> JArray(r.publishers map JString),
        "title"       -> JString(r.title),
        "coverUrl"    -> JString(r.coverUrl),
        "content"     -> JArray(r.content map JString)
    )

    case class Result(
                     authors: List[String] = Nil,
//                     description: String = "",
                     isbn: String = "",
                     issued: String = "", /* 2019-07-22T00:00:00Z */
                     publishers: List[String] = Nil,
                     title: String = "",
                     coverUrl: String = "",
                     content: List[String] = Nil
                     )
}
