package home.util

import home.model.Order
import org.junit.jupiter.api.{Assertions, Test}

class OrderOrderingTests {
    @Test
    def testWithTreeSet(): Unit = {
        val o1a = Order("2019-03-01T00:00:00Z", "oa", "", "", 0, "", 0, 0, "")
        val o1b = Order("2019-03-01T00:00:00Z", "oz", "", "", 0, "", 0, 0, "")
        val o1c = Order("2019-03-01T00:00:00Z", "om", "", "", 0, "", 0, 0, "")
        val o2  = Order("2019-03-05T00:00:00Z", "o2", "", "", 0, "", 0, 0, "")
        val o3  = Order("2019-03-11T00:00:00Z", "o3", "", "", 0, "", 0, 0, "")
        val tree = collection.mutable.TreeSet(o1a, o1b, o1c, o2, o3)(OrderOrdering)

        Assertions.assertEquals(o3.createdAt, tree.head.createdAt)
        Assertions.assertEquals(o2.createdAt, tree.tail.head.createdAt)
        Assertions.assertTrue(o1a.createdAt == tree.tail.tail.head.createdAt && o1a.id == tree.tail.tail.head.id)
        Assertions.assertTrue(o1c.createdAt == tree.tail.tail.tail.head.createdAt && o1c.id == tree.tail.tail.tail.head.id)

        val o = Order("2019-03-01T00:00:00Z", "om", "", "", 0, "", 0, 0, "")
        Assertions.assertTrue(tree(o))

        // remove an element in the tree
        tree -= o
        Assertions.assertEquals(4, tree.size)
        Assertions.assertEquals(o3.createdAt, tree.head.createdAt)
        Assertions.assertEquals(o2.createdAt, tree.tail.head.createdAt)
        Assertions.assertTrue(o1a.createdAt == tree.tail.tail.head.createdAt && o1a.id == tree.tail.tail.head.id)
        Assertions.assertTrue(o1b.createdAt == tree.tail.tail.tail.head.createdAt && o1b.id == tree.tail.tail.tail.head.id)

        // remove an element not in the tree
        tree -= Order("2019-04-19T00:00:00Z", "o", "", "", 0, "", 0, 0, "")
        Assertions.assertEquals(4, tree.size)
        Assertions.assertEquals(o3.createdAt, tree.head.createdAt)
        Assertions.assertEquals(o2.createdAt, tree.tail.head.createdAt)
        Assertions.assertTrue(o1a.createdAt == tree.tail.tail.head.createdAt && o1a.id == tree.tail.tail.head.id)
        Assertions.assertTrue(o1b.createdAt == tree.tail.tail.tail.head.createdAt && o1b.id == tree.tail.tail.tail.head.id)
    }
}
