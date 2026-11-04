import { describe, expect, it, vi } from 'vitest';
import { errorKey, looksValid, submitNickname } from './setup.js';

describe('setup', () => {
	it('pre-checks length and surrounding spaces only', () => {
		expect(looksValid('a')).toBe(false);
		expect(looksValid('ab')).toBe(true);
		expect(looksValid('a'.repeat(17))).toBe(false);
		expect(looksValid(' ab')).toBe(false);
	});
	it('maps server codes to locale keys', () => {
		expect(errorKey('602')).toBe('play.setup.error.602');
		expect(errorKey('620')).toBe('play.setup.error.620');
		expect(errorKey('weird')).toBe('play.setup.error.generic');
	});
	it('submits and reports success, server errors and network failures', async () => {
		const ok = vi.fn().mockResolvedValue({ json: async () => ({ success: true, resultCode: 'SUCCESS' }) });
		expect(await submitNickname('곰', ok)).toEqual({ ok: true });
		expect(ok).toHaveBeenCalledWith('/api/setup/nick', expect.objectContaining({ method: 'POST', body: '{"nick":"곰"}' }));
		const bad = vi.fn().mockResolvedValue({ json: async () => ({ success: false, resultCode: '620' }) });
		expect(await submitNickname('곰', bad)).toEqual({ ok: false, key: 'play.setup.error.620' });
		expect(await submitNickname('곰', vi.fn().mockRejectedValue(new Error('x')))).toEqual({ ok: false, key: 'play.setup.error.generic' });
	});
});
