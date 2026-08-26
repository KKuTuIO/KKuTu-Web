async function request(path, options = {}) {
  const response = await fetch(path, {
    credentials: 'same-origin',
    ...options,
    headers: {
      ...(options.body ? { 'Content-Type': 'application/json' } : {}),
      ...(options.headers || {})
    }
  });
  const type = response.headers.get('content-type') || '';
  const payload = type.includes('application/json') ? await response.json() : null;
  if (!response.ok) {
    const error = new Error(payload?.message || `요청을 처리하지 못했습니다. (${response.status})`);
    error.code = payload?.code || `HTTP_${response.status}`;
    error.status = response.status;
    throw error;
  }
  return payload;
}

function queryString(values) {
  const query = new URLSearchParams();
  Object.entries(values).forEach(([key, value]) => {
    if (value === undefined || value === null || value === '') return;
    if (Array.isArray(value)) {
      if (value.length) query.set(key, value.join(','));
      return;
    }
    query.set(key, String(value));
  });
  return query.toString();
}

export function configQuery(config) {
  return {
    lang: config.lang,
    dictionary: config.dictionary,
    direction: config.direction,
    duum: config.duum,
    minLength: config.minLength,
    maxLength: config.maxLength,
    includeLoanword: config.includeLoanword,
    includeSpaced: config.includeSpaced,
    includeDialect: config.includeDialect,
    includeOld: config.includeOld,
    includeCultural: config.includeCultural,
    includeKung: config.includeKung,
    themes: config.themes,
    excludedThemes: config.excludedThemes
  };
}

export const academyApi = {
  meta: () => request('/api/academy/meta'),

  search(config, filters) {
    const query = queryString({ ...configQuery(config), ...filters });
    return request(`/api/academy/search?${query}`);
  },

  word(config, word) {
    const query = queryString({
      dictionary: config.dictionary,
      direction: config.direction,
      duum: config.duum
    });
    return request(`/api/academy/word/${encodeURIComponent(config.lang)}/${encodeURIComponent(word)}?${query}`);
  },

  analyze(config, options = {}) {
    return request('/api/academy/analyze', {
      method: 'POST',
      body: JSON.stringify({ config, ...options })
    });
  },

  compare(base, compared) {
    return request('/api/academy/compare', {
      method: 'POST',
      body: JSON.stringify({ base, compared })
    });
  },

  strategy(config, startChar, usedWords = [], depth = 10) {
    return request('/api/academy/strategy', {
      method: 'POST',
      body: JSON.stringify({ config, startChar, usedWords, depth })
    });
  },

  simulator(config, chain, word, shields = 0, botLevel = null, requiredChar = null) {
    return request('/api/academy/simulator/step', {
      method: 'POST',
      body: JSON.stringify({ config, chain, word, shields, botLevel, requiredChar })
    });
  },

  practice(config, difficulty, startChar = null, usedWords = []) {
    return request('/api/academy/practice/challenge', {
      method: 'POST',
      body: JSON.stringify({ config, difficulty, startChar, usedWords })
    });
  },

  quiz(index) {
    return request(`/api/academy/quiz/daily?index=${encodeURIComponent(index)}`);
  },

  answerQuiz(questionId, answer) {
    return request('/api/academy/quiz/answer', {
      method: 'POST',
      body: JSON.stringify({ questionId, answer })
    });
  },

  restricted(lang, startChar, mission) {
    return request('/api/academy/restricted/search', {
      method: 'POST',
      body: JSON.stringify({ lang, startChar, mission: mission || null })
    });
  },

  replay(gameId) {
    return request(`/api/replay/game/${encodeURIComponent(gameId)}?includeDetail=true`);
  }
};

export function friendlyError(error) {
  const byCode = {
    RATE_LIMITED: '요청이 너무 빠릅니다. 잠시 쉬었다가 다시 시도해 주세요.',
    WORD_NOT_PUBLIC: '공개 학습 사전에서 확인할 수 없는 단어입니다.',
    WORD_TOKEN_REQUIRED: '단어 토큰이 부족합니다.',
    RESTRICTED_LIMIT: '오늘의 어인정 제한 조회 횟수를 모두 사용했습니다.',
    LOGIN_REQUIRED: '로그인 후 이용할 수 있습니다.',
    NO_CHALLENGE: '현재 규칙으로 만들 수 있는 연습 문제가 없습니다.'
  };
  return byCode[error?.code] || error?.message || '알 수 없는 오류가 발생했습니다.';
}
