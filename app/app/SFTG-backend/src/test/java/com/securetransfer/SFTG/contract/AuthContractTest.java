package com.securetransfer.SFTG.contract;

import au.com.dius.pact.consumer.MockServer;
import au.com.dius.pact.consumer.dsl.PactDslJsonBody;
import au.com.dius.pact.consumer.dsl.PactDslWithProvider;
import au.com.dius.pact.consumer.junit5.PactConsumerTestExt;
import au.com.dius.pact.consumer.junit5.PactTestFor;
import au.com.dius.pact.core.model.PactSpecVersion;
import au.com.dius.pact.core.model.RequestResponsePact;
import au.com.dius.pact.core.model.annotations.Pact;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Consumer-driven contract tests. The consumer here is the SFTG React
 * frontend; these tests pin the exact request/response shape it relies on.
 * Generated pact files land in target/pacts/ for provider verification.
 */
@ExtendWith(PactConsumerTestExt.class)
@PactTestFor(providerName = "SFTG-backend", pactVersion = PactSpecVersion.V3, port = "0")
class AuthContractTest {

    private final HttpClient client = HttpClient.newHttpClient();

    @Pact(consumer = "SFTG-frontend")
    public RequestResponsePact registerContract(PactDslWithProvider builder) {
        return builder
                .given("backend is accepting registrations")
                .uponReceiving("a valid registration request")
                .path("/api/auth/register")
                .method("POST")
                .headers("Content-Type", "application/json")
                .body(new PactDslJsonBody()
                        .stringValue("email", "user@example.com")
                        .stringValue("password", "Password1!"))
                .willRespondWith()
                .status(200)
                .body("OTP sent to email")
                .toPact();
    }

    @Test
    @PactTestFor(pactMethod = "registerContract")
    void registerReturnsConfirmation(MockServer server) throws Exception {
        HttpResponse<String> response = post(server, "/api/auth/register",
                "{\"email\":\"user@example.com\",\"password\":\"Password1!\"}");
        assertEquals(200, response.statusCode());
        assertEquals("OTP sent to email", response.body());
    }

    @Pact(consumer = "SFTG-frontend")
    public RequestResponsePact verifyOtpContract(PactDslWithProvider builder) {
        return builder
                .given("a pending OTP exists for the email")
                .uponReceiving("a valid OTP verification request")
                .path("/api/auth/verify-otp")
                .method("POST")
                .headers("Content-Type", "application/json")
                .body(new PactDslJsonBody()
                        .stringValue("email", "user@example.com")
                        .stringValue("otp", "123456"))
                .willRespondWith()
                .status(200)
                .body("Email verified successfully")
                .toPact();
    }

    @Test
    @PactTestFor(pactMethod = "verifyOtpContract")
    void verifyOtpReturnsConfirmation(MockServer server) throws Exception {
        HttpResponse<String> response = post(server, "/api/auth/verify-otp",
                "{\"email\":\"user@example.com\",\"otp\":\"123456\"}");
        assertEquals(200, response.statusCode());
        assertEquals("Email verified successfully", response.body());
    }

    @Pact(consumer = "SFTG-frontend")
    public RequestResponsePact loginContract(PactDslWithProvider builder) {
        return builder
                .given("a verified user exists")
                .uponReceiving("a valid login request")
                .path("/api/auth/login")
                .method("POST")
                .headers("Content-Type", "application/json")
                .body(new PactDslJsonBody()
                        .stringValue("email", "user@example.com")
                        .stringValue("password", "Password1!"))
                .willRespondWith()
                .status(200)
                .headers(java.util.Map.of("Content-Type", "application/json"))
                .body(new PactDslJsonBody().stringType("jwtToken", "eyJhbGciOiJIUzI1NiJ9.payload.sig"))
                .toPact();
    }

    @Test
    @PactTestFor(pactMethod = "loginContract")
    void loginReturnsJwtToken(MockServer server) throws Exception {
        HttpResponse<String> response = post(server, "/api/auth/login",
                "{\"email\":\"user@example.com\",\"password\":\"Password1!\"}");
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("jwtToken"));
    }

    @Pact(consumer = "SFTG-frontend")
    public RequestResponsePact healthContract(PactDslWithProvider builder) {
        return builder
                .given("service is healthy")
                .uponReceiving("a health probe")
                .path("/api/stats/health")
                .method("GET")
                .willRespondWith()
                .status(200)
                .body("OK")
                .toPact();
    }

    @Test
    @PactTestFor(pactMethod = "healthContract")
    void healthProbeReturnsOk(MockServer server) throws Exception {
        HttpResponse<String> response = client.send(
                HttpRequest.newBuilder(URI.create(server.getUrl() + "/api/stats/health")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        assertEquals("OK", response.body());
    }

    @Pact(consumer = "SFTG-frontend")
    public RequestResponsePact myFilesContract(PactDslWithProvider builder) {
        return builder
                .given("an authenticated user with no files")
                .uponReceiving("a request for the user's file list")
                .path("/api/files/my-files")
                .method("GET")
                .headers("Authorization", "Bearer test-token")
                .willRespondWith()
                .status(200)
                .headers(java.util.Map.of("Content-Type", "application/json"))
                .body(new PactDslJsonBody().array("files"))
                .toPact();
    }

    @Test
    @PactTestFor(pactMethod = "myFilesContract")
    void myFilesReturnsList(MockServer server) throws Exception {
        HttpResponse<String> response = client.send(
                HttpRequest.newBuilder(URI.create(server.getUrl() + "/api/files/my-files"))
                        .header("Authorization", "Bearer test-token")
                        .GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
    }

    private HttpResponse<String> post(MockServer server, String path, String json) throws Exception {
        return client.send(
                HttpRequest.newBuilder(URI.create(server.getUrl() + path))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(json))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }
}
