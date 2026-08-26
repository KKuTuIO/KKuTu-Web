import { describe, expect, it } from 'vitest';
import { configQuery, friendlyError } from './api.js';

describe('word academy API helpers', () => {
  it('projects only public rule query fields', () => {
    const query = configQuery({
      lang: 'ko',
      dictionary: 'COMBINED',
      direction: 'FORWARD',
      duum: true,
      minLength: 2,
      maxLength: 32,
      includeLoanword: false,
      includeSpaced: true,
      includeDialect: true,
      includeOld: true,
      includeCultural: true,
      includeKung: true,
      themes: ['KOT'],
      excludedThemes: ['TEST'],
      excludedWords: ['should-not-leak']
    });

    expect(query.lang).toBe('ko');
    expect(query.dictionary).toBe('COMBINED');
    expect(query.themes).toEqual(['KOT']);
    expect(query.excludedWords).toBeUndefined();
  });

  it('uses academy error codes for readable messages', () => {
    expect(friendlyError({ code: 'WORD_NOT_PUBLIC' })).toContain('현재 사전');
    expect(friendlyError({ name: 'TypeError', message: 'Failed to fetch' })).toBe('서버에 연결하지 못했습니다.');
    expect(friendlyError({ message: 'fallback' })).toBe('fallback');
  });
});
