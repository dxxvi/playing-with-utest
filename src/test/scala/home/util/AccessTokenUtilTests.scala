package home.util

import com.typesafe.config.ConfigFactory
import org.junit.jupiter.api.{Assertions, Disabled, Test}

class AccessTokenUtilTests extends AccessTokenUtil {
    @Disabled("To run this test, put the correct values in access-token-util.conf")
    @Test
    def testRetrieveAccessToken(): Unit = {
        retrieveAccessToken(ConfigFactory.load("access-token-util.conf")) match {
            case Right("test-access-token") =>
            case _ => Assertions.fail("check access-token-util.conf")
        }
    }
}
