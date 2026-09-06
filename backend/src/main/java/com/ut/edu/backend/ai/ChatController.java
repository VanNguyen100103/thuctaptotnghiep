package com.ut.edu.backend.ai;

import com.ut.edu.backend.store.TenantContext;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Public storefront AI chat - unauthenticated, one endpoint per store.
 * TenantResolverFilter resolves {slug} into TenantContext before this runs
 * (same as StorefrontController), so ChatToolExecutor's tenant-scoped reads
 * work without any manual slug plumbing. Rate-limited separately from the
 * generic default bucket - see RateLimitingConfig#resolveChatBucket.
 */
@RestController
@RequestMapping("/stores/{slug}")
@RequiredArgsConstructor
@Slf4j
public class ChatController {

    private final StoreChatService storeChatService;

    @PostMapping("/chat")
    public ResponseEntity<?> chat(@PathVariable String slug, @Valid @RequestBody ChatRequest request) {
        if (!TenantContext.hasStore()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Store not found: " + slug));
        }
        ChatResponse response = storeChatService.chat(slug, request.sessionId(), request.message());
        return ResponseEntity.ok(response);
    }
}
