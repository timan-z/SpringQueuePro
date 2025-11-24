package com.springqprobackend.springqpro;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@ImportAutoConfiguration(exclude = {
        com.springqprobackend.springqpro.config.RedisConfig.class,
        com.springqprobackend.springqpro.config.RedisTestConfig.class
})
class SpringQueueProApplicationTests {

	@Test
	void contextLoads() {
        System.out.println("I'm testing this file right now. What's all this then?");
	}

}
