import { describe, expect, it } from 'vitest';
import { AcademyLocalEngine, dueumTransform } from './academy-engine-core.js';

function corpusWord(word, { flags = 0, popularity = 10, themes = [] } = {}) {
  return {
    word,
    flags,
    popularity,
    publishedOverride: false,
    themes: new Set(themes)
  };
}

function engine(words) {
  return new AcademyLocalEngine({ schema: 1, lang: 'ko', words });
}

const config = {
  lang: 'ko',
  dictionary: 'COMBINED',
  direction: 'FORWARD',
  duum: true,
  minLength: 2,
  maxLength: 64,
  includeLoanword: true,
  includeSpaced: true,
  includeDialect: true,
  includeOld: true,
  includeCultural: true,
  includeKung: true,
  themes: [],
  excludedThemes: [],
  excludedWords: []
};

describe('Academy client engine dueum', () => {
  it('matches the live game conversion for 리→이 and 라→나', () => {
    expect(dueumTransform('리')).toBe('이');
    expect(dueumTransform('라')).toBe('나');
  });

  it('accepts transformed dueum answers in route practice', () => {
    const local = engine([
      corpusWord('이동'),
      corpusWord('나비'),
      corpusWord('동물'),
      corpusWord('비행')
    ]);

    expect(local.practiceAnswer({
      config,
      requiredChar: '리',
      usedWords: [],
      word: '이동',
      shields: 0
    }).accepted).toBe(true);

    expect(local.practiceAnswer({
      config,
      requiredChar: '라',
      usedWords: [],
      word: '나비',
      shields: 0
    }).accepted).toBe(true);
  });
});

describe('Academy client engine special rules', () => {
  it('blocks a static one-shot word under manner', () => {
    const local = engine([
      corpusWord('아가'),
      corpusWord('가끝')
    ]);
    const result = local.simulate({
      config,
      chain: ['아가'],
      word: '가끝',
      specialRule: 'MANNER'
    });
    expect(result.accepted).toBe(false);
    expect(result.code).toBe('MANNER_BLOCKED');
  });

  it('uses remaining stack size for gentle instead of querying the server', () => {
    const local = engine([
      corpusWord('아가'),
      corpusWord('가라'),
      corpusWord('라면'),
      corpusWord('라디오'),
      corpusWord('라켓'),
      corpusWord('라임')
    ]);
    const result = local.simulate({
      config,
      chain: ['아가'],
      word: '가라',
      specialRule: 'GENTLE'
    });
    expect(result.accepted).toBe(false);
    expect(result.code).toBe('GENTLE_BLOCKED');
  });

  it('blocks SBW words under safe', () => {
    const local = engine([
      corpusWord('아가'),
      corpusWord('가방', { themes: ['SBW'] }),
      corpusWord('방울')
    ]);
    const result = local.simulate({
      config,
      chain: ['아가'],
      word: '가방',
      specialRule: 'SAFE'
    });
    expect(result.accepted).toBe(false);
    expect(result.code).toBe('SAFE_WORD_BLOCKED');
  });
});
