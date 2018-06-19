package home

import java.io.File
import java.nio.file.{Files, Path, Paths, StandardOpenOption}

import com.typesafe.scalalogging.Logger
import home.akka.AkkaPlayground
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

        "test Trait with member variable" - {
            trait MyTrait {
                val trieMap: TrieMap[String, String] = TrieMap()
            }

            class ClassA extends MyTrait {
                def put(k: String, v: String): Unit = trieMap += ((k, v))
            }

            class ClassB extends MyTrait {
                def get(k: String): Option[String] = trieMap.get(k)
            }

            val a = new ClassA
            val b = new ClassB
            a.put("one", "mot")
            println(b.get("one"))
        }
    }
}