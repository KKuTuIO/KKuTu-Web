BEGIN;

CREATE TABLE IF NOT EXISTS dictionary_public_word (
    lang VARCHAR(2) NOT NULL CHECK (lang IN ('ko', 'en')),
    word TEXT NOT NULL,
    reason VARCHAR(200) NOT NULL DEFAULT '관리자 공개',
    created_by VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (lang, word)
);

CREATE INDEX IF NOT EXISTS idx_dictionary_public_word_created_at
    ON dictionary_public_word (created_at DESC);

COMMIT;
