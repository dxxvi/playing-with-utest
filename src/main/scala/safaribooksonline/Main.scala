package safaribooksonline

import spark.Spark

object Main {
    def main(args: Array[String]): Unit = {
//        Spark.get("/write", new WriteRoute, new JsonTransformer)
        Spark.get("/write", new WriteRoute)
    }
}
