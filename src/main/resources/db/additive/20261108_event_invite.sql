-- 친구 초대. Everything is kept per ACCOUNT (a person), never per profile: a person may have up to three profiles.
CREATE TABLE IF NOT EXISTS game_invite_code (
    account_uuid UUID PRIMARY KEY,
    code TEXT NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Who invited whom, once and for good: an account is invited at most once in an event, and never by itself.
CREATE TABLE IF NOT EXISTS game_invite_link (
    invitee_uuid UUID NOT NULL,
    event_id TEXT NOT NULL,
    inviter_uuid UUID NOT NULL,
    linked_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (invitee_uuid, event_id),
    CONSTRAINT game_invite_link_not_self CHECK (invitee_uuid <> inviter_uuid)
);
CREATE INDEX IF NOT EXISTS game_invite_link_inviter_idx ON game_invite_link (inviter_uuid, event_id, linked_at);

-- How far an invited account is in each stage, and which side has taken the stage's rewards.
CREATE TABLE IF NOT EXISTS game_invite_stage (
    invitee_uuid UUID NOT NULL,
    event_id TEXT NOT NULL,
    stage_id TEXT NOT NULL,
    progress BIGINT NOT NULL DEFAULT 0,
    invitee_claimed BOOLEAN NOT NULL DEFAULT FALSE,
    inviter_claimed BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (invitee_uuid, event_id, stage_id),
    CONSTRAINT game_invite_stage_nonnegative CHECK (progress >= 0)
);
