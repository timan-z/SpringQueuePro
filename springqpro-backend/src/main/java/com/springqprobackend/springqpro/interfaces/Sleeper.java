package com.springqprobackend.springqpro.interfaces;

@FunctionalInterface
public interface Sleeper {
    void sleep(long millis) throws InterruptedException;
}
