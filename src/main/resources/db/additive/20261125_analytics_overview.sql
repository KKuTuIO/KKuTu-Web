-- 백오피스 홈의 운영 지표. 새 테이블과 인덱스만 만든다. 기존 테이블은 바꾸지 않는다.
-- 날짜는 모두 한국 시간(Asia/Seoul) 달력 날짜다.

-- 하루 한 행. peak_ccu 는 접속자 표본이, 나머지는 접속 기록 집계가 채운다. updated_at 은 마지막 접속 기록 집계 시각이다.
CREATE TABLE IF NOT EXISTS analytics_daily_metric (
    stat_date date PRIMARY KEY,
    dau integer NOT NULL DEFAULT 0,
    mau integer NOT NULL DEFAULT 0,
    new_users integer NOT NULL DEFAULT 0,
    returning_users integer NOT NULL DEFAULT 0,
    peak_ccu integer NOT NULL DEFAULT 0,
    updated_at timestamptz NOT NULL DEFAULT now()
);

-- 핑 유입(INFLOW)과 소각(SINK)의 날짜·출처별 합계. total_amount 는 방향과 상관없이 양수다.
CREATE TABLE IF NOT EXISTS economy_daily_stat (
    stat_date date NOT NULL,
    direction varchar(16) NOT NULL,
    source varchar(64) NOT NULL,
    total_amount bigint NOT NULL DEFAULT 0,
    tx_count bigint NOT NULL DEFAULT 0,
    updated_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (stat_date, direction, source)
);

CREATE INDEX IF NOT EXISTS economy_daily_stat_date_idx ON economy_daily_stat (stat_date DESC);
