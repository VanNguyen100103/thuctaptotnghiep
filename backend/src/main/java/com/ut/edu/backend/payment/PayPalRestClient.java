package com.ut.edu.backend.payment;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * Low-level REST client for PayPal APIs the legacy {@code rest-api-sdk}
 * doesn't cover (Subscriptions, Catalog Products, webhook verification).
 * Fetches a fresh OAuth token per call - call volume here is low
 * (subscribe/cancel/webhook-verify), so token caching isn't worth the
 * complexity yet.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PayPalRestClient {

    private final RestTemplate restTemplate;

    @Value("${paypal.client.id}")
    private String clientId;

    @Value("${paypal.client.secret}")
    private String clientSecret;

    @Value("${paypal.mode}")
    private String mode;

    private String baseUrl() {
        return "live".equalsIgnoreCase(mode)
                ? "https://api-m.paypal.com"
                : "https://api-m.sandbox.paypal.com";
    }

    private String getAccessToken() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setBasicAuth(clientId, clientSecret);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "client_credentials");

        try {
            Map<String, Object> response = restTemplate.postForObject(
                    baseUrl() + "/v1/oauth2/token", new HttpEntity<>(body, headers), Map.class);
            Object token = response != null ? response.get("access_token") : null;
            if (token == null) {
                throw new PayPalApiException("PayPal OAuth response had no access_token");
            }
            return token.toString();
        } catch (RestClientException e) {
            throw new PayPalApiException("Failed to obtain PayPal OAuth token", e);
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> post(String path, Object requestBody) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(getAccessToken());

        try {
            Map<String, Object> response = restTemplate.postForObject(
                    baseUrl() + path, new HttpEntity<>(requestBody, headers), Map.class);
            return response != null ? response : Map.of();
        } catch (RestClientException e) {
            throw new PayPalApiException("PayPal API call failed: POST " + path, e);
        }
    }

    /** For calls whose success response has no body (e.g. subscription cancel -> 204). */
    public void postNoContent(String path, Object requestBody) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(getAccessToken());

        try {
            restTemplate.postForEntity(baseUrl() + path, new HttpEntity<>(requestBody, headers), Void.class);
        } catch (RestClientException e) {
            throw new PayPalApiException("PayPal API call failed: POST " + path, e);
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> get(String path) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(getAccessToken());

        try {
            var response = restTemplate.exchange(
                    baseUrl() + path, HttpMethod.GET, new HttpEntity<>(headers), Map.class);
            return response.getBody() != null ? response.getBody() : Map.of();
        } catch (RestClientException e) {
            throw new PayPalApiException("PayPal API call failed: GET " + path, e);
        }
    }
}
