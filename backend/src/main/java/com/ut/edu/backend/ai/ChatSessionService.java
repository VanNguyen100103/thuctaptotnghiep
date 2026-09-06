package com.ut.edu.backend.ai;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Redis-backed chat history, keyed per store+session. Same manual
 * RedisTemplate style as RedisProductCacheService/RedisUserSessionService -
 * a Redis outage degrades a conversation to "no memory this turn" rather
 * than a hard failure.
 */
@Service
@Slf4j
public class ChatSessionService {

    private static final String KEY_PREFIX = "chat:session:";

    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${ai.chat.session-ttl-minutes:30}")
    private long ttlMinutes;

    public ChatSessionService(@Lazy RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public List<AiChatMessage> loadHistory(Long storeId, String sessionId) {
        try {
            Object cached = redisTemplate.opsForValue().get(key(storeId, sessionId));
            if (cached instanceof List<?> list) {
                List<AiChatMessage> messages = new ArrayList<>();
                for (Object o : list) {
                    if (o instanceof AiChatMessage m) {
                        messages.add(m);
                    }
                }
                return messages;
            }
        } catch (Exception e) {
            log.warn("Failed to load chat session (non-critical): {}", e.getMessage());
        }
        return List.of();
    }

    public void saveHistory(Long storeId, String sessionId, List<AiChatMessage> history) {
        try {
            redisTemplate.opsForValue().set(key(storeId, sessionId), history, ttlMinutes, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.warn("Failed to save chat session (non-critical): {}", e.getMessage());
        }
    }

    private String key(Long storeId, String sessionId) {
        return KEY_PREFIX + storeId + ":" + sessionId;
    }
}
