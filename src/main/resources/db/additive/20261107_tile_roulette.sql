-- 타일 룰렛의 추첨 기록. 확률 표시와 실제 결과가 맞는지 확인하고, 문제가 생겼을 때 되짚는 데 쓴다.
-- 게이지(천장)는 계정 flag(`tileGauge:{eventId}`)에 있고, 이 표는 읽기 전용 기록이다.
CREATE TABLE IF NOT EXISTS game_event_tile_draw (
    id BIGSERIAL PRIMARY KEY,
    user_id TEXT NOT NULL,
    event_id TEXT NOT NULL,
    cell_index SMALLINT NOT NULL,
    reward JSONB NOT NULL,
    owned BOOLEAN NOT NULL DEFAULT FALSE,
    gauge_after INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT game_event_tile_draw_cell CHECK (cell_index BETWEEN 0 AND 24),
    CONSTRAINT game_event_tile_draw_reward_object CHECK (jsonb_typeof(reward) = 'object')
);

CREATE INDEX IF NOT EXISTS game_event_tile_draw_event_idx
    ON game_event_tile_draw (event_id, created_at DESC);
CREATE INDEX IF NOT EXISTS game_event_tile_draw_user_idx
    ON game_event_tile_draw (user_id, event_id, created_at DESC);
