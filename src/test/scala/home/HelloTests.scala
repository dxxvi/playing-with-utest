package home

import java.io.File
import java.nio.file.{Files, Path, Paths, StandardOpenOption}

import com.typesafe.scalalogging.Logger
import home.akka.AkkaPlayground
import home.sparkjava.Main
import home.sparkjava.model.Order
import utest._

import scala.collection.concurrent.TrieMap
import scala.io.Source

object HelloTests extends TestSuite {
    val logger: Logger = Logger[Util]

    def modify(file: File): Unit = {
        val root = new File("/home/ly/nginx-root")
    }

    val tests = Tests {
        'test1 - {
            throw new Exception("test1")
        }
        'test2 - {
            logger.debug(s"Search for all .html files and modify them:")
            Files.walk(Paths.get("/home/ly/nginx-root"))
                    .filter(p => p.toFile.isFile && (p.toFile.getName endsWith ".html"))
                    .forEach((p: Path) => {
                        val bs = Source.fromFile(p.toFile)
                        var s = bs.mkString
                        bs.close()
                        s = s.replaceAll(" -&gt;", " &#8594;")
                        Files.write(p, s.getBytes, StandardOpenOption.TRUNCATE_EXISTING)
                    })
        }
        'test3 - {
            new AkkaPlayground().httpRequest()
        }

        "test splitting timestamp" - {
            val ts = "2018-06-04T19:36:43.251000Z".split("[-T:Z]")
            println(ts.length)
        }
    }
}