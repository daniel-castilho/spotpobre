package com.spotpobre.backend;

import io.restassured.RestAssured;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;

/**
 * Production exposure lockdown proof (spec S21): with the {@code prod} profile active,
 * Swagger/API docs are unavailable, the actuator lives only on the internal management
 * port with health-only, detail-free responses, and the business listener serves no
 * operational endpoints.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.profiles.active=prod",
                // Prod contract values not supplied by AbstractIntegrationTest:
                "aws.credentials.source=static",
                "aws.s3.bucket-name=spotpobre-songs",
                "jwt.secret=test-secret-for-exposure-it-0123456789abcdef",
                "rate-limit.key-secret=exposure-it-rate-limit-secret",
                "email.from-address=no-reply@spotpobre.local",
                "email.ses-endpoint=http://localhost:4566",
                // RANDOM ports for both listeners inside the test JVM:
                "management.server.port=0"
        })
class ProductionExposureFlowIT extends AbstractIntegrationTest {

    @LocalServerPort
    private int businessPort;

    @LocalManagementPort
    private int managementPort;

    @BeforeEach
    void setUp() {
        RestAssured.baseURI = "http://localhost";
    }

    @Test
    void swaggerUiAndApiDocs_areUnavailableInProd() {
        given().port(businessPort)
                .when().get("/swagger-ui.html")
                .then().statusCode(org.hamcrest.Matchers.anyOf(equalTo(404), equalTo(401)));
        given().port(businessPort)
                .when().get("/swagger-ui/index.html")
                .then().statusCode(org.hamcrest.Matchers.anyOf(equalTo(404), equalTo(401)));
        given().port(businessPort)
                .when().get("/v3/api-docs")
                .then().statusCode(org.hamcrest.Matchers.anyOf(equalTo(404), equalTo(401)));
    }

    @Test
    void actuator_isNotServedByTheBusinessListener() {
        given().port(businessPort)
                .when().get("/actuator/health")
                .then().statusCode(org.hamcrest.Matchers.anyOf(equalTo(404), equalTo(401)));
    }

    @Test
    void managementPort_servesHealthOnly_withoutDetails() {
        given().port(managementPort)
                .when().get("/actuator/health/liveness")
                .then()
                .statusCode(200)
                .body("status", equalTo("UP"))
                .body(not(containsString("components")));

        given().port(managementPort)
                .when().get("/actuator/health/readiness")
                .then()
                .statusCode(200)
                .body("status", equalTo("UP"))
                .body(not(containsString("dynamoDb")));

        // show-details: never -> even the aggregate health exposes no component detail.
        String body = given().port(managementPort)
                .when().get("/actuator/health")
                .then()
                .statusCode(200)
                .body("status", equalTo("UP"))
                .extract().asString();
        org.junit.jupiter.api.Assertions.assertFalse(
                body.contains("dynamoDb") || body.contains("redis") || body.contains("s3"),
                "prod health must not leak component details: " + body);

        // Health-only exposure: other actuator endpoints are absent (or auth-gated - both
        // keep them private; the security chain answers 401 before the 404 would surface).
        given().port(managementPort)
                .when().get("/actuator/metrics")
                .then().statusCode(org.hamcrest.Matchers.anyOf(equalTo(401), equalTo(404)));
        given().port(managementPort)
                .when().get("/actuator/env")
                .then().statusCode(org.hamcrest.Matchers.anyOf(equalTo(401), equalTo(404)));
    }
}
