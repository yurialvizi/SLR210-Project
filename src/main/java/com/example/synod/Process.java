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

import akka.actor.AbstractActor;
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

enum LogMode {
    SENDING_AND_RECEIVING,
    SENDING_ONLY,
    RECEIVING_ONLY,
    NONE,
}

public class Process extends AbstractActor {
    private final LoggingAdapter logAdapter = Logging.getLogger(getContext().getSystem(), this);
    private CustomLogger log = new CustomLogger(logAdapter);

    private final static double alpha = 0.1; // probability of crashing
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

        log.setPrefix(this.toString());
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
        logAdapter.info(this + " - propose(" + v + ")");

        proposal = v;
        ballot += n;

        ackCounter = 0;
        gatherCounter = 0;

        sendToAll(new Read(ballot));
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(Launch.class, this::beforeReceive, this::onLaunch)
                .match(Membership.class, this::beforeReceive, this::onMembership)
                .match(Crash.class, this::beforeReceive, this::onCrashMessage)
                .match(Read.class, this::beforeReceive, this::onRead)
                .match(Gather.class, this::beforeReceive, this::onGather)
                .match(Impose.class, this::beforeReceive, this::onImpose)
                .match(Decide.class, this::beforeReceive, this::onDecide)
                .match(Ack.class, this::beforeReceive, this::onAck)
                .match(Abort.class, this::beforeReceive, this::onAbort)
                .match(Hold.class, this::beforeReceive, this::onHold)
                .build();
    }

    /*
     * MESSAGE HANDLERS
     */

    // This method is called before processing a message
    // it returns true if the message should be processed, false otherwise
    private boolean beforeReceive(Object message) {
        log.onReceiveMessage(message);

        if (mode == Mode.SILENT || mode == Mode.DECIDED)
            return false;

        // Decides with probability alpha if it going to crash
        if (faultProne && Math.random() < alpha) {
            logAdapter.info(this + " - CRASHED");
            mode = Mode.SILENT;
            return false;
        }

        return true;
    }

    private void onMembership(Membership message) {
        processes = message;
    }

    private void onLaunch(Launch message) {
        // propose a random value
        proposingInput = new Random().nextInt(2);
        propose(proposingInput);
    }

    private void onCrashMessage(Crash message) {
        faultProne = true;
    }

    private void onRead(Read message) {
        int incomingBallot = message.ballot;

        if (readBallot > incomingBallot || imposeBallot > incomingBallot) {
            sendToSender(new Abort(incomingBallot));
        } else {
            readBallot = incomingBallot;
            sendToSender(new Gather(incomingBallot, imposeBallot, estimate));
        }
    }

    private void onGather(Gather message) {
        int senderID = Integer.parseInt(getSender().path().name());

        if (message.ballot != ballot)
            return;

        State newState = new State(message.est, message.estBallot);
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
            sendToAll(new Impose(ballot, proposal));
        }
    }

    private void onImpose(Impose message) {
        Impose imposeMessage = message;
        if (readBallot > imposeMessage.ballot || imposeBallot > imposeMessage.ballot) {
            sendToSender(new Abort(imposeMessage.ballot));
        } else {
            estimate = imposeMessage.value;
            imposeBallot = imposeMessage.ballot;
            sendToSender(new Ack(imposeMessage.ballot));
        }
    }

    private void onDecide(Decide message) {
        int incomingValue = message.value;
        mode = Mode.DECIDED;

        sendToAll(new Decide(incomingValue));
    }

    private void onAck(Ack message) {
        int incomingBallot = message.ballot;

        if (incomingBallot != ballot)
            return;

        ackCounter++;

        if (ackCounter > n / 2) {
            ackCounter = 0;
            sendToAll(new Decide(proposal));
        }
    }

    private void onAbort(Abort message) {
        Abort abortMessage = message;

        if (abortMessage.ballot == ballot && mode == Mode.NORMAL) {
            propose(proposingInput);
        }
    }

    private void onHold(Hold message) {
        mode = Mode.ON_HOLD;
    }

    /*
     * HELPER METHODS
     */

    private void sendToAll(Object message) {
        log.onSendMessage(message);

        for (ActorRef actor : processes.references) {
            actor.tell(message, getSelf());
        }
    }

    private void sendToSender(Object message) {
        log.onSendMessage(message);

        getSender().tell(message, getSelf());
    }

    @Override
    public String toString() {
        return "Process #" + i;
    }
}

class CustomLogger {
    private final LoggingAdapter logger;
    public static LogMode logMode = LogMode.SENDING_ONLY;
    private String prefix = "";

    public CustomLogger(LoggingAdapter logger) {
        this.logger = logger;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    public void onSendMessage(Object message) {
        if (logMode == LogMode.SENDING_AND_RECEIVING || logMode == LogMode.SENDING_ONLY)
            logInfo("Sending " + message.toString());
    }

    public void onReceiveMessage(Object message) {
        if (logMode == LogMode.SENDING_AND_RECEIVING || logMode == LogMode.RECEIVING_ONLY)
            logInfo("Received " + message.toString());
    }

    private void logInfo(String message) {
        if (prefix != null && prefix.length() > 0) {
            logger.info(prefix + " " + message);
        } else {
            logger.info(message);
        }

    }
}