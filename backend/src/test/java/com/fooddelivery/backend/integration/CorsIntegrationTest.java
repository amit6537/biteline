package com.fooddelivery.backend.integration;

import com.fooddelivery.backend.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pretends to be a browser: sends the OPTIONS "preflight" a browser sends
 * before a cross-origin POST with JSON + Authorization header.
 */
class CorsIntegrationTest extends AbstractIntegrationTest {

    private final HttpClient http = HttpClient.newHttpClient();

    private HttpResponse<String> preflight(String origin, String path) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                .header("Origin", origin)
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "authorization,content-type")
                .build();
        return http.send(req, HttpResponse.BodyHandlers.ofString());
    }

    @Test
    @DisplayName("Our frontend's origin is allowed (even on a protected endpoint, no token needed for preflight)")
    void allowedOrigin() throws Exception {
        var res = preflight("http://localhost:5173", "/api/orders");
        assertThat(res.statusCode()).isEqualTo(200);
        assertThat(res.headers().firstValue("Access-Control-Allow-Origin")).hasValue("http://localhost:5173");
        assertThat(res.headers().firstValue("Access-Control-Allow-Headers").orElse("").toLowerCase())
                .contains("authorization");
    }

    @Test
    void unknownOriginIsRefused() throws Exception {
        var res = preflight("https://evil.example", "/api/auth/login");
        assertThat(res.statusCode()).isEqualTo(403);
        assertThat(res.headers().firstValue("Access-Control-Allow-Origin")).isEmpty();
    }

    @Test
    @DisplayName("A real cross-origin call gets the CORS header on its response")
    void actualRequestCarriesHeader() throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/actuator/health"))
                .header("Origin", "http://localhost:3000")
                .GET().build();
        var res = http.send(req, HttpResponse.BodyHandlers.ofString());
        assertThat(res.statusCode()).isEqualTo(200);
        assertThat(res.headers().firstValue("Access-Control-Allow-Origin")).hasValue("http://localhost:3000");
    }
}
