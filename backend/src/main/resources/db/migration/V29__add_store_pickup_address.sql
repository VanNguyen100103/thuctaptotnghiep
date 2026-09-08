-- V29: each store keeps its own pickup point.
--
-- V28 introduced Goship, which unlike GHN wants the pickup end named on
-- every quote and booking. That first went into deployment-wide config,
-- which was wrong for this app: it is a multi-store platform, every store
-- sells from its own address, and one set of coordinates in an environment
-- variable would have shipped every store's parcels from whichever shop the
-- deployment happened to be configured for.
--
-- Only the three codes live here. The pickup contact - who the courier
-- calls, and the street they come to - is already on this table as name,
-- phone and address, and is what the receipt prints; asking for it twice
-- would let the two drift apart.
--
-- Nullable because an existing store has none: shipping simply refuses to
-- quote until the store fills them in, which is a better failure than
-- silently booking from the wrong place.

ALTER TABLE public.stores ADD COLUMN goship_city_id character varying(20);
ALTER TABLE public.stores ADD COLUMN goship_district_id character varying(20);
ALTER TABLE public.stores ADD COLUMN goship_ward_id character varying(20);

COMMENT ON COLUMN public.stores.goship_city_id IS 'Goship city code for the pickup address, e.g. 700000';
COMMENT ON COLUMN public.stores.goship_district_id IS 'Goship district code for the pickup address';
COMMENT ON COLUMN public.stores.goship_ward_id IS 'Goship ward id for the pickup address';
