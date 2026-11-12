-- 패밀리 (guild-like groups). The game server also creates these on start (sub/utils/Family.ts ensureSchema), so running this by hand is optional.
-- A member is an account (account.uuid), not a game profile: one family per account.
-- Named family_group* because the original game already has an unrelated `family` table (_id, data).

CREATE TABLE IF NOT EXISTS family_group (
    id bigserial PRIMARY KEY,
    name varchar(48) NOT NULL,
    intro varchar(400) NOT NULL DEFAULT '',
    recruiting boolean NOT NULL DEFAULT true,
    fame bigint NOT NULL DEFAULT 0,
    created_at bigint NOT NULL
);
CREATE UNIQUE INDEX IF NOT EXISTS family_group_name_uidx ON family_group (lower(name));
CREATE TABLE IF NOT EXISTS family_group_member (
    account_uuid varchar(64) PRIMARY KEY,
    family_id bigint NOT NULL REFERENCES family_group(id) ON DELETE CASCADE,
    role varchar(8) NOT NULL DEFAULT 'member',
    rep_user_id varchar(100) NOT NULL,
    rep_nick varchar(100) NOT NULL DEFAULT '',
    weekly bigint NOT NULL DEFAULT 0,
    week integer NOT NULL DEFAULT 0,
    attended_day integer NOT NULL DEFAULT 0,
    joined_at bigint NOT NULL
);
CREATE INDEX IF NOT EXISTS family_group_member_family_idx ON family_group_member (family_id);
CREATE TABLE IF NOT EXISTS family_group_application (
    family_id bigint NOT NULL REFERENCES family_group(id) ON DELETE CASCADE,
    account_uuid varchar(64) NOT NULL,
    user_id varchar(100) NOT NULL,
    nick varchar(100) NOT NULL DEFAULT '',
    created_at bigint NOT NULL,
    PRIMARY KEY (family_id, account_uuid)
);
CREATE INDEX IF NOT EXISTS family_group_application_account_idx ON family_group_application (account_uuid);
