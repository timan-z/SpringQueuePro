package com.springqprobackend.springqpro.util;

@FunctionalInterface
public interface Sleeper {
    void sleep(long millis) throws InterruptedException;
}
