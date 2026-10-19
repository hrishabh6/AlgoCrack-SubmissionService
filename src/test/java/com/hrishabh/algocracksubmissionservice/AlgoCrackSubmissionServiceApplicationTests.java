package com.hrishabh.algocracksubmissionservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;

@SpringBootTest
class AlgoCrackSubmissionServiceApplicationTests {

    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("algocrack_submission_context")
            .withUsername("test")
            .withPassword("test");

    static {
        MYSQL.start();
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("SPRING_DATASOURCE_URL", MYSQL::getJdbcUrl);
        registry.add("SPRING_DATASOURCE_USERNAME", MYSQL::getUsername);
        registry.add("SPRING_DATASOURCE_PASSWORD", MYSQL::getPassword);
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @Test
    void contextLoads() {
    }
}
