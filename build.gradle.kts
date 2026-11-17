plugins {
	java
	id("org.springframework.boot") version "4.1.1"
	id("io.spring.dependency-management") version "1.1.7"
}

group = "com.aura"
version = "0.0.1-SNAPSHOT"
description = "Aura Social Service — friends, friend requests, follows and blocks"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(21)
	}
}

repositories {
	mavenCentral()
}

extra["springCloudVersion"] = "2025.1.3"

dependencies {
	// Web + API
	implementation("org.springframework.boot:spring-boot-starter-webmvc")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.springframework.boot:spring-boot-starter-restclient")

	// Every endpoint is user-scoped: the Supabase access token is verified here against the
	// project's JWKS (ES256) — the gateway only forwards it.
	implementation("org.springframework.boot:spring-boot-starter-security-oauth2-resource-server")

	// Persistence (JdbcClient, like the other services)
	implementation("org.springframework.boot:spring-boot-starter-data-jdbc")
	implementation("org.springframework.boot:spring-boot-starter-flyway")
	implementation("org.flywaydb:flyway-database-postgresql")
	runtimeOnly("org.postgresql:postgresql")


	// Observability
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	runtimeOnly("io.micrometer:micrometer-registry-prometheus")

	annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

dependencyManagement {
	imports {
		mavenBom("org.springframework.cloud:spring-cloud-dependencies:${property("springCloudVersion")}")
	}
}

tasks.withType<Test> {
	useJUnitPlatform()
}
