package home

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.stream.ActorMaterializer
import utest._

import scala.concurrent.ExecutionContext
import scala.io.StdIn

object SafariTextUpload extends TestSuite { val tests: Tests = Tests {
    "Receives text from safari using Akka HTTP" - {
        implicit val actorSystem: ActorSystem = ActorSystem("safari")
        implicit val actorMaterializer: ActorMaterializer = ActorMaterializer()
        implicit val executionContext: ExecutionContext = actorSystem.dispatcher

        val route =
            path("end") {
                get {
                    // TODO write to file
                    complete(
                        HttpEntity(
                            ContentTypes.`application/json`,
                            s"${LocalDateTime.now.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)}"
                        )
                    )
                }
            }
        val bindingFuture = Http().bindAndHandle(route, "localhost", 8080)
        StdIn.readLine()
        bindingFuture.flatMap(_.unbind()).onComplete(_ => actorSystem.terminate())
    }

    "Receives text from safari using SparkJava" - {
        import java.nio.file.{Files, Paths}
        import java.nio.file.StandardOpenOption._
        import spark.{Request, Response}
        import spark.Spark._
        val list = new java.util.concurrent.ConcurrentLinkedQueue[String]()

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

        println("Press Enter to end")
        scala.io.StdIn.readLine()
    }

    "beautify html files" - {
        import java.nio.file.{Files, Paths}
        import java.nio.file.StandardOpenOption._
        import scala.io.Source
        import org.jsoup.Jsoup
        import org.jsoup.nodes.{Document, Element}

        // fix the Table of Contents in Packt books
        def forPackt(document: Document): Unit = {
            object Utils {
                def distance(e1: org.jsoup.nodes.Element, e2: org.jsoup.nodes.Element): Int = distance(e1, e2, 0)

                @scala.annotation.tailrec
                private def distance(e1: org.jsoup.nodes.Element, e2: org.jsoup.nodes.Element, d: Int): Int = {
                    if (e1 == null || e2 == null)
                        Int.MaxValue
                    else if (e1 == e2)
                        d
                    else {
                        distance(e1.parent(), e2, d + 1)
                    }
                }
            }

            val regex = """([a-z\d]{8}-[a-z\d]{4}-[a-z\d]{4}-[a-z\d]{4}-[a-z\d]{12})\.html""".r
            val toc = toOption[Element](document.getElementById("toc"))
            toc.map(t => (t, t.getElementsByTag("li"))).foreach(tuple => tuple._2.forEach(li => {
                li.addClass(s"level${Utils.distance(li, tuple._1) / 2}")  // .removeAttr("style")
            }))
            toc.map(t => t.getElementsByTag("a")).foreach(_.forEach(e => {
                val id = e.attr("href").replace(".xhtml", "")
                e.attr("href", s"#$id")
                val levelClass = e.parent.className
                val title = e.text

                document.getElementsByTag("h1").forEach(h1 => {
                    if (!h1.className.contains("level") && title == h1.text) {
                        h1.removeClass("header-title").addClass(levelClass).attr("id", id)
                    }
                })
            }))
        }

        def toOption[T](x: Any): Option[T] = if (x == null) None else Some(x.asInstanceOf[T])

        Seq(
            "/home/ly/nginx-root/mastering-functional-programming/index.html"
        ).foreach(fileName => {
            val bufferedSource = Source.fromFile(fileName)

            // make <pre at the beginning of a line and </pre> at the end of a line
            var s = """<pre""".r.replaceAllIn(bufferedSource.mkString, "\n<pre")
            s = """</pre>""".r.replaceAllIn(s, "</pre>\n")

            bufferedSource.close()

            /*
             * replace all the <pre ...</pre> with <div class="pre" id="uuid..."></div> and keep these replacements
             * in this map
             */
            var replacements = Map.empty[String, Seq[String]]
            var currentUUID: Option[String] = None
            s = s
                    .replaceAll("&amp;#x", "&#x")  // packt books have this issue
                    .split('\n')
                    .map {
                        case l1: String if l1.startsWith("<pre") =>
                            val uuid = java.util.UUID.randomUUID.toString
                            replacements = replacements + (uuid -> List(l1))
                            currentUUID = Some(uuid)
                            if (l1.endsWith("</pre>")) currentUUID = None
                            s"""<div class="pre" id="$uuid"></div>"""
                        case l2: String if l2.endsWith("</pre>") =>
                            currentUUID.flatMap(replacements.get(_))
                                    .foreach(list => replacements = replacements + (currentUUID.get -> (list :+ l2)))
                            currentUUID = None
                            ""
                        case l3: String => currentUUID match {
                            case Some(uuid) =>
                                replacements = replacements + (uuid -> (replacements(uuid) :+ l3))
                                ""
                            case None => l3
                        }
                    }
                    .mkString("\n")

            val outputSettings = new Document.OutputSettings().indentAmount(2).prettyPrint(true).charset("UTF-8")
            // beautify
            val document = Jsoup.parse(s).outputSettings(outputSettings)
            forPackt(document)

            val regex = """^([ ]+)<div class="pre" id="([a-z\d]{8}-[a-z\d]{4}-[a-z\d]{4}-[a-z\d]{4}-[a-z\d]{12})"></div>[ ]*$""".r
            // put the <pre's back
            s = document.outerHtml
                    .replaceAll("(&nbsp;)+</a>", "</a>")
                    .split('\n')
                    .map {
                        case regex(space, uuid) =>
                            space + replacements.getOrElse(uuid, Seq("""<div class="not-found"></div>""")).mkString("\n")
                        case l => l
                    }
                    .mkString("\n")
            Files.write(Paths.get(fileName), s.getBytes, CREATE, TRUNCATE_EXISTING)
        })
    }
}}
