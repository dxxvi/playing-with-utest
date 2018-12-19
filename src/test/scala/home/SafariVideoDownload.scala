package home

import utest._

object SafariVideoDownload extends TestSuite {
    val tests = Tests {
        "Generate script" - {
            val u = "safaria9nexas"
            val p = "Abcd1234"
            val url = "https://www.safaribooksonline.com/videos/hands-on-docker-for/9781788999960/9781788999960-video"
            /*
             * youtube-dl --username $u --password $p -F ${url}2_4 | tail -n 1 | cut -d' ' -f1 -> the format
             */
            val list = Map(1 -> 6, 2 -> 8, 3 -> 5, 4 ->4, 5-> 6).toList flatMap { t => {
                1 to t._2 map (k =>
                    Seq(
                        s"FORMAT=`youtube-dl -F -u $u -p $p $url${t._1}_$k | grep '1920x1080' | tail -n 1 | cut -d' ' -f1`",
                        s"youtube-dl -f $$FORMAT -u $u -p $p $url${t._1}_$k",
                        "mv v-*.mp4 ~/hands-on-docker-for-microservices",
                        "for i in *.mp4; do ",
                        "  n=`echo $i | sed -e 's/-[0-9]_[0-9a-z]\\{8\\}\\.mp4/\\.mp4/g'`",
                        """  mv "$i" "$n" """,
                        f"""  mv "$$n" "v-${t._1}_$k%02d-$$n" """,
                        "done",
                    ) mkString "\n"
                )
            }}
            list.foreach(println)
        }
    }
}
