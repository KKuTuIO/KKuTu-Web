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
    const query = queryString({ dictionary: config.dictionary, direction: config.direction, duum: config.duum });
    return request(`/api/academy/word/${encodeURIComponent(config.lang)}/${encodeURIComponent(word)}?${query}`);
  },
  restricted(lang, position, char, mission) {
    return request('/api/academy/restricted/search', {
      method: 'POST',
      body: JSON.stringify({
        lang,
        startChar: position === 'START' ? char : null,
        endChar: position === 'END' ? char : null,
        mission: mission || null
      })
    });
  },
  replay(gameId) {
    return request(`/api/academy/replay/v1/${encodeURIComponent(gameId)}`);
  },
  adminPublished(lang, page = 0, size = 50) {
    return request(`/api/admin/academy/public/${encodeURIComponent(lang)}?${queryString({ page, size })}`);
  },
  adminPublish(lang, word, reason = '관리자 공개') {
    return request(`/api/admin/academy/public/${encodeURIComponent(lang)}/${encodeURIComponent(word)}`, {
      method: 'PUT', body: JSON.stringify({ reason })
    });
  },
  adminBulkPublish(lang, words, reason = '관리자 일괄 공개') {
    return request(`/api/admin/academy/public/${encodeURIComponent(lang)}/bulk`, {
      method: 'POST', body: JSON.stringify({ words, reason })
    });
  },
  adminUnpublish(lang, word) {
    return request(`/api/admin/academy/public/${encodeURIComponent(lang)}/${encodeURIComponent(word)}`, { method: 'DELETE' });
  },
  adminRefresh(lang = null) {
    const query = queryString({ lang });
    return request(`/api/admin/academy/refresh${query ? `?${query}` : ''}`, { method: 'POST' });
  }
};

export function friendlyError(error) {
  if (error?.name === 'TypeError' && /fetch/i.test(error?.message || '')) return '서버에 연결하지 못했습니다.';
  const byCode = {
    RATE_LIMITED: '요청이 너무 빠릅니다. 잠시 쉬었다가 다시 시도해 주세요.',
    WORD_NOT_PUBLIC: '현재 사전에서 확인할 수 없는 단어입니다.',
    WORD_TOKEN_REQUIRED: '단어 토큰이 부족합니다.',
    RESTRICTED_LIMIT: '오늘의 어인정 조회 횟수를 모두 사용했습니다.',
    LOGIN_REQUIRED: '로그인 후 이용할 수 있습니다.',
    NO_CHALLENGE: '현재 규칙으로 만들 수 있는 연습 문제가 없습니다.',
    FIRST_MOVE_FINISH: '첫 수에는 바로 끝나는 단어를 사용할 수 없습니다.',
    MANNER_BLOCKED: '매너 규칙에서는 받을 단어가 없는 수를 사용할 수 없습니다.',
    SAFE_BLOCKED: '안전 규칙에서는 남은 응수가 없는 수를 사용할 수 없습니다.',
    SAFE_WORD_BLOCKED: '안전 규칙에서 사용할 수 없는 단어입니다.',
    GENTLE_BLOCKED: '젠틀 규칙에서는 상대에게 최소 5개의 응수를 남겨야 합니다.',
    CORPUS_UNAVAILABLE: '학습 사전 파일을 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.',
    CLIENT_ENGINE_UNAVAILABLE: '이 브라우저에서는 학습 엔진을 사용할 수 없습니다.',
    HTTP_401: '관리자 로그인이 필요합니다.',
    HTTP_403: '단어 관리 권한이 필요합니다.'
  };
  return byCode[error?.code] || error?.message || '알 수 없는 오류가 발생했습니다.';
}
