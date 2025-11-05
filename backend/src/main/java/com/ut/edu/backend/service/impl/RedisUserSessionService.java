package com.ut.edu.backend.service.impl;

import com.ut.edu.backend.model.User;
import com.ut.edu.backend.model.UserSession;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Redis User Session Service
 * Manages user sessions in Redis for fast access
 */
@Service
@Slf4j
public class RedisUserSessionService {

    private final RedisTemplate<String, Object> redisTemplate;

    public RedisUserSessionService(@org.springframework.context.annotation.Lazy RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    private static final String SESSION_KEY_PREFIX = "user:session:";
    private static final String USER_SESSIONS_KEY_PREFIX = "user:sessions:";
    private static final long SESSION_TIMEOUT_HOURS = 24; // 24 hours

    /**
     * Save user session to Redis when user logs in
     */
    public void saveUserSession(User user, String accessToken, String refreshToken,
                                String ipAddress, String userAgent, String deviceId) {

        String sessionKey = SESSION_KEY_PREFIX + user.getId();

        UserSession session = UserSession.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .roles(user.getRoles().stream()
                        .map(role -> role.name())
                        .collect(Collectors.toSet()))
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .loginTime(LocalDateTime.now())
                .lastActivityTime(LocalDateTime.now())
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .deviceId(deviceId)
                .enabled(user.getEnabled())
                .accountNonLocked(user.getAccountNonLocked())
                .build();

        // Save to Redis with 24 hour expiration
        redisTemplate.opsForValue().set(sessionKey, session, SESSION_TIMEOUT_HOURS, TimeUnit.HOURS);

        log.info("✓ User session saved to Redis: userId={}, username={}, expires in {} hours",
                user.getId(), user.getUsername(), SESSION_TIMEOUT_HOURS);

        // Track all active sessions for this user (for multi-device login tracking)
        String userSessionsKey = USER_SESSIONS_KEY_PREFIX + user.getId();
        redisTemplate.opsForSet().add(userSessionsKey, deviceId);
        redisTemplate.expire(userSessionsKey, SESSION_TIMEOUT_HOURS, TimeUnit.HOURS);
    }

    /**
     * Get user session from Redis
     */
    public UserSession getUserSession(Long userId) {
        String sessionKey = SESSION_KEY_PREFIX + userId;
        Object sessionObj = redisTemplate.opsForValue().get(sessionKey);

        if (sessionObj instanceof UserSession session) {
            // Update last activity time
            session.setLastActivityTime(LocalDateTime.now());
            redisTemplate.opsForValue().set(sessionKey, session, SESSION_TIMEOUT_HOURS, TimeUnit.HOURS);
            log.debug("User session retrieved from Redis: userId={}", userId);
            return session;
        } else {
            log.debug("No session found in Redis for userId={}", userId);
            return null;
        }
    }

    /**
     * Get user session by username
     */
    public UserSession getUserSessionByUsername(String username) {
        // This is less efficient - would need to scan Redis keys
        // Better to use userId when possible
        log.warn("getUserSessionByUsername is not efficient - use getUserSession(userId) instead");
        return null;
    }

    /**
     * Update user session activity time
     */
    public void updateSessionActivity(Long userId) {
        UserSession session = getUserSession(userId);
        if (session != null) {
            session.setLastActivityTime(LocalDateTime.now());
            String sessionKey = SESSION_KEY_PREFIX + userId;
            redisTemplate.opsForValue().set(sessionKey, session, SESSION_TIMEOUT_HOURS, TimeUnit.HOURS);
            log.debug("Session activity updated for userId={}", userId);
        }
    }

    /**
     * Delete user session (logout)
     */
    public void deleteUserSession(Long userId, String deviceId) {
        String sessionKey = SESSION_KEY_PREFIX + userId;
        redisTemplate.delete(sessionKey);

        // Remove device from active sessions
        String userSessionsKey = USER_SESSIONS_KEY_PREFIX + userId;
        redisTemplate.opsForSet().remove(userSessionsKey, deviceId);

        log.info("✓ User session deleted from Redis: userId={}, deviceId={}", userId, deviceId);
    }

    /**
     * Check if user has active session
     */
    public boolean hasActiveSession(Long userId) {
        String sessionKey = SESSION_KEY_PREFIX + userId;
        return Boolean.TRUE.equals(redisTemplate.hasKey(sessionKey));
    }

    /**
     * Get all active devices for a user
     */
    public Set<String> getActiveDevices(Long userId) {
        String userSessionsKey = USER_SESSIONS_KEY_PREFIX + userId;
        Set<Object> devices = redisTemplate.opsForSet().members(userSessionsKey);

        if (devices != null) {
            return devices.stream()
                    .map(Object::toString)
                    .collect(Collectors.toSet());
        }

        return Set.of();
    }

    /**
     * Invalidate all sessions for a user (security: password changed, account locked, etc.)
     */
    public void invalidateAllUserSessions(Long userId) {
        String sessionKey = SESSION_KEY_PREFIX + userId;
        String userSessionsKey = USER_SESSIONS_KEY_PREFIX + userId;

        redisTemplate.delete(sessionKey);
        redisTemplate.delete(userSessionsKey);

        log.info("✓ All sessions invalidated for userId={}", userId);
    }
}
