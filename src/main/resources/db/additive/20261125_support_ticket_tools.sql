-- 고객 문의의 보관함과 답변 템플릿. 새 테이블만 만든다.

-- 보관한 문의. 행이 있으면 운영자의 처리함에서 숨긴다. 문의가 지워지면 함께 지워진다.
CREATE TABLE IF NOT EXISTS support_ticket_archive (
    ticket_id uuid PRIMARY KEY REFERENCES support_ticket (id) ON DELETE CASCADE,
    archived_by uuid NOT NULL,
    archived_at timestamptz NOT NULL DEFAULT now()
);

-- 운영자가 함께 쓰는 답변 템플릿. category 가 비어 있으면 모든 분류에서 쓴다.
CREATE TABLE IF NOT EXISTS support_reply_template (
    id uuid PRIMARY KEY,
    title varchar(80) NOT NULL,
    category varchar(48),
    body text NOT NULL,
    updated_by uuid NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);
