package com.example.synod.message;

public class Launch {
  public long timestamp;

  public Launch(long timestamp) {
    this.timestamp = timestamp;
  }

  @Override
  public String toString() {
    return "[LAUNCH]";
  }
}
