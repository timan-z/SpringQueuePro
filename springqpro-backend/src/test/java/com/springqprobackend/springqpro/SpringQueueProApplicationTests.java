package com.springqprobackend.springqpro;

import com.springqprobackend.springqpro.testcontainers.IntegrationTestBase;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@Disabled("Context smoke test not needed in CI")
class SpringQueueProApplicationTests extends IntegrationTestBase {

	@Test
	void contextLoads() {
        System.out.println("I'm testing this file right now. What's all this then?");
	}

}
