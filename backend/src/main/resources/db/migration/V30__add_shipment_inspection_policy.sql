-- V30: "Xem hàng" policy on a shipment.
--
-- Three states, not two: KiotViet offers Không cho xem hàng / Cho xem,
-- không thử / Cho thử hàng, which is also the shape GHN's own required_note
-- has. A boolean cannot hold the middle one - letting a buyer look inside is
-- a different promise from letting them try the thing on.
--
-- Goship's shipment API has no field for any of it. Its 23 parameters
-- normalise what ten carriers have in common, and each carrier's own
-- inspection flag does not survive that. What does reach the courier is
-- parcel.metadata and the printed delivery slip: KiotViet's own slip carries
-- the instruction as a line of text under "Lưu ý khi giao hàng", which is
-- how it materially takes effect there too.
--
-- So this is stored to be sent as a note and printed, not as a flag the
-- carrier's system enforces.
--
-- Defaults to no inspection, the safer of the three for a shop: a parcel
-- opened before payment can be refused after handling.

ALTER TABLE public.shipments
    ADD COLUMN inspection_policy character varying(20) NOT NULL DEFAULT 'NO_INSPECTION';

ALTER TABLE public.shipments
    ADD CONSTRAINT shipments_inspection_policy_check
    CHECK (inspection_policy IN ('NO_INSPECTION', 'VIEW_ONLY', 'TRIAL_ALLOWED'));

COMMENT ON COLUMN public.shipments.inspection_policy IS
    'Whether the recipient may open or try the goods before paying. Sent to the carrier as a note in parcel.metadata and printed on the delivery slip - Goship has no field that enforces it.';
