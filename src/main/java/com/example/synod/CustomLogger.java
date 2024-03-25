package com.example.synod;

import com.example.synod.message.Decide;
import akka.event.LoggingAdapter;

class CustomLogger {
  private final LoggingAdapter logger;
  private String prefix = "";

  public boolean logIncomingMessages = false;
  public boolean logOutgoingMessages = false;
  public boolean logConsensus = true;
  public boolean logCrashes = false;

  public CustomLogger(LoggingAdapter logger) {
    this.logger = logger;
  }

  public void setPrefix(String prefix) {
    this.prefix = prefix;
  }

  public void onCrash() {
    if (logCrashes)
      logInfo("Crashed");
  }

  public void onSendMessage(Object message) {
    if (logOutgoingMessages)
      logInfo("Sending " + message.toString());
  }

  public void onReceiveMessage(Object message) {
    boolean isDecide = message instanceof Decide;

    if (logIncomingMessages || (isDecide && logConsensus))
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