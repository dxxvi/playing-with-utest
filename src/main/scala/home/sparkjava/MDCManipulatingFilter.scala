package home.sparkjava

import ch.qos.logback.classic.filter.ThresholdFilter
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.spi.FilterReply

/**
  * This is the Logback's Threshold filter but is hi-jacked to change the sourceThread in the MDC
  */
class MDCManipulatingFilter extends ThresholdFilter {
    override def decide(event: ILoggingEvent): FilterReply = {
        val mdc: java.util.Map[String, String] = event.getMDCPropertyMap
        if (!(mdc eq null) && !mdc.isEmpty && mdc.containsKey("sourceThread")) {
            val sourceThread = mdc.get("sourceThread")
            val i = sourceThread.lastIndexOf('-', sourceThread.length - 5)
            if (i > -1) mdc.put("sourceThread", String.format("%-13s", sourceThread.substring(i + 1)))
        }
        super.decide(event)
    }
}