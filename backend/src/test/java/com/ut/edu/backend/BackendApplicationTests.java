package com.ut.edu.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Integration smoke test: boots the full Spring context against a real
 * PostgreSQL + Redis (Testcontainers). Also proves the Flyway baseline
 * migration creates the schema from scratch and Hibernate validation passes.
 * Requires a running Docker daemon.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class BackendApplicationTests {

	@Container
	@ServiceConnection
	static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

	@Container
	@ServiceConnection(name = "redis")
	static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

	@Test
	void contextLoadsAndFlywayMigrates() {
		// Context startup runs Flyway (V1__baseline.sql) on the empty container DB
		// and Hibernate ddl-auto=validate confirms entities match the schema.
	}

}
