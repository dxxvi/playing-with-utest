package home.model

case class Quote(beginsAt: String, open: Double, close: Double, high: Double, low: Double, var ema: Double = Double.NaN)