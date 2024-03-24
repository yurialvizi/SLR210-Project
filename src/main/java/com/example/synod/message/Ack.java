package com.example.synod.message;

public class Ack {
    public int ballot;

    public Ack(int ballot) {
        this.ballot = ballot;
    }

    @Override
    public String toString() {
        return "[ACK] ballot:" + ballot;
    }
}
