package home;

import com.google.gson.Gson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Spark;

public class Main {
    private static final Logger log = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        Gson gson = new Gson();

        Spark.get("/api/a", (req, res) -> {
            log.info("Request query name: {}, request query 'nick name': {}",
                    req.queryParams("name"), req.queryParams("nick name"));
            res.type("application/json");
            return gson.toJson(retrieve());
        });
    }

    public static A retrieve() {
        return new A(1, "a name")
                .add(new B(21L, "b 21"))
                .add(new B(22L, "b 22"))
                .add(new B(23L, "b 23"))
                ;
    }
}