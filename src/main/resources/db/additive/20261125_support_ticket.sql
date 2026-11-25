-- 고객 문의(티켓). 새 테이블과 인덱스만 만든다. 기존 테이블은 바꾸지 않는다.
-- 계정에는 외래 키를 걸지 않는다: 계정이 탈퇴해도 상담 기록은 보존 기간 동안 남는다.

-- 문의를 다루는 운영자. 사용자에게 보이는 이름과 직함, 문의 업무 역할.
CREATE TABLE IF NOT EXISTS support_staff (
    account_uuid uuid PRIMARY KEY,
    public_name varchar(40) NOT NULL,
    title varchar(40) NOT NULL,
    role varchar(16) NOT NULL CHECK (role IN ('VIEWER', 'AGENT', 'MANAGER')),
    active boolean NOT NULL DEFAULT TRUE,
    updated_by uuid NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS support_ticket (
    id uuid PRIMARY KEY,
    code varchar(16) NOT NULL UNIQUE,
    account_id uuid NOT NULL,
    account_uuid uuid NOT NULL,
    game_user_id varchar(128),
    nickname varchar(255),
    category varchar(48) NOT NULL,
    subject varchar(120) NOT NULL,
    status varchar(16) NOT NULL CHECK (status IN ('RECEIVED', 'IN_PROGRESS', 'ANSWERED', 'RESOLVED', 'CLOSED')),
    assignee_uuid uuid,
    notify_email boolean NOT NULL DEFAULT FALSE,
    details jsonb NOT NULL DEFAULT '{}'::jsonb,
    consent_version varchar(16) NOT NULL,
    consented_at timestamptz NOT NULL,
    request_key uuid NOT NULL,
    version integer NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    due_at timestamptz,
    user_read_at timestamptz,
    last_staff_message_at timestamptz,
    resolved_at timestamptz,
    closed_at timestamptz,
    UNIQUE (account_id, request_key)
);
CREATE INDEX IF NOT EXISTS support_ticket_account_idx ON support_ticket (account_id, updated_at DESC);
CREATE INDEX IF NOT EXISTS support_ticket_queue_idx ON support_ticket (status, updated_at);

-- 대화와 이력이 한 타임라인이다. NOTE(내부 메모)는 사용자에게 보내지 않는다.
CREATE TABLE IF NOT EXISTS support_message (
    id bigserial PRIMARY KEY,
    ticket_id uuid NOT NULL REFERENCES support_ticket (id) ON DELETE CASCADE,
    kind varchar(8) NOT NULL CHECK (kind IN ('USER', 'STAFF', 'NOTE', 'EVENT')),
    actor_uuid uuid,
    staff_name varchar(40),
    staff_title varchar(40),
    body text NOT NULL DEFAULT '',
    event jsonb,
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS support_message_ticket_idx ON support_message (ticket_id, id);

-- 첨부 파일 자리. 지금은 업로드를 받지 않는다(API 가 거절한다).
CREATE TABLE IF NOT EXISTS support_attachment (
    id uuid PRIMARY KEY,
    message_id bigint NOT NULL REFERENCES support_message (id) ON DELETE CASCADE,
    file_name varchar(160) NOT NULL,
    content_type varchar(64) NOT NULL,
    byte_size integer NOT NULL,
    data bytea NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS support_attachment_message_idx ON support_attachment (message_id);

CREATE TABLE IF NOT EXISTS support_setting (
    key varchar(48) PRIMARY KEY,
    value jsonb NOT NULL,
    updated_by uuid NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now()
);
