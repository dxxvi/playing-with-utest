package home

import home.model.Order

import scala.collection.mutable

trait StockActorUtil extends OrderUtil {
    /**
      * @param o used to add/modify/remove an order in the given orders
      * @param orders ordered by OrderOrdering
      * @return true if orders is changed, otherwise false
      */
    def update(o: Order, orders: mutable.TreeSet[Order]): Boolean = {
        val so = standardizeOrder(o)
        val existingOrderOption: Option[Order] = orders.find(_o => _o.createdAt == so.createdAt && _o.id == so.id)
        if (existingOrderOption.isEmpty) {
            if (so.state != "cancelled") {
                orders += so
                true
            }
            else false
        }
        else {
            val _o = existingOrderOption.get
            so.state match {
                case "cancelled" =>
                    orders -= so
                    true
                case s if s == "filled" || s == "queued" || s.contains("confirmed") /* unconfirmed */ =>
                    if (_o.state == so.state) false
                    else {
                        _o.state = so.state
                        _o.quantity = so.quantity
                        _o.cumulativeQuantity = so.cumulativeQuantity
                        true
                    }
                case s: String if s contains "partial" =>
                    if (_o.state == so.state) {
                        val changed = _o.cumulativeQuantity != so.cumulativeQuantity
                        _o.quantity = so.quantity
                        _o.cumulativeQuantity = so.cumulativeQuantity
                        changed
                    }
                    else {
                        _o.state = so.state
                        _o.quantity = so.quantity
                        _o.cumulativeQuantity = so.cumulativeQuantity
                        true
                    }
            }
        }
    }
}
