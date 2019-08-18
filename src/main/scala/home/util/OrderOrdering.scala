package home.util

import home.AppUtil
import home.model.Order

object OrderOrdering extends Ordering[Order] with AppUtil {
    override def compare(a: Order, b: Order): Int = {
        val x = -compareCreatedAts(a.createdAt, b.createdAt)
        if (x != 0) x
        else a.id compare b.id
    }
}