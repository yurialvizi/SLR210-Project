package com.example.synod;

import java.io.FileWriter;
import java.io.IOException;

import com.example.synod.message.Decide;
import com.example.synod.message.Launch;

import akka.actor.AbstractActor;
import akka.actor.Props;

public class WriterProcess extends AbstractActor {
  private Parameters parameters;
  private long startedAt;
  private boolean decided = false;

  public WriterProcess(Parameters parameters) {
    this.parameters = parameters;
  }

  public static Props createActor(Parameters parameters) {
    return Props.create(WriterProcess.class, () -> new WriterProcess(parameters));
  }

  @Override
  public Receive createReceive() {
    return receiveBuilder()
        .match(Launch.class, this::onLaunchMessage)
        .match(Decide.class, this::onDecideMessage)
        .matchAny(this::unhandled)
        .build();
  }

  public void unhandled(Object message) {
  }

  private void onLaunchMessage(Launch message) {
    startedAt = System.nanoTime();
  }

  private void onDecideMessage(Decide message) {
    if (decided)
      return;

    decided = true;
    long timeSpent = (System.nanoTime() - startedAt);
    writeResultToCSV(timeSpent);
  }

  public void writeResultToCSV(long timeSpent) {
    try {
      FileWriter writer = new FileWriter(Parameters.csvFile, true);
      writer
          .append(parameters.N + "," + parameters.f + "," + parameters.timeToElection + "," + parameters.alpha + ","
              + timeSpent + "\n");
      writer.close();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  public static void clearCSVFile() {
    try {
      FileWriter writer = new FileWriter(Parameters.csvFile, false);
      writer.append("N,f,timeToElection,alpha,timeSpentInNanoSeconds\n");
      writer.close();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

}
