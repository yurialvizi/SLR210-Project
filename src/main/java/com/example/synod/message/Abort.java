package com.example.synod.message;

public class Abort {
    public int ballot;

    public Abort(int ballot) {
        this.ballot = ballot;
    }

    @Override
    public String toString() {
        return "[ABORT] ballot:" + ballot;
    }
}
