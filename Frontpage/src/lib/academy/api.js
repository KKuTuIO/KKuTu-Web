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

function cleanAcademyText(value) {
  return String(value || '')
    .replaceAll('표준 공개 사전', '표준 사전')
    .replaceAll('공개 학습 사전', '사전')
    .replaceAll('공개 단어장', '사전')
    .replaceAll('공개 사전', '사전');
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

  simulator(config, chain, word, shields = 0, botLevel = null) {
    return request('/api/academy/simulator/step', {
      method: 'POST',
      body: JSON.stringify({ config, chain, word, shields, botLevel })
    }).then((response) => ({ ...response, message: cleanAcademyText(response?.message) }));
  },

  practice(config, difficulty, startChar = null, usedWords = []) {
    return request('/api/academy/practice/challenge', {
      method: 'POST',
      body: JSON.stringify({ config, difficulty, startChar, usedWords })
    });
  },

  practiceAnswer(config, requiredChar, usedWords, word, shields = 0) {
    return request('/api/academy/practice/answer', {
      method: 'POST',
      body: JSON.stringify({ config, requiredChar, usedWords, word, shields })
    });
  },

  quiz(index) {
    return request(`/api/academy/quiz/daily?index=${encodeURIComponent(index)}`).then((response) => ({
      ...response,
      prompt: cleanAcademyText(response?.prompt),
      explanationHint: cleanAcademyText(response?.explanationHint)
    }));
  },

  answerQuiz(questionId, answer) {
    return request('/api/academy/quiz/answer', {
      method: 'POST',
      body: JSON.stringify({ questionId, answer })
    }).then((response) => ({ ...response, explanation: cleanAcademyText(response?.explanation) }));
  },

  restricted(lang, startChar, mission) {
    return request('/api/academy/restricted/search', {
      method: 'POST',
      body: JSON.stringify({ lang, startChar, mission: mission || null })
    });
  },

  replay(gameId) {
    return request(`/api/replay/game/${encodeURIComponent(gameId)}?includeDetail=true`);
  },

  adminPublished(lang, page = 0, size = 50) {
    const query = queryString({ page, size });
    return request(`/api/admin/academy/public/${encodeURIComponent(lang)}?${query}`);
  },

  adminPublish(lang, word, reason = '관리자 공개') {
    return request(`/api/admin/academy/public/${encodeURIComponent(lang)}/${encodeURIComponent(word)}`, {
      method: 'PUT',
      body: JSON.stringify({ reason })
    });
  },

  adminBulkPublish(lang, words, reason = '관리자 일괄 공개') {
    return request(`/api/admin/academy/public/${encodeURIComponent(lang)}/bulk`, {
      method: 'POST',
      body: JSON.stringify({ words, reason })
    });
  },

  adminUnpublish(lang, word) {
    return request(`/api/admin/academy/public/${encodeURIComponent(lang)}/${encodeURIComponent(word)}`, {
      method: 'DELETE'
    });
  },

  adminRefresh(lang = null) {
    const query = queryString({ lang });
    return request(`/api/admin/academy/refresh${query ? `?${query}` : ''}`, {
      method: 'POST'
    });
  }
};

export function friendlyError(error) {
  if (error?.name === 'TypeError' && /fetch/i.test(error?.message || '')) {
    return '서버에 연결하지 못했습니다.';
  }

  const byCode = {
    RATE_LIMITED: '요청이 너무 빠릅니다. 잠시 쉬었다가 다시 시도해 주세요.',
    WORD_NOT_PUBLIC: '현재 사전에서 확인할 수 없는 단어입니다.',
    WORD_TOKEN_REQUIRED: '단어 토큰이 부족합니다.',
    RESTRICTED_LIMIT: '오늘의 어인정 조회 횟수를 모두 사용했습니다.',
    LOGIN_REQUIRED: '로그인 후 이용할 수 있습니다.',
    NO_CHALLENGE: '현재 규칙으로 만들 수 있는 연습 문제가 없습니다.',
    HTTP_401: '관리자 로그인이 필요합니다.',
    HTTP_403: '단어 관리 권한이 필요합니다.'
  };
  return byCode[error?.code] || cleanAcademyText(error?.message) || '알 수 없는 오류가 발생했습니다.';
}
