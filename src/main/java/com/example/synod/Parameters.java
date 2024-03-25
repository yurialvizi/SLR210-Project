package com.example.synod;

public class Parameters {
  public static String csvFile = "./results.csv";
  public int N;
  public int f;
  public int timeToElection;
  public double alpha;

  public Parameters(int N, int f, int timeToElection, double alpha) {
    this.N = N;
    this.f = f;
    this.timeToElection = timeToElection;
    this.alpha = alpha;
  }
}
