package com.example.synod.message;

public class Read {
    public int ballot;

    public Read(int ballot) {
        this.ballot = ballot;
    }

    @Override
    public String toString() {
        return "[READ] ballot:" + ballot;
    }
}
