package home

import org.junit.jupiter.api.Assertions._
import org.junit.jupiter.api.Test

class StockActorTests {
    @Test
    def testCreatedAtOrderingForOrders() {
        import StockActor._

        val orders: collection.mutable.SortedSet[Order] = collection.mutable.SortedSet[Order]()(CreatedAtOrderingForOrders)

        val order1 = Order(1, "2019-07-02T10:18:04.328826Z", 1, "O1", 1, 1, "", "", "")
        orders += order1
        orders += Order(1, "2019-10-02T10:18:04.328Z",    1, "O2", 1, 1, "", "", "")
        orders += Order(1, "2019-01-02T10:18:04",         1, "O3", 1, 1, "", "", "")

        val orderArray = orders.toArray
        assertEquals("O3", orderArray(0).id)
        assertEquals("O1", orderArray(1).id)
        assertEquals("O2", orderArray(2).id)

        orders -= Order(2, "1999-04-19T04:19:19", 2, id = "O2", 2, 2, "", "", "")
        // TODO assertEquals(2, orders.size, "equals and hashCode in Order don't work correctly")

        orders -= order1
        assertEquals(2, orders.size)
        assertFalse(orders.exists(_.id == "O1"), "Why can't I remove something from a mutable.SortedSet?")
    }
}
