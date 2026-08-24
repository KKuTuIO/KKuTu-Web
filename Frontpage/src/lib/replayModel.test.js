import {describe, expect, it} from 'vitest';
import {createReplayModel, formatReplayTime, getReplayState, getReplaySupport} from './replayModel.js';

describe('replay model', () => {
    it('rejects incomplete payloads without throwing', () => {
        expect(createReplayModel(null)).toBeNull();
        expect(createReplayModel({replayView: {payload: {}, players: []}})).toBeNull();
        expect(getReplayState(null, Number.NaN)).toBeNull();
    });

    it('classifies supported chain and wordstack modes', () => {
        expect(getReplaySupport(detail('KKT', 'Classic')).boardType).toBe('chain');
        expect(getReplaySupport(detail('KWS', 'Wordstack')).boardType).toBe('wordstack');
        expect(getReplaySupport(detail('UNKNOWN', 'Unknown'))).toEqual({supported: false, boardType: 'unavailable'});
    });

    it('normalizes hostile time values for display', () => {
        expect(formatReplayTime(-1)).toBe('0:00');
        expect(formatReplayTime(Number.POSITIVE_INFINITY)).toBe('0:00');
        expect(formatReplayTime(3_661_900)).toBe('1:01:01');
    });

    it('creates deterministic empty-game state for valid metadata', () => {
        const input = detail('KKT', 'Classic');
        input.replayView.players = [{id: 'p1', nickname: 'Player', index: 0}];
        input.replayView.payload.d = 1_000;

        const model = createReplayModel(input);
        const state = getReplayState(model, -100);

        expect(model.players).toHaveLength(1);
        expect(state.timeMs).toBe(0);
        expect(state.scores).toEqual([0]);
    });
});

function detail(mode, rule) {
    return {
        replayView: {
            players: [{id: 'p1'}],
            payload: {rm: [null, null, mode, rule], i: [], mv: []}
        }
    };
}
