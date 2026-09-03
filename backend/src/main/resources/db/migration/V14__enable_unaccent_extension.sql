-- ProductRepository.adminSearchProducts (the admin "Hàng hóa" search box) and
-- the storefront search both call Postgres' unaccent() in a native query to
-- match "ao" against "áo", but nothing ever enabled the extension - it only
-- worked on databases where a developer had created it manually, and threw
-- "function unaccent(text) does not exist" everywhere else, breaking search.
CREATE EXTENSION IF NOT EXISTS unaccent;
