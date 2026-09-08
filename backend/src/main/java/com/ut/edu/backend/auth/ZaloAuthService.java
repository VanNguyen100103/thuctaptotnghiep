package com.ut.edu.backend.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

/**
 * Zalo Login v4.
 *
 * Unlike Google, this is the redirect flow, because that is the only one Zalo
 * offers for the web: the browser goes to Zalo, the user approves, and Zalo
 * sends them back to us with a one-time code. That code is exchanged here,
 * server side, using the app's secret key.
 *
 * What comes back identifies the person only as a Zalo user id, scoped to
 * this application - no email, no phone (that needs a permission requiring
 * business verification). So this service answers "which Zalo account is
 * this", and it is up to a stored link on the user row to say who that is.
 *
 * PKCE: the code_verifier is generated here, kept in Redis under the state,
 * and sent back when the code is exchanged. It lives server-side rather than
 * in the browser because the whole exchange happens server-side - nothing
 * would be gained by round-tripping it through a page that never uses it.
 */
@Service
@Slf4j
public class ZaloAuthService {

    private static final String AUTHORIZE_URL = "https://oauth.zaloapp.com/v4/permission";
    private static final String TOKEN_URL = "https://oauth.zaloapp.com/v4/access_token";
    private static final String PROFILE_URL = "https://graph.zalo.me/v2.0/me";

    /** Long enough for someone to find their phone and approve, short enough that an abandoned attempt does not linger. */
    private static final int PENDING_LOGIN_TTL_MINUTES = 10;
    private static final String PKCE_KEY_PREFIX = "zalo:pkce:";

    @Value("${zalo.app-id:}")
    private String appId;

    @Value("${zalo.secret-key:}")
    private String secretKey;

    /** Must match a Callback URL registered on the Zalo app, exactly. */
    @Value("${zalo.redirect-uri:}")
    private String redirectUri;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SecureRandom random = new SecureRandom();

    @Autowired(required = false)
    private RedisTemplate<String, Object> redisTemplate;

    public ZaloAuthService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public boolean isConfigured() {
        return hasText(appId) && hasText(secretKey) && hasText(redirectUri);
    }

    /**
     * Where to send the browser, plus the state that ties the eventual
     * callback back to this attempt.
     */
    public String buildAuthorizeUrl() {
        requireConfigured();
        if (redisTemplate == null) {
            throw new ZaloAuthException("Đăng nhập Zalo cần Redis để giữ phiên đăng nhập tạm");
        }

        String state = randomUrlSafe(24);
        String codeVerifier = randomUrlSafe(48);
        redisTemplate.opsForValue()
                .set(PKCE_KEY_PREFIX + state, codeVerifier, PENDING_LOGIN_TTL_MINUTES, TimeUnit.MINUTES);

        return UriComponentsBuilder.fromHttpUrl(AUTHORIZE_URL)
                .queryParam("app_id", appId)
                .queryParam("redirect_uri", redirectUri)
                .queryParam("code_challenge", codeChallenge(codeVerifier))
                .queryParam("state", state)
                .build()
                // encode() matters: redirect_uri carries "://" and slashes, and
                // build().toUriString() leaves them raw in the query string.
                // Zalo answers -14003 "Invalid redirect uri" to that.
                .encode()
                .toUriString();
    }

    /**
     * Turns the code Zalo sent back into the Zalo user id behind it.
     *
     * The state is consumed here, not just read: a code that has already been
     * exchanged must not be exchangeable again.
     *
     * @return Zalo's id for the person who just approved
     */
    public String exchangeCodeForZaloUserId(String code, String state) {
        requireConfigured();
        String codeVerifier = consumeVerifier(state);
        String accessToken = requestAccessToken(code, codeVerifier);
        return fetchZaloUserId(accessToken);
    }

    private String consumeVerifier(String state) {
        if (redisTemplate == null) {
            throw new ZaloAuthException("Đăng nhập Zalo cần Redis để giữ phiên đăng nhập tạm");
        }
        Object verifier = redisTemplate.opsForValue().getAndDelete(PKCE_KEY_PREFIX + state);
        if (verifier == null) {
            // Expired, already used, or a state this server never issued -
            // all of which mean the same thing to the caller.
            throw new ZaloAuthException("Phiên đăng nhập Zalo đã hết hạn, vui lòng thử lại");
        }
        return verifier.toString();
    }

    private String requestAccessToken(String code, String codeVerifier) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        // Zalo takes the app secret as a header rather than a body field.
        headers.set("secret_key", secretKey);

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("code", code);
        form.add("app_id", appId);
        form.add("grant_type", "authorization_code");
        form.add("code_verifier", codeVerifier);

        try {
            String body = restTemplate
                    .exchange(TOKEN_URL, HttpMethod.POST, new HttpEntity<>(form, headers), String.class)
                    .getBody();
            JsonNode json = objectMapper.readTree(body);
            String accessToken = json.path("access_token").asText(null);
            if (accessToken == null || accessToken.isBlank()) {
                log.warn("Zalo refused the code exchange: {}", body);
                throw new ZaloAuthException("Zalo từ chối mã đăng nhập");
            }
            return accessToken;
        } catch (ZaloAuthException e) {
            throw e;
        } catch (Exception e) {
            log.error("Zalo token exchange failed", e);
            throw new ZaloAuthException("Không đổi được mã đăng nhập với Zalo", e);
        }
    }

    private String fetchZaloUserId(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        // Zalo reads the token from its own header, not Authorization.
        headers.set("access_token", accessToken);

        String url = UriComponentsBuilder.fromHttpUrl(PROFILE_URL)
                .queryParam("fields", "id,name")
                .build()
                .toUriString();

        try {
            String body = restTemplate
                    .exchange(url, HttpMethod.GET, new HttpEntity<>(headers), String.class)
                    .getBody();
            JsonNode json = objectMapper.readTree(body);
            String id = json.path("id").asText(null);
            if (id == null || id.isBlank()) {
                log.warn("Zalo profile carried no id: {}", body);
                throw new ZaloAuthException("Zalo không trả về định danh người dùng");
            }
            return id;
        } catch (ZaloAuthException e) {
            throw e;
        } catch (Exception e) {
            log.error("Zalo profile lookup failed", e);
            throw new ZaloAuthException("Không lấy được thông tin tài khoản Zalo", e);
        }
    }

    private void requireConfigured() {
        if (!isConfigured()) {
            throw new ZaloAuthException(
                    "Đăng nhập Zalo chưa được cấu hình - đặt ZALO_APP_ID, ZALO_SECRET_KEY và ZALO_REDIRECT_URI");
        }
    }

    /**
     * PKCE S256 as the OAuth spec defines it: base64url, unpadded, of the
     * SHA-256 of the verifier. Zalo's docs name the parameter but not the
     * transform; this is the standard one, and the only place this
     * integration is guessing.
     */
    private static String codeChallenge(String codeVerifier) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(codeVerifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (Exception e) {
            throw new ZaloAuthException("Không tạo được code_challenge", e);
        }
    }

    private String randomUrlSafe(int bytes) {
        byte[] buffer = new byte[bytes];
        random.nextBytes(buffer);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buffer);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
