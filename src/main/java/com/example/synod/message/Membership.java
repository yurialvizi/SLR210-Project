package com.example.synod.message;

import akka.actor.ActorRef;
import java.util.List;

public class Membership {

    public List<ActorRef> references;

    public Membership(List<ActorRef> references) {
        this.references = references;
    }

    @Override
    public String toString() {
        return "[MEMBERSHIP]";
    }

}
