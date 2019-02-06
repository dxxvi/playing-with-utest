package home.util

object StockDatabase {
    import home.model.Instrument

    private val symbol2Instrument: collection.mutable.Map[String, Instrument] =
        collection.mutable.Map[String, Instrument]()
    private val instrument2Instrument: collection.mutable.Map[String, Instrument] =
        collection.mutable.Map[String, Instrument]()

    def addDowStock(symbol: String, instrument: String, name: String, simpleName: String) {
        val i = Instrument(symbol, instrument, name, simpleName, StockType.DOW)
        addStock(symbol, instrument, i)
    }

    def addNasdaq100Stock(symbol: String, instrument: String, name: String, simpleName: String) {
        val i = Instrument(symbol, instrument, name, simpleName, StockType.NASDAQ100)
        addStock(symbol, instrument, i)
    }

    def addRegularStock(symbol: String, instrument: String, name: String, simpleName: String) {
        val i = Instrument(symbol, instrument, name, simpleName, StockType.REGULAR)
        addStock(symbol, instrument, i)
    }

    private def addStock(symbol: String, instrument: String, i: Instrument): Unit = {
        symbol2Instrument += (symbol -> i)
        instrument2Instrument += (instrument -> i)
    }

    def containsInstrument(instrument: String): Boolean = instrument2Instrument.contains(instrument)

    def getInstrumentFromSymbol(symbol: String): Option[Instrument] = symbol2Instrument.get(symbol)

    def getInstrumentFromInstrument(instrument: String): Option[Instrument] = instrument2Instrument.get(instrument)

    def debug(): Unit = println(
        s"""symbol2Instrument: $symbol2Instrument
           |instrument2Instrument: $instrument2Instrument
         """.stripMargin
    )
}
