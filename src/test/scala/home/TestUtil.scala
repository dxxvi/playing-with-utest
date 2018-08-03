package home

import java.io.File
import java.nio.file.{Files, Paths}
import java.util.stream.Collectors

trait TestUtil {
    def readTextFileFromTestResource(parts: String*): String = {
        val fileAbsolutePath = (Seq("src", "test", "resources") ++ parts).mkString(
            new File(".").getAbsolutePath + File.separator,
            File.separator,
            ""
        )
        Files.lines(Paths.get(fileAbsolutePath))
                .filter(!_.trim.startsWith("//"))
                .collect(Collectors.joining("\n"))
    }
}

