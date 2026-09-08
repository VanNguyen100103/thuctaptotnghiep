-- V31: link a Zalo account to a user.
--
-- Google sign-in needed no column: it hands back a verified email, and email
-- is already the key both systems share. Zalo hands back neither an email
-- nor a phone - its Social API returns id, name, gender, birthday and
-- picture, and the phone number needs a permission that requires business
-- verification.
--
-- The id it does return is scoped to the application, so it means nothing to
-- this system until somebody tells it what it means. Hence a stored link:
-- a signed-in user connects their Zalo account once, and from then on that
-- id identifies them.
--
-- The consequence is worth naming: Zalo cannot sign in a first-time user,
-- and cannot register one either. It is a faster way back in for people who
-- already have an account, not a way to get one.
--
-- Unique because a Zalo account is one person: without it, two users could
-- link the same Zalo and the login would have to pick one.

ALTER TABLE public.users ADD COLUMN zalo_user_id character varying(50);

ALTER TABLE public.users
    ADD CONSTRAINT uk_users_zalo_user_id UNIQUE (zalo_user_id);

COMMENT ON COLUMN public.users.zalo_user_id IS
    'Zalo user id from Social API, scoped to this application. Set when the user links their Zalo account; null until then.';
