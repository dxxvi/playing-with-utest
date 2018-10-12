package home

import utest._

object SafariVideoDownload extends TestSuite {
    val tests = Tests {
        "Generate script" - {
            val u = "safaria5nexas@gazyd.com"
            val p = "Abcd1234"
            val url = "https://www.safaribooksonline.com/videos/java-to-python/9781789611960/9781789611960-video"
            /*
             * youtube-dl --username $u --password $p -F ${url}2_4 | tail -n 1 | cut -d' ' -f1 -> the format
             */
            val list = Map(2 -> 22, 3 -> 9, 4 -> 16, 5 -> 13, 6 -> 4, 7 -> 7, 8 -> 1, 9 -> 21).toList flatMap { t => {
                1 to t._2 map (k =>
                    Seq(
                        s"FORMAT=`youtube-dl -F -u $u -p $p $url${t._1}_$k | tail -n 1 | cut -d' ' -f1`",
                        s"youtube-dl -f $$FORMAT -u $u -p $p $url${t._1}_$k",
                        "mv video*.mp4 ~/java-to-python",
                        "for i in *.mp4; do",
                        s"""  mv "$$i" "video${t._1}_$k-$$i" """,
                        "done"
                    ) mkString "\n"
                )
            }}
            list.foreach(println)
        }
    }
}
