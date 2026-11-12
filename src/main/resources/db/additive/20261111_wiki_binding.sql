-- 리오위키 account binding and edit-count contribution (KKuTu-Web `wiki/`; the web service also creates these with IF NOT EXISTS on first use).
-- One game account <-> one wiki user (by wiki user id, which survives renames), in both directions.
CREATE TABLE IF NOT EXISTS wiki_binding (
    account_id UUID PRIMARY KEY REFERENCES account(id) ON DELETE CASCADE,
    wiki_user_id BIGINT NOT NULL UNIQUE,
    wiki_username TEXT NOT NULL,
    bound_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- The code a player must show on the wiki to prove the wiki account is theirs. One open challenge per account; a new one replaces it.
CREATE TABLE IF NOT EXISTS wiki_bind_challenge (
    account_id UUID PRIMARY KEY REFERENCES account(id) ON DELETE CASCADE,
    wiki_username TEXT NOT NULL,
    code TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMPTZ NOT NULL
);

-- One row per wiki edit that counts towards 기여도 (the number of edits). settled_at is set once the edit is 24 hours old and not reverted.
CREATE TABLE IF NOT EXISTS wiki_contribution (
    revision_id BIGINT PRIMARY KEY,
    account_id UUID NOT NULL REFERENCES account(id) ON DELETE CASCADE,
    wiki_user_id BIGINT NOT NULL,
    page_title TEXT NOT NULL,
    edited_at TIMESTAMPTZ NOT NULL,
    settled_at TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS idx_wiki_contribution_account ON wiki_contribution (account_id, settled_at);
