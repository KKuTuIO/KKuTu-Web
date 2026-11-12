-- A returning player's stretch in an event for returning players: one row each time an account comes back after the event's days away.
-- The progress of that stretch is kept under the account and the cycle, so a new stretch starts from nothing.
CREATE TABLE IF NOT EXISTS game_event_enrollment (
    account_uuid UUID NOT NULL,
    event_id TEXT NOT NULL,
    cycle INTEGER NOT NULL,
    entered_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (account_uuid, event_id, cycle)
);
