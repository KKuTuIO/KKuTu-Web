import { describe, expect, it } from 'vitest';
import { friendlyError } from './api.js';

describe('word academy API helpers', () => {
  it('uses academy error codes for readable messages', () => {
    expect(friendlyError({ code: 'WORD_NOT_PUBLIC' })).toContain('현재 사전');
    expect(friendlyError({ name: 'TypeError', message: 'Failed to fetch' })).toBe('서버에 연결하지 못했습니다.');
    expect(friendlyError({ message: 'fallback' })).toBe('fallback');
  });
});
