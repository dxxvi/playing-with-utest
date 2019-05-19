package home.model

case class Stats(
    HL_3m: Array[Double],    // each array has 99 elements representing the 1, 2, ... 99 percentile
    HO_3m: Array[Double],
    OL_3m: Array[Double],
    HPC_3m: Array[Double],
    PCL_3m: Array[Double],
    H_3m: Array[Double],
    L_3m: Array[Double],
    HL_1m: Array[Double],
    HO_1m: Array[Double],
    OL_1m: Array[Double],
    HPC_1m: Array[Double],
    PCL_1m: Array[Double],
    H_1m: Array[Double],
    L_1m: Array[Double],
    var high: Double = Double.MinPositiveValue,
    var low: Double = Double.MaxValue,
    var open: Double = Double.NaN,
    var previousClose: Double = Double.NaN
) {
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
        l1m(ltp)
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
