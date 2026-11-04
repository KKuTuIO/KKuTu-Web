import { describe, expect, it } from 'vitest';
import { toWorkerPayload } from './academyClient.js';

describe('academy worker payload', () => {
  it('removes reactive proxies before calling postMessage', () => {
    const config = new Proxy({
      lang: 'ko',
      themes: new Proxy(['food'], {})
    }, {});

    expect(() => structuredClone(config)).toThrow();

    const payload = toWorkerPayload({ config });
    expect(() => structuredClone(payload)).not.toThrow();
    expect(payload).toEqual({ config: { lang: 'ko', themes: ['food'] } });
  });
});
