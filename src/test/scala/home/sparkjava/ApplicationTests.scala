package home.sparkjava

import com.softwaremill.sttp._
import org.json4s._
import org.json4s.native.JsonMethods._
import org.junit.jupiter.api.Assertions._
import org.junit.jupiter.api.Test

class ApplicationTests {
    @Test
    def test() {
        implicit val sttpBackend: SttpBackend[Id, Nothing] = HttpURLConnectionBackend()

        sttp.get(uri"http://localhost:4560/debug/AXP").send().body match {
            case Left(s) => fail(s)
            case Right(s) => println(s)
        }
    }
}
