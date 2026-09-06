import { afterEach, describe, expect, it, vi } from 'vitest';
import { academyApi, friendlyError } from './api.js';

describe('word academy API helpers', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('loads replay detail from the canonical replay API', async () => {
    const fetch = vi.fn().mockResolvedValue({
      ok: true,
      headers: { get: () => 'application/json' },
      json: async () => ({ ok: true, game: { gameId: 'game/id' } })
    });
    vi.stubGlobal('fetch', fetch);

    await academyApi.replay('game/id');

    expect(fetch.mock.calls[0][0]).toBe('/api/replay/game/game%2Fid?includeDetail=true');
  });

  it('uses academy error codes for readable messages', () => {
    expect(friendlyError({ code: 'WORD_NOT_PUBLIC' })).toContain('현재 사전');
    expect(friendlyError({ name: 'TypeError', message: 'Failed to fetch' })).toBe('서버에 연결하지 못했습니다.');
    expect(friendlyError({ message: 'fallback' })).toBe('fallback');
  });
});
