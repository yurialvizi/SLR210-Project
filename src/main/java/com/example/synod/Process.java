package com.example.synod;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.UntypedAbstractActor;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import com.example.synod.message.Launch;
import com.example.synod.message.Membership;

import java.lang.reflect.Array;
import java.util.Random;
import java.util.ArrayList;
import java.util.List;

public class Process extends UntypedAbstractActor {
    private final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);// Logger attached to actor

    private int n;//number of processes
    private int i;//id of current process
    private Membership processes;//other processes' references
    private Boolean proposal;
    private int ballot;
    private int readBallot;
    private int imposeBallot;
    private int estimate;
    // List of states

    public Process(int n, int i) {
        this.n = n;
        this.i = i;
        this.ballot = i - n;
        this.readBallot = 0;
        this.imposeBallot = i - n;
    }

    private void propose(Boolean v) {
        log.info(this + " - propose("+ v+")");
        proposal = v;
        ballot += n;
    }

    public void onReceive(Object message) throws Throwable {
        if (message instanceof Membership) {
            log.info(this + " - membership received");
            Membership m = (Membership) message;
            processes = m;
        } else if (message instanceof Launch) {
            log.info(this + " - launch received");
            propose(true);
        }
    }

    @Override
    public String toString() {
        return "Process #" + i;
    }

}
