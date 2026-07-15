-- V4: Multi-tenancy - map legacy ADMIN accounts to the demo store (TODO Phase 1.4)
-- The dashboard moved from /admin/** (ROLE_ADMIN) to /store/** (OWNER/MANAGER),
-- so pre-SaaS admin users become OWNER of the demo store their data was
-- backfilled into (V3). Without this they would lose dashboard access.
-- SUPER_ADMIN (platform operator) accounts are seeded separately in Phase 2.

UPDATE public.users u
SET store_id   = s.id,
    store_role = 'OWNER'
FROM public.stores s
WHERE s.slug = 'fashion-store-demo'
  AND u.store_id IS NULL
  AND u.store_role IS NULL
  AND EXISTS (
      SELECT 1 FROM public.user_roles r
      WHERE r.user_id = u.id AND r.role = 'ADMIN'
  );
