-- V34: "Bán trực tiếp" was the wrong name for the channel V32 stamps on a
-- register order.
--
-- It reads as a counter sale - customer standing there, takes the goods, goes
-- home - and that is precisely the sale that raises no order at all. The only
-- thing that ever produced this value is the "Bán giao hàng" tab, whose whole
-- point is that the goods are NOT handed over directly. So the order screen
-- ended up printing "Kênh bán: Bán trực tiếp" one column away from "Đối tác
-- giao: Giao Hàng Tiết Kiệm", which is a contradiction on its face.
--
-- Renamed to what it actually is. The constraint is rebuilt around the new
-- name rather than widened to accept both: there is no such thing as a
-- half-migrated channel here, and leaving 'DIRECT' legal would let it come
-- back.

UPDATE public.orders SET sales_channel = 'POS_DELIVERY' WHERE sales_channel = 'DIRECT';

ALTER TABLE public.orders DROP CONSTRAINT orders_sales_channel_check;

ALTER TABLE public.orders ADD CONSTRAINT orders_sales_channel_check
    CHECK (((sales_channel)::text = ANY ((ARRAY['STOREFRONT'::character varying, 'POS_DELIVERY'::character varying, 'FACEBOOK'::character varying, 'ZALO'::character varying, 'SHOPEE'::character varying, 'LAZADA'::character varying, 'TIKTOK'::character varying, 'OTHER'::character varying])::text[])));
