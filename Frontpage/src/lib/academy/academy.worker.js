import { AcademyLocalEngine, decodeCorpusPack } from './academy-engine-core.js';

const loaded = new Map();
const manifestCheckedAt = new Map();
const MANIFEST_RECHECK_MS = 5 * 60 * 1000;

async function fetchManifest(lang, force = false) {
  const now = Date.now();
  const cached = loaded.get(lang);
  if (!force && cached?.manifest && now - (manifestCheckedAt.get(lang) || 0) < MANIFEST_RECHECK_MS) {
    return cached.manifest;
  }
  const response = await fetch(`/api/academy/corpus/manifest/${encodeURIComponent(lang)}`, {
    cache: force ? 'reload' : 'default',
    credentials: 'same-origin'
  });
  if (!response.ok) throw Object.assign(new Error('학습 사전을 불러오지 못했습니다.'), { code: 'CORPUS_UNAVAILABLE' });
  const manifest = await response.json();
  manifestCheckedAt.set(lang, now);
  return manifest;
}

async function loadEngine(lang) {
  const normalized = lang === 'en' ? 'en' : 'ko';
  let manifest = await fetchManifest(normalized);
  const current = loaded.get(normalized);
  if (current?.manifest?.version === manifest.version && current.engine) return current.engine;

  let response = await fetch(manifest.url, { cache: 'force-cache', credentials: 'same-origin' });
  if (response.status === 404) {
    manifest = await fetchManifest(normalized, true);
    response = await fetch(manifest.url, { cache: 'reload', credentials: 'same-origin' });
  }
  if (!response.ok) throw Object.assign(new Error('학습 사전 파일을 불러오지 못했습니다.'), { code: 'CORPUS_UNAVAILABLE' });
  const pack = decodeCorpusPack(await response.arrayBuffer());
  const engine = new AcademyLocalEngine(pack);
  loaded.set(normalized, { manifest, engine });
  return engine;
}

self.onmessage = async (event) => {
  const { id, operation, payload = {} } = event.data || {};
  try {
    const lang = payload.config?.lang || payload.base?.lang || 'ko';
    const engine = await loadEngine(lang);
    let result;
    switch (operation) {
      case 'simulate': result = engine.simulate(payload); break;
      case 'practice': result = engine.practice(payload); break;
      case 'practiceAnswer': result = engine.practiceAnswer(payload); break;
      case 'analyze': result = engine.analyze(payload); break;
      case 'compare': result = engine.compare(payload); break;
      case 'strategy': result = engine.strategy(payload); break;
      case 'preload': result = { ready: true }; break;
      default: throw Object.assign(new Error('지원하지 않는 계산 요청입니다.'), { code: 'CLIENT_ENGINE_OPERATION' });
    }
    self.postMessage({ id, ok: true, result });
  } catch (error) {
    self.postMessage({
      id,
      ok: false,
      error: { code: error?.code || 'CLIENT_ENGINE_FAILED', message: error?.message || '브라우저 계산 중 오류가 발생했습니다.' }
    });
  }
};
