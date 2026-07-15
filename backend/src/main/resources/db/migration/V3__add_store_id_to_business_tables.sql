-- V3: Multi-tenancy - add store_id to business tables (TODO Phase 1.2)
-- Direct tenant column: products, categories, orders, carts, coupons,
-- coupon_usages, payments, wishlists, product_views.
-- Indirect via relations (no own column): product_images, order_items,
-- cart_items, reviews, review_images.
-- Existing data is backfilled to the demo store created in V2.
-- Columns stay NULLABLE until the 1.4 service refactor sets the store on
-- every write path; a follow-up migration will then tighten to NOT NULL.

-- ========================= ADD store_id COLUMNS ======================
ALTER TABLE public.products      ADD COLUMN store_id bigint;
ALTER TABLE public.categories    ADD COLUMN store_id bigint;
ALTER TABLE public.orders        ADD COLUMN store_id bigint;
ALTER TABLE public.carts         ADD COLUMN store_id bigint;
ALTER TABLE public.coupons       ADD COLUMN store_id bigint;
ALTER TABLE public.coupon_usages ADD COLUMN store_id bigint;
ALTER TABLE public.payments      ADD COLUMN store_id bigint;
ALTER TABLE public.wishlists     ADD COLUMN store_id bigint;
ALTER TABLE public.product_views ADD COLUMN store_id bigint;

-- ===================== BACKFILL TO THE DEMO STORE ====================
-- V2 seeded 'fashion-store-demo'; all pre-SaaS data belongs to it.
UPDATE public.products      SET store_id = s.id FROM public.stores s WHERE s.slug = 'fashion-store-demo';
UPDATE public.categories    SET store_id = s.id FROM public.stores s WHERE s.slug = 'fashion-store-demo';
UPDATE public.orders        SET store_id = s.id FROM public.stores s WHERE s.slug = 'fashion-store-demo';
UPDATE public.carts         SET store_id = s.id FROM public.stores s WHERE s.slug = 'fashion-store-demo';
UPDATE public.coupons       SET store_id = s.id FROM public.stores s WHERE s.slug = 'fashion-store-demo';
UPDATE public.coupon_usages SET store_id = s.id FROM public.stores s WHERE s.slug = 'fashion-store-demo';
UPDATE public.payments      SET store_id = s.id FROM public.stores s WHERE s.slug = 'fashion-store-demo';
UPDATE public.wishlists     SET store_id = s.id FROM public.stores s WHERE s.slug = 'fashion-store-demo';
UPDATE public.product_views SET store_id = s.id FROM public.stores s WHERE s.slug = 'fashion-store-demo';

-- ========================= FOREIGN KEYS ==============================
ALTER TABLE ONLY public.products      ADD CONSTRAINT fk_products_store      FOREIGN KEY (store_id) REFERENCES public.stores(id);
ALTER TABLE ONLY public.categories    ADD CONSTRAINT fk_categories_store    FOREIGN KEY (store_id) REFERENCES public.stores(id);
ALTER TABLE ONLY public.orders        ADD CONSTRAINT fk_orders_store        FOREIGN KEY (store_id) REFERENCES public.stores(id);
ALTER TABLE ONLY public.carts         ADD CONSTRAINT fk_carts_store         FOREIGN KEY (store_id) REFERENCES public.stores(id);
ALTER TABLE ONLY public.coupons       ADD CONSTRAINT fk_coupons_store       FOREIGN KEY (store_id) REFERENCES public.stores(id);
ALTER TABLE ONLY public.coupon_usages ADD CONSTRAINT fk_coupon_usages_store FOREIGN KEY (store_id) REFERENCES public.stores(id);
ALTER TABLE ONLY public.payments      ADD CONSTRAINT fk_payments_store      FOREIGN KEY (store_id) REFERENCES public.stores(id);
ALTER TABLE ONLY public.wishlists     ADD CONSTRAINT fk_wishlists_store     FOREIGN KEY (store_id) REFERENCES public.stores(id);
ALTER TABLE ONLY public.product_views ADD CONSTRAINT fk_product_views_store FOREIGN KEY (store_id) REFERENCES public.stores(id);

-- ============================ INDEXES ================================
CREATE INDEX idx_products_store      ON public.products (store_id);
CREATE INDEX idx_categories_store    ON public.categories (store_id);
CREATE INDEX idx_orders_store        ON public.orders (store_id);
CREATE INDEX idx_carts_store         ON public.carts (store_id);
CREATE INDEX idx_coupons_store       ON public.coupons (store_id);
CREATE INDEX idx_coupon_usages_store ON public.coupon_usages (store_id);
CREATE INDEX idx_payments_store      ON public.payments (store_id);
CREATE INDEX idx_wishlists_store     ON public.wishlists (store_id);
CREATE INDEX idx_product_views_store ON public.product_views (store_id);

-- ================= GLOBAL UNIQUE -> UNIQUE PER STORE =================
-- Two stores must be able to use the same product slug/sku, category
-- name/slug, coupon code; a user gets one cart per store.
-- Constraint names below are the Hibernate-generated ones dumped in V1;
-- IF EXISTS keeps this idempotent for databases created slightly differently.

-- products: slug, sku
ALTER TABLE public.products DROP CONSTRAINT IF EXISTS ukostq1ec3toafnjok09y9l7dox; -- UNIQUE (slug)
ALTER TABLE public.products DROP CONSTRAINT IF EXISTS ukfhmd06dsmj6k0n90swsh8ie9g; -- UNIQUE (sku)
ALTER TABLE ONLY public.products ADD CONSTRAINT uk_products_store_slug UNIQUE (store_id, slug);
ALTER TABLE ONLY public.products ADD CONSTRAINT uk_products_store_sku  UNIQUE (store_id, sku);

-- categories: name, slug
ALTER TABLE public.categories DROP CONSTRAINT IF EXISTS ukt8o6pivur7nn124jehx7cygw5; -- UNIQUE (name)
ALTER TABLE public.categories DROP CONSTRAINT IF EXISTS ukoul14ho7bctbefv8jywp5v3i2; -- UNIQUE (slug)
ALTER TABLE ONLY public.categories ADD CONSTRAINT uk_categories_store_name UNIQUE (store_id, name);
ALTER TABLE ONLY public.categories ADD CONSTRAINT uk_categories_store_slug UNIQUE (store_id, slug);

-- coupons: code
ALTER TABLE public.coupons DROP CONSTRAINT IF EXISTS ukeplt0kkm9yf2of2lnx6c1oy9b; -- UNIQUE (code)
ALTER TABLE ONLY public.coupons ADD CONSTRAINT uk_coupons_store_code UNIQUE (store_id, code);

-- carts: one cart per user per store (entity refactor lands with 1.4)
ALTER TABLE public.carts DROP CONSTRAINT IF EXISTS uk64t7ox312pqal3p7fg9o503c2; -- UNIQUE (user_id)
ALTER TABLE ONLY public.carts ADD CONSTRAINT uk_carts_store_user UNIQUE (user_id, store_id);
