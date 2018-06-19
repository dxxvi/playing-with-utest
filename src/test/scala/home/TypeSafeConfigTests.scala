package home

import utest._

object TypeSafeConfigTests extends TestSuite {
    import com.typesafe.config.ConfigFactory

    val tests = Tests {
        'test1 - {
            val config = ConfigFactory.load()
            println(s"server: ${config.getString("server")}")
            println(s"robinhood.a: ${config.getString("robinhood.a")}, robinhood.c: ${config.getInt("robinhood.c")}")
        }
    }
}
