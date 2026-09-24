package com.ut.edu.backend.automation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AutomationOutboxTest {

    @Mock private AutomationEventRepository eventRepository;

    private AutomationOutbox outbox;
    private AutomationEvent event;

    @BeforeEach
    void setUp() {
        outbox = new AutomationOutbox(eventRepository);
        ReflectionTestUtils.setField(outbox, "maxAttempts", 6);

        event = AutomationEvent.builder()
                .id(1L)
                .storeId(42L)
                .eventId(UUID.randomUUID())
                .eventKey(AutomationEvents.SALE_COMPLETED)
                .payload("{}")
                .status(AutomationEventStatus.SENDING)
                .attempts(0)
                .nextAttemptAt(LocalDateTime.now())
                .build();
        event.setCreatedAt(LocalDateTime.now());
        when(eventRepository.findById(1L)).thenReturn(Optional.of(event));
    }

    @Test
    void claimDue_marksWhatItTookSoNoOtherInstanceTakesItToo() {
        when(eventRepository.findDueIds(any(LocalDateTime.class), anyInt())).thenReturn(List.of(1L));
        when(eventRepository.findAllById(anyList())).thenReturn(List.of(event));

        assertThat(outbox.claimDue(25)).containsExactly(event);
        verify(eventRepository).claim(eq(List.of(1L)), eq(AutomationEventStatus.SENDING), any(LocalDateTime.class));
    }

    @Test
    void claimDue_doesNotTouchTheDatabaseWhenThereIsNothingDue() {
        when(eventRepository.findDueIds(any(LocalDateTime.class), anyInt())).thenReturn(List.of());

        assertThat(outbox.claimDue(25)).isEmpty();
        verify(eventRepository, org.mockito.Mockito.never()).claim(anyList(), any(), any());
    }

    /** First failure goes back in a minute - the common cause is n8n waking up. */
    @Test
    void markFailed_schedulesTheFirstRetryAMinuteOut() {
        outbox.markFailed(1L, "connect timed out", false);

        assertThat(event.getStatus()).isEqualTo(AutomationEventStatus.PENDING);
        assertThat(event.getAttempts()).isEqualTo(1);
        assertThat(event.getLastError()).isEqualTo("connect timed out");
        assertThat(event.getNextAttemptAt()).isBetween(
                LocalDateTime.now().plusSeconds(50), LocalDateTime.now().plusSeconds(70));
    }

    /** Later failures back off, so a wrong URL is not hammered for hours. */
    @Test
    void markFailed_backsOffFurtherEachTime() {
        event.setAttempts(3);

        outbox.markFailed(1L, "connect timed out", false);

        assertThat(event.getAttempts()).isEqualTo(4);
        assertThat(event.getNextAttemptAt()).isAfter(LocalDateTime.now().plusMinutes(100));
    }

    @Test
    void markFailed_givesUpAtTheAttemptCeiling() {
        event.setAttempts(5);

        outbox.markFailed(1L, "connect timed out", false);

        assertThat(event.getStatus()).isEqualTo(AutomationEventStatus.DEAD);
        assertThat(event.getAttempts()).isEqualTo(6);
    }

    /** A dead event keeps its reason: the shop's screen is where it gets explained. */
    @Test
    void markFailed_truncatesAReasonTooLongForTheColumn() {
        outbox.markFailed(1L, "x".repeat(900), false);

        assertThat(event.getLastError()).hasSize(500).endsWith("...");
    }

    @Test
    void markDelivered_isTerminalAndClearsTheLastError() {
        event.setLastError("connect timed out");

        outbox.markDelivered(1L);

        assertThat(event.getStatus()).isEqualTo(AutomationEventStatus.DELIVERED);
        assertThat(event.getDeliveredAt()).isNotNull();
        assertThat(event.getLastError()).isNull();
    }

    /**
     * The receiver is a free-tier service that spins down; the request that
     * wakes it is answered about nine minutes later. Spending an attempt on
     * that would push the next try past the fifteen minutes it stays up, so
     * every later retry would wake it and die without ever delivering.
     */
    @Test
    void markFailed_doesNotSpendAnAttemptWhileTheTargetIsStillWakingUp() {
        outbox.markFailed(1L, "ResourceAccessException: connect timed out", true);

        assertThat(event.getAttempts()).isZero();
        assertThat(event.getStatus()).isEqualTo(AutomationEventStatus.PENDING);
        assertThat(event.getNextAttemptAt()).isBetween(
                LocalDateTime.now().plusSeconds(50), LocalDateTime.now().plusSeconds(70));
    }

    @Test
    void markFailed_fallsBackToBackoffOnceTheWakeWindowHasPassed() {
        event.setCreatedAt(LocalDateTime.now().minusMinutes(40));

        outbox.markFailed(1L, "ResourceAccessException: connect timed out", true);

        assertThat(event.getAttempts()).isEqualTo(1);
        assertThat(event.getNextAttemptAt()).isAfter(LocalDateTime.now().plusSeconds(50));
    }

    /** A 404 is not a slow start - waiting does not publish a workflow. */
    @Test
    void markFailed_spendsAnAttemptForAFailureWaitingCannotFix() {
        outbox.markFailed(1L, "HTTP 404 NOT_FOUND", false);

        assertThat(event.getAttempts()).isEqualTo(1);
    }

}
