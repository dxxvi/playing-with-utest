package home.akka;

import akka.actor.ActorSystem;
import akka.http.javadsl.Http;
import akka.http.javadsl.model.HttpHeader;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.model.Query;
import akka.http.javadsl.model.Uri;
import akka.japi.Pair;
import akka.japi.function.Procedure;
import akka.stream.ActorMaterializer;
import akka.stream.javadsl.Sink;
import akka.util.ByteString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;

public class AkkaPlayground {
    private final Logger logger = LoggerFactory.getLogger(AkkaPlayground.class);

    public void httpRequest() throws ExecutionException, InterruptedException {
        var httpRequest = HttpRequest.create().withUri(
                Uri
                        .create("https://api.robinhood.com/quotes/")
                        .query(Query.create(new Pair<>("symbols", "AMD,ON")))
        ).addHeader(HttpHeader.parse("Accept", "application/json"));

        var actorSystem = ActorSystem.create();
        var materializer = ActorMaterializer.create(actorSystem);
        Http.get(actorSystem).singleRequest(httpRequest)
                .thenAccept(httpResponse -> {
                    logger.debug("Status code: {}", httpResponse.status());
                    httpResponse.entity().getDataBytes().runForeach(byteString -> {

                    }, materializer);
                })
                .thenRun(actorSystem::terminate);

    }

    public void stream() {
    }
}
