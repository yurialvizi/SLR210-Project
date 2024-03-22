package com.example.synod;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.example.synod.message.Abort;
import com.example.synod.message.Ack;
import com.example.synod.message.Crash;
import com.example.synod.message.Decide;
import com.example.synod.message.Gather;
import com.example.synod.message.Hold;
import com.example.synod.message.Impose;
import com.example.synod.message.Launch;
import com.example.synod.message.Membership;
import com.example.synod.message.Read;

import akka.actor.Actor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.UntypedAbstractActor;
import akka.event.Logging;
import akka.event.LoggingAdapter;

// Enum of all the possible states
enum Mode {
    NORMAL,
    SILENT,
    ON_HOLD,
    DECIDED,
}

public class Process extends UntypedAbstractActor {
    private final static double alpha = 0.1; // probability of crashing

    private final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);// Logger attached to actor

    private Mode mode; // current state of the process

    private int n; // number of processes
    private int i; // id of current process
    private Membership processes; // other processes' references
    private Integer proposal;
    private int ballot;
    private int readBallot;
    private int imposeBallot;
    private Integer estimate;
    private List<State> states;

    private int gatherCounter;
    private int ackCounter;

    private int proposingInput;
    private boolean faultProne;

    public Process(int n, int i) {
        this.n = n;
        this.i = i;
        ballot = i - n;
        proposal = null;
        readBallot = 0;
        imposeBallot = i - n;
        estimate = null;
        states = new ArrayList<State>();
        for (int j = 0; j < n; j++) {
            states.add(new State(null, 0));
        }

        gatherCounter = 0;
        mode = Mode.NORMAL;
        faultProne = false;
    }

    public static Props createActor(int n, int i) {
        return Props.create(Actor.class, () -> {
            return new Process(n, i);
        });
    }

    private void clearStatesList() {
        for (int j = 0; j < n; j++) {
            states.set(j, new State(null, 0));
        }
    }

    private void propose(int v) {
        log.info(this + " - propose(" + v + ")");

        proposal = v;
        ballot += n;

        ackCounter = 0;
        gatherCounter = 0;

        log.info(this + " - Sending Read - ballot: " + ballot);
        for (ActorRef actor : processes.references) {
            actor.tell(new Read(ballot), getSelf());
        }
    }

    public void onReceive(Object message) throws Throwable {
        if (mode == Mode.SILENT || mode == Mode.DECIDED)
            return;

        if (faultProne && Math.random() < alpha) {
            // Decides with probability alpha if it going to crash
            log.info(this + " - CRASHED");
            mode = Mode.SILENT;
            return;
        }

        if (message instanceof Membership) {
            log.info(this + " - membership received");
            Membership m = (Membership) message;
            processes = m;
        } else if (message instanceof Launch) {
            log.info(this + " - launch received");
            // propose a random value
            proposingInput = new Random().nextInt(2);
            propose(proposingInput);

        } else if (message instanceof Crash) {
            log.info(this + " - crash received");
            faultProne = true;
        } else if (message instanceof Read) {
            int incomingBallot = ((Read) message).ballot;
            log.info(this + " - read received ballot: " + incomingBallot);

            if (readBallot > incomingBallot || imposeBallot > incomingBallot) {
                log.info(this + " - Sending Abort - ballot: " + incomingBallot);
                getSender().tell(new Abort(incomingBallot), getSelf());
            } else {
                readBallot = incomingBallot;
                log.info(this + " - Sending Gather - incomingBallot: " + incomingBallot + " imposeBallot: "
                        + imposeBallot + " estimate: " + estimate);
                getSender().tell(new Gather(incomingBallot, imposeBallot, estimate), getSelf());
            }
        } else if (message instanceof Gather) {
            int senderID = Integer.parseInt(getSender().path().name());
            Gather gatherMessage = (Gather) message;

            log.info(this + " - gather received from " + senderID + " with est: " + gatherMessage.est
                    + " and estBallot: " + gatherMessage.estBallot + " ballot: " + gatherMessage.ballot);

            if (gatherMessage.ballot != ballot)
                return;

            State newState = new State(gatherMessage.est, gatherMessage.estBallot);
            states.set(senderID, newState);
            gatherCounter++;

            if (gatherCounter > n / 2) {
                gatherCounter = 0;
                State highestState = new State(null, 0);
                for (State state : states) {
                    if (state.estBallot > highestState.estBallot) {
                        highestState = new State(state.est, state.estBallot);
                    }
                }
                if (highestState.estBallot > 0) {
                    proposal = highestState.est;
                }
                clearStatesList();
                log.info(this + " - Sending Impose - ballot: " + ballot + " proposal: " + proposal);
                for (ActorRef actor : processes.references) {
                    actor.tell(new Impose(ballot, proposal), getSelf());
                }
            }
        } else if (message instanceof Impose) {
            log.info(this + " - impose received with ballot: " + ((Impose) message).ballot + " and value: "
                    + ((Impose) message).value);
            Impose imposeMessage = (Impose) message;
            if (readBallot > imposeMessage.ballot || imposeBallot > imposeMessage.ballot) {
                log.info(this + " - Sending Abort - ballot: " + imposeMessage.ballot);
                getSender().tell(new Abort(imposeMessage.ballot), getSelf());
            } else {
                estimate = imposeMessage.value;
                imposeBallot = imposeMessage.ballot;

                log.info(this + " - Sending Ack - ballot: " + imposeMessage.ballot);
                getSender().tell(new Ack(imposeMessage.ballot), getSelf());
            }
        } else if (message instanceof Decide) {
            int incomingValue = ((Decide) message).value;
            mode = Mode.DECIDED;
            log.info(this + " - received Decide  with value:" + incomingValue);

            for (ActorRef actor : processes.references) {
                actor.tell(new Decide(incomingValue), getSelf());
            }
        } else if (message instanceof Ack) {
            int incomingBallot = ((Ack) message).ballot;

            if (incomingBallot != ballot)
                return;

            ackCounter++;

            log.info(this + " - ack received for " + incomingBallot + " ballot");

            if (ackCounter > n / 2) {
                ackCounter = 0;

                log.info(this + " - Sending Decide - proposal: " + proposal);
                for (ActorRef actor : processes.references) {
                    actor.tell(new Decide(proposal), getSelf());
                }
            }
        } else if (message instanceof Abort) {
            log.info(this + " - abort received");
            Abort abortMessage = (Abort) message;

            if (abortMessage.ballot == ballot && mode == Mode.NORMAL) {
                propose(proposingInput);
            }
        } else if (message instanceof Hold) {
            log.info(this + " - hold received");
            mode = Mode.ON_HOLD;
        } else {
            unhandled(message);
        }
    }

    @Override
    public String toString() {
        return "Process #" + i;
    }

}
