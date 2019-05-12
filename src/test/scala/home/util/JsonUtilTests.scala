package home.util

import org.json4s.JsonAST.JString
import org.junit.jupiter.api.{Assertions, Test}

class JsonUtilTests extends JsonUtil {
    @Test
    def test1(): Unit = {
        fromJValueToOption[String](JString("xxx")) match {
            case Some(_) =>
            case _ => Assertions.fail("Fail with T of String, jValue of JString")
        }

        fromJValueToOption[Int](JString("4")) match {
            case Some(4) =>
            case _ => Assertions.fail("""Fail with [Int]JString("4")""")
        }

        fromJValueToOption[Long](JString("4")) match {
            case Some(4) =>
            case _ => Assertions.fail("""Fail with [Long]JString("4")""")
        }
    }
}
