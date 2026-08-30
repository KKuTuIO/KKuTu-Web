let worker;
let sequence = 0;
const pending = new Map();

function ensureWorker() {
  if (worker) return worker;
  if (typeof Worker === 'undefined') {
    throw Object.assign(new Error('이 브라우저에서는 학습 엔진을 사용할 수 없습니다.'), { code: 'CLIENT_ENGINE_UNAVAILABLE' });
  }
  worker = new Worker(new URL('./academy.worker.js', import.meta.url), { type: 'module' });
  worker.onmessage = (event) => {
    const { id, ok, result, error } = event.data || {};
    const task = pending.get(id);
    if (!task) return;
    pending.delete(id);
    if (ok) task.resolve(result);
    else task.reject(Object.assign(new Error(error?.message || '브라우저 계산 중 오류가 발생했습니다.'), { code: error?.code || 'CLIENT_ENGINE_FAILED' }));
  };
  worker.onerror = () => {
    for (const task of pending.values()) task.reject(Object.assign(new Error('학습 엔진이 중단되었습니다.'), { code: 'CLIENT_ENGINE_FAILED' }));
    pending.clear();
    worker?.terminate();
    worker = null;
  };
  return worker;
}

function call(operation, payload) {
  const active = ensureWorker();
  const id = ++sequence;
  return new Promise((resolve, reject) => {
    pending.set(id, { resolve, reject });
    active.postMessage({ id, operation, payload });
  });
}

export const academyClient = {
  preload(config) { return call('preload', { config }); },
  simulate(config, chain, word, shields = 0, botLevel = null, specialRule = 'NONE') {
    return call('simulate', { config, chain, word, shields, botLevel, specialRule });
  },
  practice(config, difficulty, startChar = null, usedWords = []) {
    return call('practice', { config, difficulty, startChar, usedWords });
  },
  practiceAnswer(config, requiredChar, usedWords, word, shields = 0) {
    return call('practiceAnswer', { config, requiredChar, usedWords, word, shields });
  },
  analyze(config, options = {}) {
    return call('analyze', { config, ...options });
  },
  compare(base, compared) {
    return call('compare', { base, compared });
  },
  strategy(config, startChar, usedWords = [], depth = 10) {
    return call('strategy', { config, startChar, usedWords, depth });
  }
};
