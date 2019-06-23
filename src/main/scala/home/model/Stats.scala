package home.model

case class Stats(
    _HL_3m: Array[Double],    // each array has 100 elements representing the 1, 2, ... 100 percentile
    _HO_3m: Array[Double],
    _OL_3m: Array[Double],
    _HPC_3m: Array[Double],
    _PCL_3m: Array[Double],
    _H_3m: Array[Double],
    _L_3m: Array[Double],
    _HL_1m: Array[Double],
    _HO_1m: Array[Double],
    _OL_1m: Array[Double],
    _HPC_1m: Array[Double],
    _PCL_1m: Array[Double],
    _H_1m: Array[Double],
    _L_1m: Array[Double],
    var high: Double = Double.MinPositiveValue,
    var low: Double = Double.MaxValue,
    var open: Double = Double.NaN,
    var previousClose: Double = Double.NaN
) {
    val HL_3m: Array[Double]  = makeLongerArray(_HL_3m) // has 199 elements representing the 1, 2, ... 199 percentile
    val HO_3m: Array[Double]  = makeLongerArray(_HO_3m)
    val OL_3m: Array[Double]  = makeLongerArray(_OL_3m)
    val HPC_3m: Array[Double] = makeLongerArray(_HPC_3m)
    val PCL_3m: Array[Double] = makeLongerArray(_PCL_3m)
    val H_3m: Array[Double]   = makeLongerArray(_H_3m)
    val L_3m: Array[Double]   = makeLongerArray(_L_3m)
    val HL_1m: Array[Double]  = makeLongerArray(_HL_1m)
    val HO_1m: Array[Double]  = makeLongerArray(_HO_1m)
    val OL_1m: Array[Double]  = makeLongerArray(_OL_1m)
    val HPC_1m: Array[Double] = makeLongerArray(_HPC_1m)
    val PCL_1m: Array[Double] = makeLongerArray(_PCL_1m)
    val H_1m: Array[Double]   = makeLongerArray(_H_1m)
    val L_1m: Array[Double]   = makeLongerArray(_L_1m)

    /**
      * @return an array of a(0) a(1) ... a(n) a(n)+a(n)-a(n-1) a(n)+a(n)-a(n-2) ... a(n)+a(n)-a(0)
      */
    private def makeLongerArray(a: Array[Double]): Array[Double] = {
        val n = a.length - 1
        val tail = (1 to n) map (i => a(n) + a(n) - a(n-i))
        a ++ tail
    }

    override def toString: String =
        s"""  HL_3m: [${HL_3m.map(d => f"$d%.2f").mkString(", ")}]
           |  HO_3m: [${HO_3m.map(d => f"$d%.2f").mkString(", ")}]
           |  OL_3m: [${OL_3m.map(d => f"$d%.2f").mkString(", ")}]
           |  HPC_3m: [${HPC_3m.map(d => f"$d%.2f").mkString(", ")}]
           |  PCL_3m: [${PCL_3m.map(d => f"$d%.2f").mkString(", ")}]
           |  H_3m: [${H_3m.map(d => f"$d%.2f").mkString(", ")}]
           |  L_3m: [${L_3m.map(d => f"$d%.2f").mkString(", ")}]
           |  HL_1m: [${HL_1m.map(d => f"$d%.2f").mkString(", ")}]
           |  HO_1m: [${HO_1m.map(d => f"$d%.2f").mkString(", ")}]
           |  OL_1m: [${OL_1m.map(d => f"$d%.2f").mkString(", ")}]
           |  HPC_1m: [${HPC_1m.map(d => f"$d%.2f").mkString(", ")}]
           |  PCL_1m: [${PCL_1m.map(d => f"$d%.2f").mkString(", ")}]
           |  H_1m: [${H_1m.map(d => f"$d%.2f").mkString(", ")}]
           |  L_1m: [${L_1m.map(d => f"$d%.2f").mkString(", ")}]
           |  high: $high
           |  low: $low
           |  open: $open
           |  previousClose: $previousClose""".stripMargin
    
    def toStatsCurrent(ltp: Double): StatsCurrent = StatsCurrent(
        hl3m(ltp - low),
        hl3m(high -ltp),
        ho3m(ltp - open),
        ol3m(open - ltp),
        hpc3m(ltp - previousClose),
        pcl3m(previousClose - ltp),
        h3m(ltp),
        l3m(ltp),
        hl1m(ltp - low),
        hl1m(high - ltp),
        ho1m(ltp - open),
        ol1m(open - ltp),
        hpc1m(ltp - previousClose),
        pcl1m(previousClose - ltp),
        h1m(ltp),
        l1m(ltp),
        if (high.isNaN) high else f"$high%.2f".toDouble,
        if (low.isNaN) low else f"$low%.2f".toDouble,
        if (open.isNaN) open else f"$open%.2f".toDouble,
        if (previousClose.isNaN) previousClose else f"$previousClose%.2f".toDouble
    )
    
    def hl3m(d: Double): Int = HL_3m.takeWhile(_ <= d).length
    def ho3m(d: Double): Int = HO_3m.takeWhile(_ <= d).length
    def ol3m(d: Double): Int = OL_3m.takeWhile(_ <= d).length
    def hpc3m(d: Double): Int = HPC_3m.takeWhile(_ <= d).length
    def pcl3m(d: Double): Int = PCL_3m.takeWhile(_ <= d).length
    def h3m(d: Double): Int = H_3m.takeWhile(_ <= d).length
    def l3m(d: Double): Int = L_3m.takeWhile(_ <= d).length

    def hl1m(d: Double): Int = HL_1m.takeWhile(_ <= d).length
    def ho1m(d: Double): Int = HO_1m.takeWhile(_ <= d).length
    def ol1m(d: Double): Int = OL_1m.takeWhile(_ <= d).length
    def hpc1m(d: Double): Int = HPC_1m.takeWhile(_ <= d).length
    def pcl1m(d: Double): Int = PCL_1m.takeWhile(_ <= d).length
    def h1m(d: Double): Int = H_1m.takeWhile(_ <= d).length
    def l1m(d: Double): Int = L_1m.takeWhile(_ <= d).length

    def delta: Double = high - low
}
