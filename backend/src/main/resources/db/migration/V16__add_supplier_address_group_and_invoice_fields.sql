-- V16: fields to match KiotViet's real "Tạo nhà cung cấp" modal (user-supplied
-- screenshot) - "Khu vực"/"Phường/Xã" under "Địa chỉ", "Nhóm nhà cung cấp"
-- (a free-text tag, same treatment as Product#brand - no manageable group
-- entity, matches this app's existing "no fake dropdown" convention), and
-- "Tên công ty" under "Thông tin xuất hóa đơn" (taxCode/"Mã số thuế" already
-- existed from V15). All optional, plain strings - no province/district/ward
-- dataset backing "Khu vực"/"Phường/Xã", so they're plain text inputs on the
-- frontend, not a real cascading address search.

ALTER TABLE public.suppliers
    ADD COLUMN region character varying(200),
    ADD COLUMN ward character varying(200),
    ADD COLUMN group_name character varying(200),
    ADD COLUMN company_name character varying(200);
