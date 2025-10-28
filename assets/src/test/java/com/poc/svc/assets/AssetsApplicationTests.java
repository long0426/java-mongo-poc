package com.poc.svc.assets;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
        classes = AssetsApplication.class,
        properties = "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration,org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration"
)
class AssetsApplicationTests {

	@Test
	void contextLoads() {
	}
}
