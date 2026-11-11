import { t } from '../i18n.svelte.js';

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

export const academyApi = {
  meta: () => request('/api/academy/meta'),
  injeong(lang, criterion, value, mission) {
    return request('/api/academy/injeong/search', {
      method: 'POST',
      body: JSON.stringify({
        lang,
        startChar: criterion === 'START' ? value : null,
        endChar: criterion === 'END' ? value : null,
        theme: criterion === 'THEME' ? value : null,
        word: criterion === 'WORD' ? value : null,
        mission: criterion === 'WORD' ? null : mission || null
      })
    });
  },
  replay(gameId) {
    return request(`/api/replay/game/${encodeURIComponent(gameId)}?includeDetail=true`);
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

/** Readable text for a failed request: by error code, from the locale (the server's own message is the fallback). */
export function friendlyError(error) {
  if (error?.name === 'TypeError' && /fetch/i.test(error?.message || '')) return t('play.academy.error.offline');
  const key = `play.academy.error.${error?.code}`;
  const text = error?.code ? t(key) : key;
  if (error?.code && text !== key) return text;
  return error?.message || t('play.academy.error.unknown');
}
