package com.springqprobackend.springqpro.util;

import org.springframework.stereotype.Component;

/* NOTE: So, from what I understand, this file -- a concrete implementation of the Sleeper interface -- is
needed because Spring **needs** to inject something concrete at runtime to satisfy the Sleeper interface.
I mainly added the Sleeper interface and this implementation here to use in my Handler Unit Tests, so I could
adjust the sleep times to remove them entirely for Unit Testing (scalability and decoupling is obviously also just improved design).
-- When Spring sees, say, EmailHandler depends on a Sleeper, it'll dependency inject RealSleeper into it!
*/
@Component
public class RealSleeper implements Sleeper {
    @Override
    public void sleep(long millis) throws InterruptedException {
        Thread.sleep(millis);
    }
}
