import {beforeEach, describe, expect, it, vi} from 'vitest';

describe('session request cache', () => {
    beforeEach(() => {
        vi.resetModules();
        vi.restoreAllMocks();
    });

    it('coalesces concurrent authentication requests', async () => {
        const fetchMock = vi.fn().mockResolvedValue(response({id: 'profile'}));
        vi.stubGlobal('fetch', fetchMock);
        const {loadAuth} = await import('./session.js');

        const first = loadAuth();
        const second = loadAuth();

        expect(first).toBe(second);
        await expect(first).resolves.toEqual({id: 'profile'});
        expect(fetchMock).toHaveBeenCalledTimes(1);
    });

    it('does not permanently cache infrastructure failures', async () => {
        const fetchMock = vi.fn()
            .mockResolvedValueOnce(response({}, 503))
            .mockResolvedValueOnce(response({id: 'recovered'}));
        vi.stubGlobal('fetch', fetchMock);
        const {loadAuth} = await import('./session.js');

        await expect(loadAuth()).rejects.toThrow('503');
        await expect(loadAuth()).resolves.toEqual({id: 'recovered'});
        expect(fetchMock).toHaveBeenCalledTimes(2);
    });

    it('encodes profile identifiers before constructing request paths', async () => {
        const fetchMock = vi.fn().mockResolvedValue(response({}));
        vi.stubGlobal('fetch', fetchMock);
        const {loadUser} = await import('./session.js');

        await loadUser('../account?admin=true');

        expect(fetchMock).toHaveBeenCalledWith('/user/..%2Faccount%3Fadmin%3Dtrue', undefined);
    });

    it('always bypasses browser cache for moderation status', async () => {
        const fetchMock = vi.fn().mockResolvedValue(response({blocked: false}));
        vi.stubGlobal('fetch', fetchMock);
        const {loadBlock} = await import('./session.js');

        await loadBlock();

        expect(fetchMock).toHaveBeenCalledWith('/api/block', {cache: 'no-store'});
    });
});

function response(body, status = 200) {
    return {
        ok: status >= 200 && status < 300,
        status,
        json: vi.fn().mockResolvedValue(body)
    };
}
