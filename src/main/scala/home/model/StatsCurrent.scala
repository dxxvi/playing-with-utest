package home.model

import org.json4s.{JDouble, JInt, JObject}

case class StatsCurrent(
                               currl3m: Int,  // compare current-low to all high-low
                               hcurr3m: Int,  // compare high-current to all high-low
                               curro3m: Int,  // compare current-open to all high-open
                               ocurr3m: Int,  // compare open-current to all open-low
                               currpc3m: Int, // compare current-previousClose to all high-previousClose
                               pccurr3m: Int, // compare previousClose-current to all previousClose-low
                               h3m: Int,      // compare current to all high
                               l3m: Int,      // compare current to all low
                               currl1m: Int,
                               hcurr1m: Int,
                               curro1m: Int,
                               ocurr1m: Int,
                               currpc1m: Int,
                               pccurr1m: Int,
                               h1m: Int,
                               l1m: Int,
                               high: Double,
                               low: Double,
                               open: Double,
                               previousClose: Double
                       ) {
    def toJObject: JObject = JObject(
        "currl3m"       -> JInt(currl3m),
        "hcurr3m"       -> JInt(hcurr3m),
        "curro3m"       -> JInt(curro3m),
        "ocurr3m"       -> JInt(ocurr3m),
        "currpc3m"      -> JInt(currpc3m),
        "pccurr3m"      -> JInt(pccurr3m),
        "h3m"           -> JInt(h3m),
        "l3m"           -> JInt(l3m),
        "currl1m"       -> JInt(currl1m),
        "hcurr1m"       -> JInt(hcurr1m),
        "curro1m"       -> JInt(curro1m),
        "ocurr1m"       -> JInt(ocurr1m),
        "currpc1m"      -> JInt(currpc1m),
        "pccurr1m"      -> JInt(pccurr1m),
        "h1m"           -> JInt(h1m),
        "l1m"           -> JInt(l1m),
        "high"          -> JDouble(if (high.isNaN) -1 else high ),
        "low"           -> JDouble(if (low.isNaN) -1 else low),
        "open"          -> JDouble(if (open.isNaN) -1 else open),
        "previousClose" -> JDouble(if (previousClose.isNaN) -1 else previousClose)
    )
}