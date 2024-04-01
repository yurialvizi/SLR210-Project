package com.example.synod;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.example.synod.message.Crash;
import com.example.synod.message.Hold;
import com.example.synod.message.Launch;
import com.example.synod.message.Membership;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;

public class Main {
    public static void main(String[] args) throws InterruptedException {
        int[] N = new int[] { 3, 10, 15, 20, 25, 30, 40, 50, 60, 70, 80, 90, 100 };
        double[] timeToElection = new double[] { 0.01, 0.1, 0.5, 1, 1.5, 2 };
        double[] alpha = new double[] { 0, 0.1, 1 };
        int repeatTimes = 5;

        WriterProcess.clearCSVFile();

        for (int i = 0; i < N.length; i++)
            for (double a : alpha)
                for (double t : timeToElection)
                    for (int nTry = 0; nTry < repeatTimes; nTry++) {
                        Parameters parameters = new Parameters(N[i], (int) Math.ceil(N[i] / 2) - 1, (int) (t * 1000),
                                a);
                        runSimulation(parameters);
                    }
    }

    public static void runSimulation(Parameters p) throws InterruptedException {
        // Instantiate an actor system
        final ActorSystem system = ActorSystem.create("system");

        system.log().info("System started with parameters:");
        system.log().info(p.toString());

        Process.setAlpha(p.alpha);
        List<ActorRef> processes = new ArrayList<>();

        // Create N actors
        for (int i = 1; i <= p.N; i++) {
            final ActorRef actor = system.actorOf(Process.createActor(p.N, i), Integer.toString(i));
            processes.add(actor);
        }

        // Add process to write results to CSV
        List<ActorRef> processesWithWriter = new ArrayList<>();
        final ActorRef writer = system.actorOf(WriterProcess.createActor(p), "writer");
        processesWithWriter.add(writer);
        processesWithWriter.addAll(processes);

        // Give each process a view of all the other processes
        Membership membership = new Membership(processesWithWriter);
        for (ActorRef actor : processes) {
            actor.tell(membership, ActorRef.noSender());
        }

        system.log().info("Starting consensus");

        long startedAt = System.nanoTime();

        // Launch the processes
        for (ActorRef actor : processesWithWriter) {
            actor.tell(new Launch(startedAt), ActorRef.noSender());
        }

        Collections.shuffle(processes);
        List<ActorRef> faultyProcesses = processes.subList(0, p.f);
        List<ActorRef> nonFaultyProcesses = processes.subList(p.f, p.N);

        // Crash the chosen processes
        for (ActorRef actor : faultyProcesses) {
            actor.tell(new Crash(), ActorRef.noSender());
        }

        // Hold the system for a while
        system.log().info("Time to hold");
        Thread.sleep(p.timeToElection);

        // NonFaultyProcesses is shuffled, so we can choose the first one
        ActorRef chosenProcess = nonFaultyProcesses.get(0);

        system.log().info("Chosen process: " + chosenProcess);

        // Hold other processes
        for (ActorRef process : processes) {
            if (process.equals(chosenProcess))
                continue;
            process.tell(new Hold(), ActorRef.noSender());
        }

        try {
            system.log().info("Waiting for 0,1 seconds before terminating the system");
            waitBeforeTerminate();
            system.log().info("Exiting");
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            system.terminate();
        }
    }

    public static void waitBeforeTerminate() throws InterruptedException {
        Thread.sleep(100);
    }
}
