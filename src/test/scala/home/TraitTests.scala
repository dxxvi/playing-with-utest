package home

import utest._

/**
  * These exercises are from the Scala Design Patterns book.
  */
object TraitTests extends TestSuite { val tests = Tests {
    "Test" - {

    }
}}

trait Alarm {
    def trigger(): String
}

trait Notifier {
    val notificationMessage: String

    def printNotification(): Unit = println(notificationMessage)

    def clear()
}

class NotifierImpl(val notificationMessage: String) extends Notifier {
    override def clear(): Unit = println("Clear")
}