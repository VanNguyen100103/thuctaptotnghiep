package com.ut.edu.backend.shipping.ghn;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * Thin wrapper around GHN's Public API v2 (sandbox by default - see
 * ghn.base-url in application.properties). Every call needs the Token +
 * ShopId headers identifying the sandbox shop created at 5sao.ghn.dev; the
 * pickup ("from") address is whatever that shop registered there, GHN infers
 * it from ShopId rather than taking it on each call.
 */
@Component
@Slf4j
public class GhnClient {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${ghn.base-url}")
    private String baseUrl;

    @Value("${ghn.api-token}")
    private String apiToken;

    @Value("${ghn.shop-id}")
    private String shopId;

    public GhnClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public boolean isConfigured() {
        return apiToken != null && !apiToken.isBlank() && shopId != null && !shopId.isBlank();
    }

    public JsonNode getProvinces() {
        return get("/shiip/public-api/master-data/province");
    }

    public JsonNode getDistricts(int provinceId) {
        return get("/shiip/public-api/master-data/district?province_id=" + provinceId);
    }

    public JsonNode getWards(int districtId) {
        return get("/shiip/public-api/master-data/ward?district_id=" + districtId);
    }

    public JsonNode createOrder(Map<String, Object> body) {
        return post("/shiip/public-api/v2/shipping-order/create", body);
    }

    public JsonNode getOrderDetail(String orderCode) {
        return post("/shiip/public-api/v2/shipping-order/detail", Map.of("order_code", orderCode));
    }

    public JsonNode calculateFee(Map<String, Object> body) {
        return post("/shiip/public-api/v2/shipping-order/fee", body);
    }

    private HttpHeaders headers() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Token", apiToken);
        headers.set("ShopId", shopId);
        return headers;
    }

    private JsonNode get(String path) {
        if (!isConfigured()) {
            throw new GhnApiException("GHN is not configured - set GHN_API_TOKEN and GHN_SHOP_ID");
        }
        try {
            String response = restTemplate
                    .exchange(baseUrl + path, HttpMethod.GET, new HttpEntity<>(headers()), String.class)
                    .getBody();
            return parse(response);
        } catch (HttpStatusCodeException e) {
            return parseErrorBody(path, e);
        } catch (RestClientException e) {
            log.error("GHN GET {} failed", path, e);
            throw new GhnApiException("GHN API call failed: " + path, e);
        }
    }

    private JsonNode post(String path, Map<String, Object> body) {
        if (!isConfigured()) {
            throw new GhnApiException("GHN is not configured - set GHN_API_TOKEN and GHN_SHOP_ID");
        }
        try {
            String response = restTemplate
                    .exchange(baseUrl + path, HttpMethod.POST, new HttpEntity<>(body, headers()), String.class)
                    .getBody();
            return parse(response);
        } catch (HttpStatusCodeException e) {
            return parseErrorBody(path, e);
        } catch (RestClientException e) {
            log.error("GHN POST {} failed: {}", path, e.getMessage());
            throw new GhnApiException("GHN API call failed: " + path, e);
        }
    }

    /** GHN returns its own error detail in the response body even on a non-2xx status (RestTemplate throws before parse() ever sees it) - surface that instead of a bare HTTP status so callers see GHN's actual reason (e.g. a missing/invalid field) rather than a generic "call failed". */
    private JsonNode parseErrorBody(String path, HttpStatusCodeException e) {
        String body = e.getResponseBodyAsString();
        log.error("GHN {} failed with {}: {}", path, e.getStatusCode(), body);
        if (body == null || body.isBlank()) {
            throw new GhnApiException("GHN API call failed: " + path, e);
        }
        return parse(body);
    }

    private JsonNode parse(String response) {
        try {
            JsonNode root = objectMapper.readTree(response);
            int code = root.path("code").asInt();
            if (code != 200) {
                String message = root.path("code_message_value").asText(root.path("message").asText("Unknown GHN error"));
                throw new GhnApiException("GHN returned code " + code + ": " + message);
            }
            return root;
        } catch (GhnApiException e) {
            throw e;
        } catch (Exception e) {
            throw new GhnApiException("Failed to parse GHN response", e);
        }
    }
}
