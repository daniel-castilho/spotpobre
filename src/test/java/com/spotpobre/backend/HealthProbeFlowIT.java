package com.spotpobre.backend;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * S7 — automated probe failure and recovery verification (runtime-deployment epic).
 *
 * <p>Boots a dedicated application context whose S3 bucket does not exist, then proves the
 * S6 health model end to end: readiness DOWN (503) while a critical dependency is missing,
 * liveness still UP, non-probe actuator endpoints still authenticated, and full recovery to
 * readiness UP once the bucket is created. Runs against Testcontainers LocalStack via
 * {@link AbstractIntegrationTest}.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"aws.s3.bucket-name=spotpobre-missing-bucket-it"}
)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class HealthProbeFlowIT extends AbstractIntegrationTest {

    private static final String MISSING_BUCKET = "spotpobre-missing-bucket-it";

    @LocalServerPort
    private int port;

    @Autowired
    private S3Client s3Client;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
    }

    @Test
    @Order(1)
    void missingBucket_readinessDown_livenessUp_nonProbeActuatorUnauthorized() {
        assertFalse(bucketExists());

        // Critical dependency (S3) unavailable -> readiness must be DOWN (503)...
        given()
                .when().get("/actuator/health/readiness")
                .then().statusCode(503);

        // ...while the process itself is alive...
        given()
                .when().get("/actuator/health/liveness")
                .then().statusCode(200);

        // ...and non-probe actuator endpoints remain authenticated (401 for anonymous).
        given()
                .when().get("/actuator/metrics")
                .then().statusCode(401);
    }

    @Test
    @Order(2)
    void dependencyRestored_readinessRecoversToUp() {
        if (!bucketExists()) {
            s3Client.createBucket(CreateBucketRequest.builder().bucket(MISSING_BUCKET).build());
        }

        given()
                .when().get("/actuator/health/readiness")
                .then().statusCode(200);

        given()
                .when().get("/actuator/health/liveness")
                .then().statusCode(200);
    }

    private boolean bucketExists() {
        try {
            s3Client.headBucket(HeadBucketRequest.builder().bucket(MISSING_BUCKET).build());
            return true;
        } catch (NoSuchBucketException e) {
            return false;
        }
    }
}
