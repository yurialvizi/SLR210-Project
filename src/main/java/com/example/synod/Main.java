package com.example.synod;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import com.example.synod.message.Crash;
import com.example.synod.message.Hold;
import com.example.synod.message.Launch;
import com.example.synod.message.Membership;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;

public class Main {
    public static final int N = 10;
    public static final int holdTime = 50; // in milliseconds

    public static void main(String[] args) throws InterruptedException {
        // Instantiate an actor system
        final ActorSystem system = ActorSystem.create("system");
        system.log().info("System started with N=" + N);

        List<ActorRef> processes = new ArrayList<>();

        // Create N actors
        for (int i = 0; i < N; i++) {
            final ActorRef actor = system.actorOf(Process.createActor(N, i), Integer.toString(i));
            processes.add(actor);
        }

        // Give each process a view of all the other processes
        Membership membership = new Membership(processes);
        for (ActorRef actor : processes) {
            actor.tell(membership, ActorRef.noSender());
        }

        // Launch the processes
        for (ActorRef actor : processes) {
            actor.tell(new Launch(), ActorRef.noSender());
        }

        // Choose f random processes to crash
        Random rand = new Random();
        int f = N % 2 == 0 ? rand.nextInt(N / 2) : rand.nextInt((N + 1) / 2);

        Collections.shuffle(processes);
        List<ActorRef> faultyProcesses = processes.subList(0, f);
        List<ActorRef> nonFaultyProcesses = processes.subList(f, N);

        // Crash the chosen processes
        for (ActorRef actor : faultyProcesses) {
        system.log().info("Crashing " + actor);
        actor.tell(new Crash(), ActorRef.noSender());
        }

        // Hold the system for a while
        Thread.sleep(holdTime);
        system.log().info("Time to election");

        ActorRef chosenProcess = nonFaultyProcesses.get(0); // NonFaultyProcesses is shuffled, so we can choose the
                                                            // first one
        system.log().info("Chosen process: " + chosenProcess);

        // Hold all processes
        for (ActorRef process : processes) {
            if (process.equals(chosenProcess)) { // TODO: Check
                system.log().info("Skipping " + process);
                continue;
            }
            system.log().info("Holding " + process);
            process.tell(new Hold(), ActorRef.noSender());
        }

        try {
            system.log().info("Waiting for 60 seconds before terminating the system");
            waitBeforeTerminate();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            system.terminate();
        }
    }

    public static void waitBeforeTerminate() throws InterruptedException {
        Thread.sleep(2000);
    }
}
