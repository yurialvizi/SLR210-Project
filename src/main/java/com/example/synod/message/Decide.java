package com.example.synod.message;

public class Decide {
    public int value;

    public Decide(int value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return "[DECIDE] value:" + value;
    }
}
