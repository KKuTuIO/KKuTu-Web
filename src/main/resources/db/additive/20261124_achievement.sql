-- 업적 (achievements, shown as 명성). The game server also creates these on start (sub/utils/Achievement.ts ensureSchema), so running this by hand is optional.
-- Every row belongs to an account (account.uuid), not a game profile. Nothing here changes an existing table.
-- The definitions of the achievements (names, tiers, points) are a config file of the game server; the database only holds the counters and what was unlocked.

-- Counters per account. A stat_key that starts with 'max.' keeps the largest value, every other key is a sum (e.g. wins, time.KSH, max.chain).
CREATE TABLE IF NOT EXISTS game_achievement_stat (
    account_uuid varchar(64) NOT NULL,
    stat_key varchar(64) NOT NULL,
    value bigint NOT NULL DEFAULT 0,
    updated_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (account_uuid, stat_key)
);

-- The tiers that were reached: one row per tier of an achievement (ach_key), and the primary key keeps a tier from being given twice.
-- The points are not stored: they are summed from the definitions, so changing the point policy cannot make the data wrong.
CREATE TABLE IF NOT EXISTS game_achievement_unlock (
    account_uuid varchar(64) NOT NULL,
    ach_key varchar(96) NOT NULL,
    tier smallint NOT NULL,
    unlocked_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (account_uuid, ach_key, tier)
);
CREATE INDEX IF NOT EXISTS game_achievement_unlock_account_idx ON game_achievement_unlock (account_uuid, unlocked_at DESC);
