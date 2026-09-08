package com.ut.edu.backend.shipping.goship;

/**
 * What the recipient is allowed to do with the parcel before paying.
 *
 * Three states rather than a boolean because the middle one is a real,
 * different promise: letting someone look inside the box is not the same as
 * letting them try the thing on and hand it back. KiotViet offers exactly
 * these three, and so does GHN's own required_note.
 *
 * Goship has no field for any of it (see V30), so the label below is what
 * actually travels - written into parcel.metadata and printed on the
 * delivery slip for the courier at the door to read.
 */
public enum InspectionPolicy {

    NO_INSPECTION("Không cho xem hàng"),
    VIEW_ONLY("Cho xem, không thử"),
    TRIAL_ALLOWED("Cho thử hàng");

    private final String note;

    InspectionPolicy(String note) {
        this.note = note;
    }

    /** The instruction as the courier reads it. */
    public String note() {
        return note;
    }
}
