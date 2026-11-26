-- 말모이(사용자가 모은 낱말). 새 테이블과 인덱스만 만든다. 웹이 쓰고 게임 서버는 읽는다.
-- 계정에는 외래 키를 걸지 않는다: 계정이 탈퇴해도 낱말과 이력은 남는다(개인 식별 정보는 계정 쪽에서 지운다).

-- 한 권. kind: EXT(확장팩, 원 주제 base_theme 필요) | NEW(새 주제). state: UNLISTED(일부 공개) | PUBLIC(공개) | HIDDEN(운영팀 관리).
CREATE TABLE IF NOT EXISTS malmoi (
    id bigserial PRIMARY KEY,
    kind varchar(8) NOT NULL CHECK (kind IN ('EXT', 'NEW')),
    base_theme varchar(16),
    lang varchar(4) NOT NULL DEFAULT 'ko',
    title varchar(40) NOT NULL,
    description varchar(200) NOT NULL DEFAULT '',
    criteria jsonb NOT NULL DEFAULT '{}'::jsonb,
    cover jsonb NOT NULL DEFAULT '{}'::jsonb,
    creator_uuid uuid,
    creator_name varchar(40),
    owner_uuid uuid,
    owner_name varchar(40),
    state varchar(10) NOT NULL DEFAULT 'UNLISTED' CHECK (state IN ('UNLISTED', 'PUBLIC', 'HIDDEN')),
    invite_token varchar(32),
    locked boolean NOT NULL DEFAULT FALSE,
    revision bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX IF NOT EXISTS malmoi_invite_token_idx ON malmoi (invite_token) WHERE invite_token IS NOT NULL;
CREATE INDEX IF NOT EXISTS malmoi_state_idx ON malmoi (state, updated_at DESC);

-- 낱말. 게임에는 applies_at이 지났고 removed_at이 비어 있는 줄만 반영한다(공개 말모이는 올린 뒤 24시간).
-- meanings는 뜻의 배열, pending은 이미 반영된 낱말의 수정 대기분({meanings, source, applies_at}).
CREATE TABLE IF NOT EXISTS malmoi_word (
    id bigserial PRIMARY KEY,
    malmoi_id bigint NOT NULL,
    word varchar(80) NOT NULL,
    meanings jsonb NOT NULL DEFAULT '[]'::jsonb,
    source varchar(200) NOT NULL DEFAULT '',
    author_uuid uuid,
    author_name varchar(40),
    applies_at timestamptz NOT NULL DEFAULT now(),
    removed_at timestamptz,
    remove_at timestamptz,
    pending jsonb,
    locked boolean NOT NULL DEFAULT FALSE,
    promotion varchar(12),
    created_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (malmoi_id, word)
);
CREATE INDEX IF NOT EXISTS malmoi_word_live_idx ON malmoi_word (malmoi_id, applies_at) WHERE removed_at IS NULL;

-- 변경 이력(최근 변경, 되돌리기, 삭제 사유).
CREATE TABLE IF NOT EXISTS malmoi_revision (
    id bigserial PRIMARY KEY,
    malmoi_id bigint NOT NULL,
    word varchar(80),
    kind varchar(12) NOT NULL,
    reason varchar(80) NOT NULL DEFAULT '',
    actor_uuid uuid,
    actor_name varchar(40),
    reverted_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS malmoi_revision_book_idx ON malmoi_revision (malmoi_id, created_at DESC);

-- 계정별 등록(구독처럼). 방에서 "말모이"를 켜면 방장이 등록한 말모이를 모두 쓴다.
CREATE TABLE IF NOT EXISTS malmoi_registration (
    account_uuid uuid NOT NULL,
    malmoi_id bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (account_uuid, malmoi_id)
);
CREATE INDEX IF NOT EXISTS malmoi_registration_book_idx ON malmoi_registration (malmoi_id);

-- 초대한 편집자(일부 공개 말모이).
CREATE TABLE IF NOT EXISTS malmoi_editor (
    malmoi_id bigint NOT NULL,
    account_uuid uuid NOT NULL,
    name varchar(40) NOT NULL DEFAULT '',
    accepted boolean NOT NULL DEFAULT FALSE,
    created_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (malmoi_id, account_uuid)
);

-- 말모이 집필 면허. 한 계정의 발급 이력이고, 유효한 면허는 expires_at이 지나지 않았고 revoked_at이 비어 있는 줄이다.
CREATE TABLE IF NOT EXISTS malmoi_license (
    id bigserial PRIMARY KEY,
    account_uuid uuid NOT NULL,
    profile_id varchar(64),
    number varchar(24) NOT NULL,
    oath_version smallint NOT NULL DEFAULT 1,
    fee integer NOT NULL,
    issued_at timestamptz NOT NULL DEFAULT now(),
    expires_at timestamptz NOT NULL,
    revoked_at timestamptz,
    revoke_reason varchar(120),
    reissue_after timestamptz
);
CREATE INDEX IF NOT EXISTS malmoi_license_account_idx ON malmoi_license (account_uuid, issued_at DESC);

-- 공개 심사(레벨 30 미만이 만든 말모이를 공개로 올릴 때 백오피스에서 본다).
CREATE TABLE IF NOT EXISTS malmoi_publish_review (
    id bigserial PRIMARY KEY,
    malmoi_id bigint NOT NULL,
    revision bigint NOT NULL,
    requested_by uuid,
    status varchar(10) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    reason varchar(200) NOT NULL DEFAULT '',
    reviewer_uuid uuid,
    created_at timestamptz NOT NULL DEFAULT now(),
    decided_at timestamptz
);
CREATE INDEX IF NOT EXISTS malmoi_publish_review_status_idx ON malmoi_publish_review (status, created_at);

-- 신고.
CREATE TABLE IF NOT EXISTS malmoi_report (
    id bigserial PRIMARY KEY,
    malmoi_id bigint NOT NULL,
    word varchar(80),
    reporter_uuid uuid,
    reason varchar(80) NOT NULL,
    detail varchar(300) NOT NULL DEFAULT '',
    status varchar(10) NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'DONE', 'DISMISSED')),
    handled_by uuid,
    created_at timestamptz NOT NULL DEFAULT now(),
    handled_at timestamptz
);
CREATE INDEX IF NOT EXISTS malmoi_report_status_idx ON malmoi_report (status, created_at);

-- 말모이별 차단(최대 7일, 그 말모이 안에서만).
CREATE TABLE IF NOT EXISTS malmoi_block (
    malmoi_id bigint NOT NULL,
    account_uuid uuid NOT NULL,
    name varchar(40) NOT NULL DEFAULT '',
    reason varchar(120) NOT NULL DEFAULT '',
    blocked_by uuid,
    until_at timestamptz NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (malmoi_id, account_uuid)
);

-- 승계(확장팩 낱말이 원 주제로 들어간 기록). 낱말은 malmoi_word.promotion에도 표시한다.
CREATE TABLE IF NOT EXISTS malmoi_promotion (
    id bigserial PRIMARY KEY,
    malmoi_id bigint NOT NULL,
    word varchar(80) NOT NULL,
    base_theme varchar(16),
    result varchar(10) NOT NULL CHECK (result IN ('APPROVED', 'REJECTED', 'EXISTING')),
    reason varchar(200) NOT NULL DEFAULT '',
    reviewer_uuid uuid,
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS malmoi_promotion_book_idx ON malmoi_promotion (malmoi_id, created_at DESC);

-- 승계 신청(공개 말모이의 주인장이 신청하고, 단어팀이 검토한다).
CREATE TABLE IF NOT EXISTS malmoi_promotion_request (
    id bigserial PRIMARY KEY,
    malmoi_id bigint NOT NULL,
    requested_by uuid,
    words integer NOT NULL,
    status varchar(10) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'ACCEPTED', 'REJECTED')),
    reason varchar(200) NOT NULL DEFAULT '',
    reviewer_uuid uuid,
    created_at timestamptz NOT NULL DEFAULT now(),
    decided_at timestamptz
);
CREATE INDEX IF NOT EXISTS malmoi_promotion_request_status_idx ON malmoi_promotion_request (status, created_at);

-- 낱말이 게임에서 쓰인 횟수(승계 검토 자료). 게임 서버가 쓰고 백오피스가 읽는다. 공식 낱말의 hit와는 따로 센다.
CREATE TABLE IF NOT EXISTS malmoi_word_use (
    malmoi_id bigint NOT NULL,
    word varchar(80) NOT NULL,
    uses bigint NOT NULL DEFAULT 0,
    last_used_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (malmoi_id, word)
);
