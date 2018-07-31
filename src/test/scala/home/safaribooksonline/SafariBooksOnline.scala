package home.safaribooksonline

import java.nio.file.{Files, Paths}
import java.nio.file.StandardOpenOption._
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentLinkedQueue

import spark.{Request, Response}
import spark.Spark._

object SafariBooksOnline extends App {
    val list = new ConcurrentLinkedQueue[String]()

    port(8080)

    get("/end", (_, res: Response) => {
        Files.write(Paths.get("/dev/shm", "index.html"), list, CREATE, TRUNCATE_EXISTING)

        res.`type`("application/json")
        s"""{"now":"${LocalDateTime.now.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)}"}"""
    })

    post("/safaribooksonline-text", (req: Request, res: Response) => {
        list.add(req.body())
        res.`type`("application/json")
        s"""{"now":"${LocalDateTime.now.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)}"}"""
    })
}

class SafariBooksOnline {}