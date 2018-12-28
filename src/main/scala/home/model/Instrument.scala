package home.model

import home.util.StockType.StockType

/**
  * This class doesn't have all Instrument information which can be found from here:
  * {
  *   "bloomberg_unique": "EQ0035838500001000",
  *   "country": "US",
  *   "day_trade_ratio": "0.2500",
  *   "fundamentals": "https://api.robinhood.com/fundamentals/ON/",
  *   "id": "dad8fa2c-1e8d-4cb9-b354-1f0b91a4193e",
  *   "list_date": "2000-04-28",
  *   "maintenance_ratio": "0.2500",
  *   "margin_initial_ratio": "0.5000",
  *   "market": "https://api.robinhood.com/markets/XNAS/",
  *   "min_tick_size": null,
  *   "name": "ON Semiconductor Corporation Common Stock",
  *   "quote": "https://api.robinhood.com/quotes/ON/",
  *   "rhs_tradability": "tradable",
  *   "simple_name": "ON Semiconductor",
  *   "splits": "https://api.robinhood.com/instruments/dad8fa2c-1e8d-4cb9-b354-1f0b91a4193e/splits/",
  *   "state": "active",
  *   "symbol": "ON",
  *   "tradability": "tradable",
  *   "tradable_chain_id": "793179e1-be92-4e33-aa8a-26690d6faf12",
  *   "tradeable": true,
  *   "type": "stock",
  *   "url": "https://api.robinhood.com/instruments/dad8fa2c-1e8d-4cb9-b354-1f0b91a4193e/"
  * }
  */
case class Instrument(symbol: String, instrument: String, name: String, simpleName: String, stockType: StockType)
