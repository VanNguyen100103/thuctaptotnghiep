package com.ut.edu.backend.automation;

/**
 * Where an outbox row is in its life.
 *
 * SENDING exists because a POST is not instant and this app runs more than
 * one instance: without it two sweepers would pick up the same row and the
 * shop would get the same Telegram message twice. A row is claimed into
 * SENDING in its own short transaction, and only then posted.
 */
public enum AutomationEventStatus {

    /** Waiting for its turn - either never tried, or backing off after a failure. */
    PENDING,

    /** Claimed by a sweeper and currently being posted. */
    SENDING,

    /** n8n accepted it. Terminal. */
    DELIVERED,

    /** Out of attempts. Terminal, and kept so the failure is visible rather than silent. */
    DEAD
}
