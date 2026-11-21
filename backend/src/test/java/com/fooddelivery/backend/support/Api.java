package com.fooddelivery.backend.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Api — a tiny HTTP client for tests. Talks to the REAL running app over HTTP,
 * exactly like a mobile app would.
 *
 *   Response r = api.post("/api/orders", Map.of("deliveryAddressId", id), token);
 *   assertThat(r.status()).isEqualTo(201);
 *   String orderId = r.body().get("id").asText();
 *
 * Body can be a Map / object (sent as JSON) or a String (sent as-is — handy
 * for testing broken JSON).
 */
public class Api {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final String baseUrl;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    public Api(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    /** status + parsed JSON body (empty object if there was no body). */
    public record Response(int status, JsonNode body) {
        public String text(String field) {
            return body.path(field).asText(null);
        }
    }

    public Response get(String path, String token)                 { return send("GET", path, null, token); }
    public Response get(String path)                               { return send("GET", path, null, null); }
    public Response post(String path, Object body, String token)   { return send("POST", path, body, token); }
    public Response post(String path, Object body)                 { return send("POST", path, body, null); }
    public Response put(String path, Object body, String token)    { return send("PUT", path, body, token); }
    public Response patch(String path, Object body, String token)  { return send("PATCH", path, body, token); }
    public Response patch(String path, String token)               { return send("PATCH", path, null, token); }
    public Response delete(String path, String token)              { return send("DELETE", path, null, token); }

    public Response send(String method, String path, Object body, String token) {
        try {
            String json = body == null ? null : (body instanceof String s ? s : JSON.writeValueAsString(body));
            HttpRequest.Builder req = HttpRequest.newBuilder(URI.create(baseUrl + path))
                    .timeout(Duration.ofSeconds(20))
                    .header("Content-Type", "application/json")
                    .method(method, json == null ? HttpRequest.BodyPublishers.noBody()
                                                 : HttpRequest.BodyPublishers.ofString(json));
            if (token != null) {
                req.header("Authorization", "Bearer " + token);
            }
            HttpResponse<String> res = http.send(req.build(), HttpResponse.BodyHandlers.ofString());
            JsonNode node = res.body() == null || res.body().isBlank()
                    ? JSON.createObjectNode()
                    : JSON.readTree(res.body());
            return new Response(res.statusCode(), node);
        } catch (Exception e) {
            throw new RuntimeException(method + " " + path + " failed: " + e.getMessage(), e);
        }
    }
}
