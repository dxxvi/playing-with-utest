package home

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

trait AppUtil {
    /**
      * @param url is like https://api.robinhood.com/instruments/dad8fa2c-1e8d-4cb9-b354-1f0b91a4193e/
      * @return dad8fa2c-1e8d-4cb9-b354-1f0b91a4193e
      */
    def extractInstrument(url: String): String =
        url.replace("https://api.robinhood.com/instruments/", "").replace("/", "")

    /**
      * @param _ca1 and ca2 are like "2018-11-19T17:08:09.123456Z", "2018-11-19T17:08:09Z" or "2018-11-19T17:08:09.123Z"
      * @return -1 if ca1 is before ca2, 1 if ca1 is after ca2
      */
    def compareCreatedAts(_ca1: String, _ca2: String): Int = {
        val regex = "[-:TZ]"
        (_ca1.split(regex).toStream zip _ca2.split(regex).toStream)
                .map { case (s1, s2) => s1.toDouble compareTo s2.toDouble }
                .find(_ != 0)
                .getOrElse(0)
    }

    def putTogetherUuid(uuid1: String, uuid2: String): String =
        if (uuid1 < uuid2) uuid1 + "-" + uuid2 else uuid2 + "-" + uuid1

    /**
      * Add n seconds to timestamp and return a string
      */
    def addSeconds(timestamp: String /* 2019-04-19T02:03:04Z */, n: Int): String /* 2019-04-19T03:04:05Z */ = {
        val localDateTime = LocalDateTime.parse(
            timestamp.replace("Z", ""), DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        localDateTime.plusSeconds(n).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) + "Z"
    }
}
