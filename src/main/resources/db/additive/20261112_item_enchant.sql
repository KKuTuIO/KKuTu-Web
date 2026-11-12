-- 강화 (item enchant): the tables the enchant feature uses, in their final shape. Nothing here changes an existing table.
-- item_enchant keeps what is shared by the copies of an item (the failures in a row, "pity"); a copy that has been enchanted has a row in
-- item_enchant_instance (instance = its number among the user's copies of that item), and every try is logged in item_enchant_log.
CREATE TABLE IF NOT EXISTS item_enchant (
    user_id    text        NOT NULL,
    item_id    text        NOT NULL,
    pity       integer     NOT NULL DEFAULT 0,
    attempts   integer     NOT NULL DEFAULT 0,
    updated_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, item_id)
);
CREATE TABLE IF NOT EXISTS item_enchant_log (
    id          bigserial PRIMARY KEY,
    user_id     text        NOT NULL,
    item_id     text        NOT NULL,
    ticket      text        NOT NULL,
    pity_before integer     NOT NULL,
    chance      integer     NOT NULL,
    success     boolean     NOT NULL,
    cost        bigint      NOT NULL,
    line        jsonb,
    created_at  timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS item_enchant_log_user ON item_enchant_log (user_id, created_at);
CREATE TABLE IF NOT EXISTS item_enchant_instance (
    user_id    text        NOT NULL,
    item_id    text        NOT NULL,
    instance   integer     NOT NULL,
    level      integer     NOT NULL DEFAULT 0,
    lines      jsonb       NOT NULL DEFAULT '[]'::jsonb,
    pending    jsonb,
    worn       boolean     NOT NULL DEFAULT false,
    updated_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, item_id, instance)
);
