package home;

import akka.NotUsed;
import akka.actor.ActorSystem;
import akka.http.javadsl.ConnectHttp;
import akka.http.javadsl.Http;
import akka.http.javadsl.ServerBinding;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.server.AllDirectives;
import akka.http.javadsl.server.Route;
import akka.stream.ActorMaterializer;
import akka.stream.javadsl.Flow;

import java.io.IOException;
import java.util.concurrent.CompletionStage;

public class MyServer extends AllDirectives {
    public static void main(String[] args) throws IOException {
        ActorSystem system = ActorSystem.create("test-system");
        ActorMaterializer materializer = ActorMaterializer.create(system);
        MyServer server = new MyServer();
        Flow<HttpRequest, HttpResponse, NotUsed> routeFlow = server.createRoute().flow(system, materializer);
        CompletionStage<ServerBinding> binding = Http.get(system)
                .bindAndHandle(routeFlow, ConnectHttp.toHost("localhost", 8282), materializer);
        System.out.print("Press Enter to stop: ");
        System.in.read();
        binding.thenCompose(ServerBinding::unbind).thenAccept(done -> system.terminate());
    }

    private Route createRoute() {
        return route(
                path("hello", () ->
                        get(() -> complete("Hello World!")))
        );
    }
}
