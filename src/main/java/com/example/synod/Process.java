package com.example.synod;

import akka.actor.Actor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.UntypedAbstractActor;
import akka.event.Logging;
import akka.event.LoggingAdapter;

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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

// Enum of all the possible states
enum Mode {
    NORMAL,
    SILENT,
    ON_HOLD,
    FINISHED,
}

public class Process extends UntypedAbstractActor {
    private final static double alpha = 0.1; // probability of crashing

    private final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);// Logger attached to actor

    private Mode mode; // current state of the process

    private int n; // number of processes
    private int i; // id of current process
    private Membership processes; // other processes' references
    private int proposal;
    private int ballot;
    private int readBallot;
    private int imposeBallot;
    private int estimate;
    private List<State> states;
    private Map<Integer, Integer> ackCounter = new HashMap<Integer, Integer>();

    private int chosenValue;
    private boolean faultProne;

    public Process(int n, int i) {
        this.n = n;
        this.i = i;
        this.ballot = i - n;
        this.readBallot = 0;
        this.imposeBallot = i - n;
        this.estimate = -1;
        this.mode = Mode.NORMAL;
        this.faultProne = false;
        this.states = new ArrayList<State>();
        for (int j = 0; j < n; j++) {
            states.add(new State(-1, 0));
        }
    }

    public static Props createActor(int n, int i) {
        return Props.create(Actor.class, () -> {
            return new Process(n, i);
        });
    }

    private void clearStates() {
        for (int j = 0; j < n; j++) {
            states.set(j, new State(-1, 0));
        }
    }

    private boolean checkMajority() {
        int count = 0;
        for (State state : states) {
            if (state.estBallot != 0) {
                count++;
            }
        }
        return count > n / 2;
    }

    private void reinitialize() {
        proposal = 0;
        ballot = i - n;
        readBallot = 0;
        imposeBallot = i - n;
        estimate = -1;
        clearStates();
        ackCounter.clear();
    }

    private void propose(int v) {
        log.info(this + " - propose(" + v + ")");
        reinitialize();
        proposal = v;
        ballot += n;
        clearStates();
        ackCounter.clear();
        for (ActorRef actor : processes.references) {
            actor.tell(new Read(ballot), getSelf());
        }
    }

    public void onReceive(Object message) throws Throwable {
        if (mode == Mode.SILENT) {
            log.info(this + " - SILENT");
            return;
        }

        if (faultProne) {
            log.info(this + " - FAULT_PRONE");
            // Decides with probability alpha if it going to crash
            if (Math.random() < alpha) {
                log.info(this + " - CRASHED");
                mode = Mode.SILENT;
                return;
            }
        }

        if (message instanceof Membership) {
            log.info(this + " - membership received");
            Membership m = (Membership) message;
            processes = m;
        } else if (message instanceof Launch) {
            log.info(this + " - launch received");
            // propose a random value
            chosenValue = new Random().nextInt(2);
            propose(chosenValue);

        } else if (message instanceof Crash) {
            log.info(this + " - crash received");
            faultProne = true;
        } else if (message instanceof Read) {
            int incomingBallot = ((Read) message).ballot;
            log.info(this + " - read received for " + incomingBallot + " ballot");
            if (readBallot > incomingBallot || imposeBallot > incomingBallot) {
                getSender().tell(new Abort(incomingBallot), getSelf());
            } else {
                readBallot = incomingBallot;
                getSender().tell(new Gather(incomingBallot, imposeBallot, estimate), getSelf());
            }
        } else if (message instanceof Gather) {
            int senderID = Integer.parseInt(getSender().path().name());
            Gather gatherMessage = (Gather) message;
            log.info(this + " - gather received from " + senderID + " with " + gatherMessage.est + " value");
            State newState = new State(gatherMessage.est, gatherMessage.estBallot);
            states.set(senderID, newState);
            if (checkMajority()) {
                int highestBallot = -1;
                int highestState = -1;
                for (State state : states) {
                    if (state.estBallot > imposeBallot) {
                        highestBallot = state.estBallot;
                        highestState = state.est;
                    }
                }
                if (highestBallot > 0) {
                    proposal = highestState;
                }
                log.info("ballot: " + ballot + " proposal: " + proposal);
                for (ActorRef actor : processes.references) {
                    actor.tell(new Impose(ballot, proposal), getSelf());
                }
                clearStates();
            }
        } else if (message instanceof Impose) {
            log.info(this + " - impose received");
            Impose imposeMessage = (Impose) message;
            if (readBallot > imposeMessage.ballot || imposeBallot > imposeMessage.ballot) {
                getSender().tell(new Abort(imposeMessage.ballot), getSelf());
            } else {
                estimate = imposeMessage.value;
                imposeBallot = imposeMessage.ballot;
                getSender().tell(new Ack(imposeMessage.ballot), getSelf());
            }
        } else if (message instanceof Decide) {
            if (mode == Mode.FINISHED) return;

            int incomingValue = ((Decide) message).value;
            for (ActorRef actor : processes.references) {
                actor.tell(new Decide(incomingValue), getSelf());
            }
            mode = Mode.FINISHED;
            log.info(this + " - decided on " + proposal);
        } else if (message instanceof Ack) {
            int incomingBallot = ((Ack) message).ballot;
            log.info(this + " - ack received for " + incomingBallot + " ballot");
            if (ackCounter.containsKey(incomingBallot)) {
                ackCounter.put(incomingBallot, ackCounter.get(incomingBallot) + 1);
            } else {
                ackCounter.put(incomingBallot, 1);
            }

            if (ackCounter.get(incomingBallot) > n / 2) {
                for (ActorRef actor : processes.references) {
                    actor.tell(new Decide(proposal), getSelf());
                }
            }
        } else if (message instanceof Abort) {
            log.info(this + " - abort received");
            if (mode != Mode.ON_HOLD) {
                propose(chosenValue);
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
