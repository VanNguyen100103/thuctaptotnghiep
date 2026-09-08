package com.ut.edu.backend.shipping.goship;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * Thin wrapper around Goship's API v2 (sandbox by default - see
 * goship.base-url in application.properties).
 *
 * Goship is a carrier aggregator rather than a carrier: one integration
 * fronts GHN, GHTK, Viettel Post, J&T, Ninja Van, SPX, BEST and VNPost, and
 * every route is priced by all of them at once (see #rates). That is the
 * reason this replaced the direct GHN client - the flow it enables, a
 * cashier picking a carrier by price at the counter, is not something a
 * single-carrier integration can offer.
 *
 * Authentication has two shapes and this prefers the safer one. Goship's
 * documented flow is POST /login with the account's own username and
 * password alongside the API client id/secret, which would mean keeping a
 * login password in deployment config. Their tokens last roughly ten years,
 * so a token generated once in the dashboard can be configured directly
 * instead (goship.access-token) and no password is ever stored. The login
 * flow stays as a fallback for deployments that would rather rotate.
 */
@Component
@Slf4j
public class GoshipClient {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${goship.base-url}")
    private String baseUrl;

    /** A token generated in the Goship dashboard. Preferred: it keeps the account password out of config entirely. */
    @Value("${goship.access-token:}")
    private String configuredToken;

    @Value("${goship.username:}")
    private String username;

    @Value("${goship.password:}")
    private String password;

    @Value("${goship.client-id:}")
    private String clientId;

    @Value("${goship.client-secret:}")
    private String clientSecret;

    /** Only used by the login fallback. Goship's tokens outlive any process, so this is a per-boot cache, not a refresh cycle. */
    private volatile String loginToken;

    public GoshipClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public boolean isConfigured() {
        return hasText(configuredToken) || (hasText(username) && hasText(password) && hasText(clientId) && hasText(clientSecret));
    }

    // ---- Address master data ----
    // Goship's codes are strings ("100000" for a city, "100300" for a
    // district) while ward ids are numbers. They are passed around as
    // strings throughout so the two never have to be told apart.

    public JsonNode cities() {
        return get("/cities");
    }

    public JsonNode districts(String cityId) {
        return get("/cities/" + cityId + "/districts");
    }

    public JsonNode wards(String districtId) {
        return get("/districts/" + districtId + "/wards");
    }

    /**
     * Every carrier's price for one route at once. The `id` on each row is
     * what {@link #createShipment} has to be given - a quote and the booking
     * it turns into are the same decision here, unlike GHN where a fee
     * lookup was informational only.
     */
    public JsonNode rates(Map<String, Object> body) {
        return post("/rates", body);
    }

    /**
     * Books a shipment. Goship processes this asynchronously and answers 200
     * even when the booking later fails, so a success here means "accepted",
     * not "picked up" - the webhook is what confirms it.
     */
    public JsonNode createShipment(Map<String, Object> body) {
        return post("/shipments", body);
    }

    /** Looks a shipment up by Goship id, our order id, or the carrier's own code. */
    public JsonNode searchShipment(String code) {
        return get("/shipments/search?code=" + code);
    }

    // ---- transport ----

    private String token() {
        if (hasText(configuredToken)) {
            return configuredToken;
        }
        String cached = loginToken;
        if (cached != null) {
            return cached;
        }
        synchronized (this) {
            if (loginToken == null) {
                loginToken = login();
            }
            return loginToken;
        }
    }

    private String login() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
        Map<String, Object> body = Map.of(
                "username", username,
                "password", password,
                "client_id", clientId,
                "client_secret", clientSecret);
        try {
            String response = restTemplate
                    .exchange(baseUrl + "/login", HttpMethod.POST, new HttpEntity<>(body, headers), String.class)
                    .getBody();
            String token = objectMapper.readTree(response).path("access_token").asText(null);
            if (token == null || token.isBlank()) {
                throw new GoshipApiException("Goship login returned no access_token");
            }
            log.info("Goship login succeeded");
            return token;
        } catch (GoshipApiException e) {
            throw e;
        } catch (Exception e) {
            throw new GoshipApiException("Goship login failed: " + e.getMessage(), e);
        }
    }

    private HttpHeaders headers() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
        headers.setBearerAuth(token());
        return headers;
    }

    private JsonNode get(String path) {
        return call(HttpMethod.GET, path, null, true);
    }

    private JsonNode post(String path, Map<String, Object> body) {
        return call(HttpMethod.POST, path, body, true);
    }

    /**
     * @param retryOnUnauthorized a cached login token that has been revoked
     *                            looks exactly like a configuration error
     *                            until it is thrown away and fetched again,
     *                            so a 401 is worth exactly one retry.
     */
    private JsonNode call(HttpMethod method, String path, Map<String, Object> body, boolean retryOnUnauthorized) {
        if (!isConfigured()) {
            throw new GoshipApiException(
                    "Goship is not configured - set GOSHIP_ACCESS_TOKEN, or GOSHIP_USERNAME/PASSWORD with GOSHIP_CLIENT_ID/SECRET");
        }
        try {
            String response = restTemplate
                    .exchange(baseUrl + path, method, new HttpEntity<>(body, headers()), String.class)
                    .getBody();
            return parse(response);
        } catch (HttpStatusCodeException e) {
            if (retryOnUnauthorized && e.getStatusCode() == HttpStatus.UNAUTHORIZED && !hasText(configuredToken)) {
                log.warn("Goship rejected the cached token, logging in again");
                loginToken = null;
                return call(method, path, body, false);
            }
            return parseErrorBody(path, e);
        } catch (RestClientException e) {
            log.error("Goship {} {} failed", method, path, e);
            throw new GoshipApiException("Goship API call failed: " + path + " - " + e.getMessage(), e);
        }
    }

    /** Goship puts its own reason in the body even on a non-2xx status, and RestTemplate throws before parse() would ever see it - surface that instead of a bare HTTP status. */
    private JsonNode parseErrorBody(String path, HttpStatusCodeException e) {
        String body = e.getResponseBodyAsString();
        log.error("Goship {} failed with {}: {}", path, e.getStatusCode(), body);
        if (body == null || body.isBlank()) {
            throw new GoshipApiException("Goship API call failed: " + path, e);
        }
        return parse(body);
    }

    /**
     * Most endpoints answer {@code {code, status, data}}, but the shipment
     * booking replies with the shipment object at the top level. Rather than
     * keep two parsers, this validates the envelope when there is one and
     * hands back the root either way; callers reach for "data" through
     * {@link #payload(JsonNode)}.
     */
    private JsonNode parse(String response) {
        try {
            JsonNode root = objectMapper.readTree(response);
            JsonNode code = root.path("code");
            if (code.isInt() && code.asInt() != 200) {
                String message = root.path("message").asText(root.path("status").asText("Unknown Goship error"));
                throw new GoshipApiException("Goship returned code " + code.asInt() + ": " + message);
            }
            return root;
        } catch (GoshipApiException e) {
            throw e;
        } catch (Exception e) {
            throw new GoshipApiException("Failed to parse Goship response", e);
        }
    }

    /** The useful half of a response, whether or not it came wrapped in an envelope. */
    public static JsonNode payload(JsonNode root) {
        JsonNode data = root.path("data");
        return data.isMissingNode() || data.isNull() ? root : data;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
