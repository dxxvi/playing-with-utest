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

        val cookie = "sessionid=q7c5a6a5xjjzrgou6hen7n4lm4nlddc6; csrfsafari=CkvmBDfv6ckdHxw02lve5EgYyUJqHP9VnbsjDi1gUm6Wdi12sw3nVA7VoWteELJd; BrowserCookie=19cb5b6f-bc27-4909-acba-9d87225c77dd; groot_sessionid=qg9au56xt96xu989zry8i8lcplegqyd5; orm-jwt=eyJhbGciOiAiUlMyNTYifQ.eyJhY2N0cyI6IFsiNjQ5MGUyM2UtMmEwOS00ZmQ3LTllY2MtZGY1MDY1NDgyNDk0Il0sICJlaWRzIjogeyJoZXJvbiI6ICIxNGRiMmQ4ZC0wZjQ5LTRkZTItYTU5NS1kYThmODRmNjVmNGMifSwgImV4cCI6IDE1NjIxMjAwMTIsICJpbmRpdmlkdWFsIjogdHJ1ZSwgInBlcm1zIjogeyJhY2FkbSI6ICJ2IiwgImNuZnJjIjogInYiLCAiY3NzdGQiOiAidiIsICJlcHVicyI6ICJwIiwgImxycHRoIjogInAiLCAibHZ0cmciOiAicCIsICJvcmlvbCI6ICJwIiwgInBseWxzIjogInAiLCAidXNycGYiOiAiZXYiLCAidmlkZW8iOiAicCJ9LCAic3ViIjogIjU4NGI2M2ViLTliOGItNGU2NS1hZDM0LTMwYTM1Y2YzMWVkZCJ9.cw7MDlS-0L2UIouV24Ie4wDRWBj9tDeo1i1BplPPkrrmjLXE0fVQ_ZMQOlznTLdnhB3RHTrWY1fTYMPwEsDFeDF3Y9xSKQkCdw4063bFOeLZdVjo8ebK8sJi-fmoO1oE1qbzxEcI9lj9O8VeTymU6MDR2Ri-JuMODMzHMz7w3yk; orm-rt=22f3dab1e4414fa2ab208f9f1d4c8190; logged_in=y; salesforce_id=697539016608d00d145f103445b0f47d"
        val url = "https://learning.oreilly.com/api/v2/search/?query=*&extended_publisher_data=true&highlight=true&include_assessments=false&include_case_studies=true&include_courses=false&include_orioles=false&include_playlists=false&include_collections=false&is_academic_institution_account=false&formats=book&sort=publication_date&facet_json=true&page="
        val results = (5 to 90).toList flatMap (page => f(page, url, cookie))
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
