package home;

import akka.actor.AbstractActor;

public class FirstActor extends AbstractActor {
    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(String.class, s -> System.out.printf("First Actor: %s\n", s))
                .build();
    }
}
