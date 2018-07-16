package spark.serialization

import java.io.OutputStream

import org.apache.logging.log4j.scala.Logger


class SerializerChain() {
    private val logger: Logger = Logger(classOf[home.sparkjava.WebSocketListener])

    private val defaultSerializer = new DefaultSerializer

    private val inputStreamSerializer = new InputStreamSerializer
    inputStreamSerializer setNext defaultSerializer

    private val root = new BytesSerializer
    root setNext inputStreamSerializer

    def process(outputStream: OutputStream, element: Any): Unit = {
        logger.debug("Using my SerializerChain")
        root.processElement(outputStream, element)
    }
}
