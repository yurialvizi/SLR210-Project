package com.example.synod.message;

public class Impose {
    public int ballot;
    public int value;

    public Impose(int ballot, int value) {
        this.ballot = ballot;
        this.value = value;
    }

    @Override
    public String toString() {
        return "[IMPOSE] ballot:" + ballot + " value:" + value;
    }
}
