package com.ut.edu.backend.ai;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Homepage AI consultant - unauthenticated and, unlike ChatController,
 * deliberately outside /stores/** so TenantResolverFilter never runs and no
 * store is in scope. Shares ChatController's rate-limit bucket (see
 * RateLimitingFilter#selectBucket) because it costs the same real LLM calls.
 */
@RestController
@RequestMapping("/assistant")
@RequiredArgsConstructor
@Slf4j
public class PlatformChatController {

    private final PlatformChatService platformChatService;

    @PostMapping("/chat")
    public ResponseEntity<ChatResponse> chat(@Valid @RequestBody ChatRequest request) {
        return ResponseEntity.ok(platformChatService.chat(request.sessionId(), request.message()));
    }
}
