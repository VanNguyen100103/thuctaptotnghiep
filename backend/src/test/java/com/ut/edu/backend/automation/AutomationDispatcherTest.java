package com.ut.edu.backend.automation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AutomationDispatcherTest {

    private static final String URL = "http://n8n.test/webhook/tryum-events";

    @Mock private AutomationOutbox outbox;
    @Mock private AutomationSignatureService signatureService;
    @Mock private RestTemplate restTemplate;

    private AutomationDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        dispatcher = new AutomationDispatcher(outbox, signatureService, restTemplate);
        ReflectionTestUtils.setField(dispatcher, "enabled", true);
        ReflectionTestUtils.setField(dispatcher, "webhookUrl", URL);
        ReflectionTestUtils.setField(dispatcher, "batchSize", 25);
        when(signatureService.isConfigured()).thenReturn(true);
        when(signatureService.sign(anyString(), anyString())).thenReturn("sha256=deadbeef");
    }

    private AutomationEvent event(long id) {
        return AutomationEvent.builder()
                .id(id)
                .storeId(42L)
                .eventId(UUID.fromString("00000000-0000-0000-0000-00000000000" + id))
                .eventKey(AutomationEvents.SALE_COMPLETED)
                .payload("{\"event\":\"sale.completed\"}")
                .status(AutomationEventStatus.SENDING)
                .attempts(0)
                .build();
    }

    @Test
    void dispatchOnce_marksDeliveredWhenN8nAccepts() {
        when(outbox.claimDue(25)).thenReturn(List.of(event(1)));
        when(restTemplate.exchange(eq(URL), eq(HttpMethod.POST), any(), eq(String.class)))
                .thenReturn(ResponseEntity.ok("ok"));

        assertThat(dispatcher.dispatchOnce()).isEqualTo(1);
        verify(outbox).markDelivered(1L);
        verify(outbox, never()).markFailed(any(), anyString());
    }

    /**
     * The body must go out byte for byte as it was stored, and the headers
     * must carry the delivery id unchanged - that pair is what the receiving
     * workflow verifies and de-duplicates on.
     */
    @Test
    void dispatchOnce_postsTheStoredBodyWithSignedHeaders() {
        AutomationEvent event = event(1);
        when(outbox.claimDue(25)).thenReturn(List.of(event));
        when(restTemplate.exchange(eq(URL), eq(HttpMethod.POST), any(), eq(String.class)))
                .thenReturn(ResponseEntity.ok("ok"));

        dispatcher.dispatchOnce();

        ArgumentCaptor<HttpEntity<String>> captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(eq(URL), eq(HttpMethod.POST), captor.capture(), eq(String.class));
        HttpEntity<String> sent = captor.getValue();

        assertThat(sent.getBody()).isEqualTo(event.getPayload());
        assertThat(sent.getHeaders().getFirst("X-Tryum-Event")).isEqualTo(AutomationEvents.SALE_COMPLETED);
        assertThat(sent.getHeaders().getFirst("X-Tryum-Store")).isEqualTo("42");
        assertThat(sent.getHeaders().getFirst("X-Tryum-Delivery")).isEqualTo(event.getEventId().toString());
        assertThat(sent.getHeaders().getFirst("X-Tryum-Signature")).isEqualTo("sha256=deadbeef");
        assertThat(sent.getHeaders().getFirst("X-Tryum-Timestamp")).isNotBlank();
    }

    /** n8n asleep on a free-tier host - the ordinary case, and a retry fixes it. */
    @Test
    void dispatchOnce_marksFailedWhenTheCallCannotBeMade() {
        when(outbox.claimDue(25)).thenReturn(List.of(event(1)));
        when(restTemplate.exchange(eq(URL), eq(HttpMethod.POST), any(), eq(String.class)))
                .thenThrow(new ResourceAccessException("connect timed out"));

        assertThat(dispatcher.dispatchOnce()).isZero();
        verify(outbox).markFailed(eq(1L), anyString());
    }

    /**
     * RestTemplate throws on 4xx and 5xx, so the only non-2xx that arrives as
     * a response is a redirect - an n8n sitting behind a proxy that has been
     * moved. Counting that as delivered would lose the event silently.
     */
    @Test
    void dispatchOnce_marksFailedOnANonSuccessResponse() {
        when(outbox.claimDue(25)).thenReturn(List.of(event(1)));
        when(restTemplate.exchange(eq(URL), eq(HttpMethod.POST), any(), eq(String.class)))
                .thenReturn(ResponseEntity.status(HttpStatus.FOUND).build());

        assertThat(dispatcher.dispatchOnce()).isZero();
        verify(outbox).markFailed(eq(1L), anyString());
    }

    /** One shop's broken webhook must not stop every other shop's events. */
    @Test
    void dispatchOnce_keepsGoingAfterOneEventFails() {
        when(outbox.claimDue(25)).thenReturn(List.of(event(1), event(2)));
        when(restTemplate.exchange(eq(URL), eq(HttpMethod.POST), any(), eq(String.class)))
                .thenThrow(new ResourceAccessException("boom"))
                .thenReturn(ResponseEntity.ok("ok"));

        assertThat(dispatcher.dispatchOnce()).isEqualTo(1);
        verify(outbox).markFailed(eq(1L), anyString());
        verify(outbox).markDelivered(2L);
    }

    @Test
    void dispatchOnce_reclaimsWorkAbandonedByADeadInstanceFirst() {
        when(outbox.claimDue(25)).thenReturn(List.of());

        dispatcher.dispatchOnce();

        verify(outbox).releaseStaleClaims(any(Duration.class));
    }

    @Test
    void run_staysQuietWithoutASigningSecret() {
        when(signatureService.isConfigured()).thenReturn(false);

        dispatcher.run();

        assertThat(dispatcher.isConfigured()).isFalse();
        verify(outbox, never()).claimDue(org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void run_staysQuietWithoutAWebhookUrl() {
        ReflectionTestUtils.setField(dispatcher, "webhookUrl", "");

        dispatcher.run();

        verify(outbox, never()).claimDue(org.mockito.ArgumentMatchers.anyInt());
    }
}
