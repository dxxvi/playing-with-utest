package home

import home.model.Order
import home.util.OrderOrdering
import org.junit.jupiter.api.Assertions._
import org.junit.jupiter.api.Test

import scala.collection.mutable

class StockActorUtilTests extends StockActorUtil {
    @Test // TODO needs to finish this test
    def testUpdate(): Unit = {
        val standardizedOrders: mutable.TreeSet[Order] = mutable.TreeSet.empty[Order](OrderOrdering)

        var order = Order("2019-04-19T02:03:03Z", "o1", "...", "...", 1, "confirmed", 0, 1, "...")
        assertTrue(update(order, standardizedOrders))
        assertEquals(1, standardizedOrders.size)

        standardizedOrders.clear()
        order = Order("2019-04-19T02:03:04Z", "o1", "...", "...", 1.1, "cancelled", 0, 1, "...")
        assertFalse(update(order, standardizedOrders))
        assertTrue(standardizedOrders.isEmpty)

        standardizedOrders.clear()
        standardizedOrders add Order("2019-04-19T02:03:03Z", "o1", "...", "...", 1.1, "confirmed", 0, 1, "...")
        order = Order("2019-04-19T02:03:03Z", "o1", "...", "...", 1.1, "cancelled", 0, 1, "...")
        assertTrue(update(order, standardizedOrders))
        assertTrue(standardizedOrders.isEmpty)

        standardizedOrders.clear()
        standardizedOrders add Order("2019-04-19T02:03:03Z", "o1", "...", "...", 1.1, "confirmed", 0, 1, "...")
        order = Order("2019-04-19T02:03:03Z", "o1", "...", "...", 1.1, "confirmed", 0, 1, "...")
        assertFalse(update(order, standardizedOrders))

        standardizedOrders.clear()
        standardizedOrders add Order("2019-04-19T02:03:03Z", "o1", "...", "...", 1.1, "unconfirmed", 0, 1, "...")
        order = Order("2019-04-19T02:03:03Z", "o1", "...", "...", 1.1, "confirmed", 0, 1, "...")
        assertTrue(update(order, standardizedOrders))
        assertEquals(1, standardizedOrders.size)
        assertEquals("confirmed", standardizedOrders.head.state)

        standardizedOrders.clear()
        standardizedOrders add Order("2019-04-19T02:03:03Z", "o1", "...", "...", 1.1, "partial_filled", 2, 5, "...")
        order = Order("2019-04-19T02:03:03Z", "o1", "...", "...", 1.1, "partial_filled", 2, 5, "...")
        assertFalse(update(order, standardizedOrders))
        assertEquals(1, standardizedOrders.size)

        standardizedOrders.clear()
        standardizedOrders add Order("2019-04-19T02:03:03Z", "o1", "...", "...", 1.1, "partial_filled", 2, 5, "...")
        order = Order("2019-04-19T02:03:03Z", "o1", "...", "...", 1.1, "partial_filled", 3, 5, "...")
        assertTrue(update(order, standardizedOrders))
        assertEquals(1, standardizedOrders.size)
        assertEquals(3, standardizedOrders.head.cumulativeQuantity)

        standardizedOrders.clear()
        standardizedOrders add Order("2019-04-19T02:03:03Z", "o1", "...", "...", 1.1, "partial_filled", 2, 5, "...")
        order = Order("2019-04-19T02:03:03Z", "o1", "...", "...", 1.1, "filled", 0, 5, "...")
        assertTrue(update(order, standardizedOrders))
        assertEquals(1, standardizedOrders.size)
        assertEquals("filled", standardizedOrders.head.state)

    }
}
