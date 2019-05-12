package home.util

import akka.event.LoggingAdapter

object LoggingAdapterImpl extends LoggingAdapter {
    def getInstance: LoggingAdapterImpl.type = this

    final override def isErrorEnabled = true
    final override def isWarningEnabled = true
    final override def isInfoEnabled = true
    final override def isDebugEnabled = true

    final protected override def notifyError(message: String): Unit = println(s"ERROR: $message")
    final protected override def notifyError(cause: Throwable, message: String): Unit = {
        println(s"ERROR: $message: ${cause.getMessage}")
    }
    final protected override def notifyWarning(message: String): Unit = println(s"WARNING: $message")
    final protected override def notifyInfo(message: String): Unit = println(s"INFO: $message")
    final protected override def notifyDebug(message: String): Unit = println(s"DEBUG: $message")
}
