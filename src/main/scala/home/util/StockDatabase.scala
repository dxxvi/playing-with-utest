package home.util

object StockDatabase {
    import home.model.Instrument

    private val symbol2Instrument: collection.mutable.Map[String, Instrument] =
        collection.mutable.Map[String, Instrument]()
    private val instrument2Instrument: collection.mutable.Map[String, Instrument] =
        collection.mutable.Map[String, Instrument]()

    def addDowStock(symbol: String, instrument: String, name: String, simpleName: String) {
        val i = Instrument(symbol, instrument, name, simpleName, StockType.DOW)
        symbol2Instrument + (symbol -> i)
        instrument2Instrument + (instrument -> i)
    }

    def addRegularStock(symbol: String, instrument: String, name: String, simpleName: String) {
        val i = Instrument(symbol, instrument, name, simpleName, StockType.REGULAR)
        symbol2Instrument + (symbol -> i)
        instrument2Instrument + (instrument -> i)
    }

    def getInstrumentFromSymbol(symbol: String): Option[Instrument] = symbol2Instrument.get(symbol)

    def getInstrumentFromInstrument(instrument: String): Option[Instrument] = instrument2Instrument.get(instrument)
}
