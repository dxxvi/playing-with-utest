package home

import java.nio.file.{Files, Path}
import java.nio.file.StandardOpenOption._
import java.util.UUID

import com.typesafe.config.{Config, ConfigFactory}
import spark.{Request, Response, Spark}

import scala.util.{Failure, Success, Try}

object Main {
    def main(args: Array[String]): Unit = {
        val config: Config = ConfigFactory.load()

        if (!config.hasPath("port")) {
            println("Syntax: java -Dport=xxx -jar post-save.jar")
            System.exit(-1)
        }

        val port = config.getInt("port")

        Spark.port(port)
        Spark.post("/", (request: Request, response: Response) => {
            import org.json4s._
            import org.json4s.native.JsonMethods._
            Try (
                parse(request.body) \ "body" match {
                    case JString(s) =>
                        Files.writeString(Path.of(UUID.randomUUID().toString), s, CREATE, TRUNCATE_EXISTING)
                }
            ) match {
                case Success(_) => ""
                case Failure(_) => """{"_":1}"""
            }
        })
    }
}
