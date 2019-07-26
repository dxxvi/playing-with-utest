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

        val cookie = "sessionid=p3905uuih09ifurdelmcgkq2u0dg4mkm; BrowserCookie=2a477f61-5a23-46af-a2e9-8537481818a9; csrfsafari=XSDxz8tgKNbaHsuqfCW6fni8YLhQ2dqhv7q4a4jogNBQUTzV0FYayXlmWFbdcd6g; groot_sessionid=9p4wu4d4ooqrmbk3osajcnoeu2oewas8; orm-jwt=eyJhbGciOiAiUlMyNTYifQ.eyJhY2N0cyI6IFsiYmUyMTYxZWItOTQ5MS00NTIxLTgxYmMtNGZmMjQ3NmFmOGFlIl0sICJlaWRzIjogeyJoZXJvbiI6ICJmOWMwNmVmOS0yOGQ1LTQ1OTMtYThjNy03NjFkMjBmNzFhM2MifSwgImVudiI6ICJwcm9kdWN0aW9uIiwgImV4cCI6IDE1NjQxMDEzNzUsICJpbmRpdmlkdWFsIjogdHJ1ZSwgInBlcm1zIjogeyJhY2FkbSI6ICJ2IiwgImNuZnJjIjogInYiLCAiY3NzdGQiOiAidiIsICJlcHVicyI6ICJwIiwgImxycHRoIjogInAiLCAibHZ0cmciOiAicCIsICJvcmlvbCI6ICJwIiwgInBseWxzIjogInAiLCAidXNycGYiOiAiZXYiLCAidmlkZW8iOiAicCJ9LCAic3ViIjogIjM5MzE1MTEyLTM2MzItNDgxNC1iZDhlLWQ3MDk5OWJhY2ZkNiJ9.rxTvSopEyyQhUEP-bbMjOljJRCTxWUphAYUn1z-FWJOAIh9ZRPKXmBb0HAo3ZeXXBWrYSQ3nxswNBnZ0HdXi-yqhBI8sV-u_3Iz6DWcdyNwq6qZZ7xhENgid4ZxlF8RzCqcfuu7VJQjHnQp5gd7GbcMIF7hUIDt3Q9dkkKhkMsE; orm-rt=0e835d80b22448b984b473bb39af85ce; salesforce_id=c26e4abccfc35552fcd9072462ca040b; logged_in=y"
        val url = "https://learning.oreilly.com/api/v2/search/?query=*&extended_publisher_data=true&highlight=true&include_assessments=false&include_case_studies=true&include_courses=true&include_orioles=true&include_playlists=true&include_collections=false&include_notebooks=false&is_academic_institution_account=false&formats=book&formats=video&sort=publication_date&facet_json=true&page="
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
