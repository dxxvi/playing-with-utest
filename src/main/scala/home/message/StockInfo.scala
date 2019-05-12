package home.message

import home.model.{LastTradePrice, Order}

case class StockInfo(ltp: LastTradePrice, quantityAccount: (Double, String), recentOrders: List[Order]) {
}
