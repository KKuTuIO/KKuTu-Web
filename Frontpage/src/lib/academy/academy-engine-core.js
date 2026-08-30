const decoder = new TextDecoder();
const INJEONG = 2;

const RIEUL_TO_NIEUN = new Set([0x1161, 0x1162, 0x1169, 0x116c, 0x116e, 0x116f]);
const RIEUL_TO_IEUNG = new Set([0x1163, 0x1167, 0x1168, 0x116d, 0x1172, 0x1175]);
const NIEUN_TO_IEUNG = new Set([0x1167, 0x116d, 0x1172, 0x1175]);

export function dueumTransform(value) {
  const character = [...String(value || '')][0];
  if (!character) return null;
  const offset = character.codePointAt(0) - 0xac00;
  if (offset < 0 || offset > 11171) return null;
  const initial = Math.floor(offset / 588);
  const medial = Math.floor(offset / 28) % 21;
  const final = offset % 28;
  const medialJamo = 0x1161 + medial;
  let transformed = null;
  if (initial === 5) {
    if (RIEUL_TO_NIEUN.has(medialJamo)) transformed = 2;
    else if (RIEUL_TO_IEUNG.has(medialJamo)) transformed = 11;
  } else if (initial === 2 && NIEUN_TO_IEUNG.has(medialJamo)) {
    transformed = 11;
  }
  if (transformed === null) return null;
  return String.fromCharCode(((transformed * 21 + medial) * 28) + final + 0xac00);
}

export function acceptedSources(required, enabled = true, lang = 'ko') {
  if (!enabled || lang !== 'ko') return [required];
  const transformed = dueumTransform(required);
  return transformed && transformed !== required ? [required, transformed] : [required];
}

function readUtf8(view, bytes, cursor) {
  const length = view.getUint16(cursor.offset, false);
  cursor.offset += 2;
  const result = decoder.decode(bytes.subarray(cursor.offset, cursor.offset + length));
  cursor.offset += length;
  return result;
}

export function decodeCorpusPack(buffer) {
  const bytes = new Uint8Array(buffer);
  const view = new DataView(buffer);
  if (bytes.length < 10 || String.fromCharCode(...bytes.subarray(0, 4)) !== 'KWDB') {
    throw Object.assign(new Error('사전 파일 형식이 올바르지 않습니다.'), { code: 'CORPUS_INVALID' });
  }
  const schema = view.getUint8(4);
  if (schema !== 1) throw Object.assign(new Error('지원하지 않는 사전 파일 버전입니다.'), { code: 'CORPUS_SCHEMA' });
  const lang = view.getUint8(5) === 0 ? 'ko' : 'en';
  const count = view.getUint32(6, false);
  const cursor = { offset: 10 };
  const words = new Array(count);
  for (let i = 0; i < count; i += 1) {
    const word = readUtf8(view, bytes, cursor);
    const flags = view.getUint16(cursor.offset, false); cursor.offset += 2;
    const popularity = view.getUint8(cursor.offset); cursor.offset += 1;
    const publishedOverride = view.getUint8(cursor.offset) === 1; cursor.offset += 1;
    const theme = readUtf8(view, bytes, cursor);
    words[i] = {
      word,
      flags,
      popularity,
      publishedOverride,
      themes: new Set(theme.split(',').filter((item) => item && item !== '0'))
    };
  }
  return { schema, lang, words };
}

function normalizeConfig(raw = {}) {
  const lang = raw.lang === 'en' ? 'en' : 'ko';
  const minLength = Math.max(1, Math.min(64, Number(raw.minLength) || 2));
  const maxLength = Math.max(minLength, Math.min(128, Number(raw.maxLength) || 64));
  return {
    lang,
    dictionary: ['BASIC', 'STANDARD', 'COMBINED'].includes(raw.dictionary) ? raw.dictionary : 'COMBINED',
    direction: raw.direction === 'REVERSE' ? 'REVERSE' : 'FORWARD',
    duum: lang === 'ko' && raw.duum !== false,
    minLength,
    maxLength,
    includeLoanword: raw.includeLoanword !== false,
    includeSpaced: raw.includeSpaced !== false,
    includeDialect: raw.includeDialect !== false,
    includeOld: raw.includeOld !== false,
    includeCultural: raw.includeCultural !== false,
    includeKung: raw.includeKung !== false,
    themes: [...new Set(raw.themes || [])].filter(Boolean).sort(),
    excludedThemes: [...new Set(raw.excludedThemes || [])].filter(Boolean).sort(),
    excludedWords: [...new Set(raw.excludedWords || [])].filter(Boolean).sort().slice(0, 1000)
  };
}

function viewKey(config) {
  const { excludedWords, ...base } = config;
  return JSON.stringify(base);
}

function graphKey(config) {
  return JSON.stringify(config);
}

function pushIndex(map, key, word) {
  const list = map.get(key);
  if (list) list.push(word);
  else map.set(key, [word]);
}

function cachePut(map, key, value, max = 4) {
  if (map.has(key)) map.delete(key);
  map.set(key, value);
  while (map.size > max) map.delete(map.keys().next().value);
  return value;
}

class CorpusView {
  constructor(pack, rawConfig) {
    this.config = normalizeConfig(rawConfig);
    const requiredThemes = new Set(this.config.themes);
    const excludedThemes = new Set(this.config.excludedThemes);
    const excludedWords = new Set(this.config.excludedWords);
    this.words = pack.words.filter((word) => {
      if (excludedWords.has(word.word)) return false;
      if (this.config.dictionary === 'BASIC' && word.flags !== 0) return false;
      if (this.config.dictionary === 'STANDARD' && (word.flags & INJEONG)) return false;
      if (word.word.length < this.config.minLength || word.word.length > this.config.maxLength) return false;
      if (!this.config.includeLoanword && (word.flags & 1)) return false;
      if (!this.config.includeSpaced && (word.flags & 4)) return false;
      if (!this.config.includeDialect && (word.flags & 8)) return false;
      if (!this.config.includeOld && (word.flags & 16)) return false;
      if (!this.config.includeCultural && (word.flags & 32)) return false;
      if (!this.config.includeKung && (word.flags & 64)) return false;
      if (requiredThemes.size && ![...word.themes].some((theme) => requiredThemes.has(theme))) return false;
      if (excludedThemes.size && [...word.themes].some((theme) => excludedThemes.has(theme))) return false;
      return true;
    });
    this.byId = new Map();
    this.byStart = new Map();
    this.byEnd = new Map();
    for (const word of this.words) {
      this.byId.set(word.word, word);
      pushIndex(this.byStart, word.word[0] || '', word);
      pushIndex(this.byEnd, word.word.at(-1) || '', word);
    }
    this.sourceIndex = this.config.direction === 'REVERSE' ? this.byEnd : this.byStart;
    this.rawBaseline = new Map([...this.sourceIndex.entries()].map(([char, words]) => [char, words.length]));
  }

  source(word) { return this.config.direction === 'REVERSE' ? word.word.at(-1) : word.word[0]; }
  destination(word) { return this.config.direction === 'REVERSE' ? word.word[0] : word.word.at(-1); }

  connects(required, word) {
    return acceptedSources(required, this.config.duum, this.config.lang).includes(this.source(word));
  }

  connectionWords(required) {
    if (!required) return [];
    const result = [];
    for (const source of acceptedSources(required, this.config.duum, this.config.lang)) {
      const words = this.sourceIndex.get(source);
      if (words) result.push(...words);
    }
    return result;
  }

  staticCount(required) {
    let count = 0;
    for (const source of acceptedSources(required, this.config.duum, this.config.lang)) {
      count += this.rawBaseline.get(source) || 0;
    }
    return count;
  }

  stackConsidered(word) {
    if ((word.flags & INJEONG) && !word.themes.has('hbw')) return false;
    const source = this.source(word);
    const destination = this.destination(word);
    const sub = this.config.duum ? dueumTransform(destination) : null;
    return source !== destination && source !== sub;
  }

  chainState(chain = []) {
    const raw = new Map(this.rawBaseline);
    const used = new Set();
    for (const text of chain) {
      if (used.has(text)) continue;
      const word = this.byId.get(text);
      if (!word) continue;
      used.add(text);
      if (!this.stackConsidered(word)) continue;
      const destination = this.destination(word);
      raw.set(destination, Math.max(0, (raw.get(destination) || 0) - 1));
    }
    return { raw, used };
  }

  dynamicCount(required, state) {
    if (!required) return 0;
    let count = state.raw.get(required) || 0;
    if (this.config.duum && this.config.lang === 'ko') {
      const sub = dueumTransform(required);
      if (sub && sub !== required) count += state.raw.get(sub) || 0;
    }
    return count;
  }

  afterDefenseCount(word, state) {
    const destination = this.destination(word);
    const before = this.dynamicCount(destination, state);
    return Math.max(0, before - (this.stackConsidered(word) ? 1 : 0));
  }
}

function moveError(view, state, required, word, specialRule, firstMove) {
  if (!word) return ['WORD_NOT_PUBLIC', '현재 사전에서 확인할 수 없는 단어입니다.'];
  if (state.used.has(word.word)) return ['DUPLICATED_WORD', '이미 사용한 단어입니다.'];
  if (required && !view.connects(required, word)) return ['NOT_CHAINABLE', `‘${required}’에서 이어지는 단어가 아닙니다.`];
  const next = view.destination(word);
  const staticCount = view.staticCount(next);
  if (firstMove && staticCount === 0) return ['FIRST_MOVE_FINISH', '첫 수에는 바로 끝나는 단어를 사용할 수 없습니다.'];
  if (specialRule === 'MANNER' && staticCount === 0) return ['MANNER_BLOCKED', '매너 규칙에서는 받을 단어가 없는 수를 사용할 수 없습니다.'];
  if (specialRule === 'SAFE' && word.themes.has('SBW')) return ['SAFE_WORD_BLOCKED', '안전 규칙에서 사용할 수 없는 단어입니다.'];
  const dynamic = view.dynamicCount(next, state);
  if (specialRule === 'SAFE' && dynamic === 0) return ['SAFE_BLOCKED', '안전 규칙에서는 남은 응수가 없는 수를 사용할 수 없습니다.'];
  if (specialRule === 'GENTLE' && dynamic === 0) return ['GENTLE_BLOCKED', '젠틀 규칙에서는 상대가 받을 수 없는 수를 사용할 수 없습니다.'];
  if (specialRule === 'GENTLE' && dynamic < 5) return ['GENTLE_BLOCKED', '젠틀 규칙에서는 상대에게 최소 5개의 응수를 남겨야 합니다.'];
  return null;
}

function simpleMoveView(view, state, required, word) {
  const to = view.destination(word);
  const defenseCount = view.afterDefenseCount(word, state);
  return {
    word: word.word,
    from: required || view.source(word),
    to,
    resultingState: defenseCount === 0 ? 'LOSS' : 'ROUTE',
    resultingPly: null,
    defenseCount,
    hit: word.popularity
  };
}

function availableMoves(view, state, required, specialRule = 'NONE', firstMove = false) {
  return view.connectionWords(required)
    .filter((word) => !moveError(view, state, required, word, specialRule, firstMove))
    .map((word) => simpleMoveView(view, state, required, word));
}

function rankStackMoves(moves, level = 'EXPERT') {
  const list = [...moves];
  if (level === 'RANDOM') {
    for (let i = list.length - 1; i > 0; i -= 1) {
      const j = Math.floor(Math.random() * (i + 1));
      [list[i], list[j]] = [list[j], list[i]];
    }
    return list;
  }
  if (level === 'BALANCED') {
    return list.sort((a, b) => b.hit - a.hit || a.defenseCount - b.defenseCount || a.word.localeCompare(b.word));
  }
  return list.sort((a, b) => a.defenseCount - b.defenseCount || b.hit - a.hit || b.word.length - a.word.length || a.word.localeCompare(b.word));
}

function buildGraph(view) {
  const nodes = new Set();
  for (const word of view.words) {
    nodes.add(view.source(word));
    nodes.add(view.destination(word));
  }
  const adjacency = new Map();
  const targets = new Map();
  const reverse = new Map();
  for (const node of nodes) {
    const words = view.connectionWords(node);
    adjacency.set(node, words);
    const destinations = [...new Set(words.map((word) => view.destination(word)))];
    targets.set(node, destinations);
    for (const destination of destinations) {
      if (!reverse.has(destination)) reverse.set(destination, new Set());
      reverse.get(destination).add(node);
    }
  }

  const states = new Map();
  const remaining = new Map([...targets.entries()].map(([node, values]) => [node, values.length]));
  const maximumWinningChild = new Map();
  const queue = [];
  let head = 0;
  for (const node of nodes) {
    if ((targets.get(node) || []).length === 0) {
      states.set(node, { state: 'LOSS', ply: 0, representativeWord: null });
      queue.push(node);
    }
  }
  while (head < queue.length) {
    const node = queue[head++];
    const known = states.get(node);
    for (const predecessor of reverse.get(node) || []) {
      if (states.has(predecessor)) continue;
      if (known.state === 'LOSS') {
        const candidates = (adjacency.get(predecessor) || []).filter((word) => view.destination(word) === node);
        const representativeWord = candidates.sort((a, b) => b.popularity - a.popularity || b.word.length - a.word.length)[0]?.word || null;
        states.set(predecessor, { state: 'WIN', ply: (known.ply || 0) + 1, representativeWord });
        queue.push(predecessor);
      } else if (known.state === 'WIN') {
        remaining.set(predecessor, (remaining.get(predecessor) || 1) - 1);
        maximumWinningChild.set(predecessor, Math.max(maximumWinningChild.get(predecessor) || 0, known.ply || 0));
        if (remaining.get(predecessor) === 0) {
          states.set(predecessor, { state: 'LOSS', ply: (maximumWinningChild.get(predecessor) || 0) + 1, representativeWord: null });
          queue.push(predecessor);
        }
      }
    }
  }
  for (const node of nodes) if (!states.has(node)) states.set(node, { state: 'ROUTE', ply: null, representativeWord: null });

  for (const [node, state] of states) {
    if (state.representativeWord) continue;
    const words = adjacency.get(node) || [];
    let candidates;
    if (state.state === 'WIN') candidates = words.filter((word) => states.get(view.destination(word))?.state === 'LOSS')
      .sort((a, b) => (states.get(view.destination(a))?.ply ?? 999) - (states.get(view.destination(b))?.ply ?? 999) || b.popularity - a.popularity);
    else if (state.state === 'LOSS') candidates = words.filter((word) => states.get(view.destination(word))?.state === 'WIN')
      .sort((a, b) => (states.get(view.destination(b))?.ply ?? 0) - (states.get(view.destination(a))?.ply ?? 0) || b.popularity - a.popularity);
    else candidates = words.filter((word) => states.get(view.destination(word))?.state === 'ROUTE').sort((a, b) => b.popularity - a.popularity);
    state.representativeWord = candidates[0]?.word || null;
  }

  return { view, adjacency, states, routeGroups: routeComponents(view, adjacency, states), generatedAt: Date.now() };
}

function routeComponents(view, adjacency, states) {
  const routes = new Set([...states.entries()].filter(([, state]) => state.state === 'ROUTE').map(([node]) => node));
  if (!routes.size) return [];
  const edges = new Map();
  const reverse = new Map();
  for (const node of routes) {
    const destinations = [...new Set((adjacency.get(node) || []).map((word) => view.destination(word)).filter((dest) => routes.has(dest)))];
    edges.set(node, destinations);
    for (const destination of destinations) {
      if (!reverse.has(destination)) reverse.set(destination, []);
      reverse.get(destination).push(node);
    }
  }
  const visited = new Set();
  const order = [];
  for (const start of routes) {
    if (visited.has(start)) continue;
    const stack = [[start, false]];
    while (stack.length) {
      const [node, exiting] = stack.pop();
      if (exiting) { order.push(node); continue; }
      if (visited.has(node)) continue;
      visited.add(node);
      stack.push([node, true]);
      for (const next of edges.get(node) || []) if (!visited.has(next)) stack.push([next, false]);
    }
  }
  const assigned = new Set();
  const components = [];
  for (let i = order.length - 1; i >= 0; i -= 1) {
    const start = order[i];
    if (assigned.has(start)) continue;
    const component = new Set();
    const stack = [start];
    assigned.add(start);
    while (stack.length) {
      const node = stack.pop();
      component.add(node);
      for (const previous of reverse.get(node) || []) {
        if (!assigned.has(previous)) { assigned.add(previous); stack.push(previous); }
      }
    }
    components.push(component);
  }
  return components.sort((a, b) => b.size - a.size);
}

function graphMoveViews(graph, required, usedWords = new Set()) {
  const sourceState = graph.states.get(required)?.state;
  const view = graph.view;
  return (graph.adjacency.get(required) || [])
    .filter((word) => !usedWords.has(word.word))
    .map((word) => {
      const to = view.destination(word);
      const target = graph.states.get(to) || { state: 'LOSS', ply: 0 };
      const defenseCount = (graph.adjacency.get(to) || []).filter((candidate) => !usedWords.has(candidate.word)).length;
      return { word, to, target, defenseCount };
    })
    .sort((a, b) => {
      const priority = (item) => {
        if (sourceState === 'WIN') return item.target.state === 'LOSS' ? 0 : 1;
        if (sourceState === 'LOSS') return item.target.state === 'WIN' ? 0 : 1;
        if (sourceState === 'ROUTE') return item.target.state === 'ROUTE' ? 0 : 1;
        return 0;
      };
      const first = priority(a) - priority(b);
      if (first) return first;
      if (sourceState === 'WIN') {
        const ply = (a.target.ply ?? 999) - (b.target.ply ?? 999);
        if (ply) return ply;
      } else if (sourceState === 'LOSS') {
        const ply = (b.target.ply ?? 0) - (a.target.ply ?? 0);
        if (ply) return ply;
      } else if (a.defenseCount !== b.defenseCount) return a.defenseCount - b.defenseCount;
      return b.word.popularity - a.word.popularity || b.word.word.length - a.word.word.length || a.word.word.localeCompare(b.word.word);
    })
    .map(({ word, to, target, defenseCount }) => ({
      word: word.word,
      from: required,
      to,
      resultingState: target.state,
      resultingPly: target.ply,
      defenseCount,
      hit: word.popularity
    }));
}

function criticalWords(graph) {
  const result = [];
  for (const [source, state] of graph.states) {
    if (state.state !== 'WIN') continue;
    for (const word of graph.adjacency.get(source) || []) {
      const to = graph.view.destination(word);
      if (graph.states.get(to)?.state !== 'LOSS') continue;
      result.push({ word: word.word, from: source, to, defenseCount: (graph.adjacency.get(to) || []).length, ply: state.ply });
    }
  }
  const seen = new Set();
  return result.filter((item) => !seen.has(item.word) && seen.add(item.word))
    .sort((a, b) => (a.ply ?? 999) - (b.ply ?? 999) || a.defenseCount - b.defenseCount || a.word.localeCompare(b.word));
}

export class AcademyLocalEngine {
  constructor(pack) {
    this.pack = pack;
    this.viewCache = new Map();
    this.graphCache = new Map();
  }

  view(rawConfig) {
    const config = normalizeConfig(rawConfig);
    const canonical = { ...config, excludedWords: [] };
    if (config.excludedWords.length) return new CorpusView(this.pack, config);
    const key = viewKey(canonical);
    return this.viewCache.get(key) || cachePut(this.viewCache, key, new CorpusView(this.pack, canonical));
  }

  graph(rawConfig) {
    const config = normalizeConfig(rawConfig);
    const key = graphKey(config);
    return this.graphCache.get(key) || cachePut(this.graphCache, key, buildGraph(new CorpusView(this.pack, config)));
  }

  simulate({ config, chain = [], word: text, shields = 0, botLevel = null, specialRule = 'NONE' }) {
    const view = this.view(config);
    const safeChain = chain.map((item) => String(item || '').trim()).filter(Boolean).slice(0, 2000);
    const state = view.chainState(safeChain);
    const previous = view.byId.get(safeChain.at(-1));
    const required = previous ? view.destination(previous) : null;
    const candidate = view.byId.get(String(text || '').trim());
    const rule = ['MANNER', 'SAFE', 'GENTLE'].includes(specialRule) ? specialRule : 'NONE';
    const error = moveError(view, state, required, candidate, rule, safeChain.length === 0);
    if (error) return this.rejected(error[0], error[1], safeChain, required, shields);

    const source = required || view.source(candidate);
    const analysis = simpleMoveView(view, state, source, candidate);
    const newChain = [...safeChain, candidate.word];
    const afterState = view.chainState(newChain);
    const alternatives = rankStackMoves(
      availableMoves(view, state, source, rule, safeChain.length === 0).filter((move) => move.word !== candidate.word),
      'EXPERT'
    ).slice(0, 12);
    let botMove = null;
    if (botLevel) {
      const botMoves = rankStackMoves(availableMoves(view, afterState, analysis.to, rule, false), botLevel);
      botMove = botMoves[0] || null;
    }
    return {
      accepted: true,
      code: 'ACCEPTED',
      message: botMove ? '정상적으로 이어졌습니다. 상대 수를 계산했습니다.' : '정상적으로 이어졌습니다.',
      chain: newChain,
      requiredChar: required,
      nextChar: analysis.to,
      shieldUsed: false,
      analysis,
      alternatives,
      botMove
    };
  }

  rejected(code, message, chain, required, shields) {
    return {
      accepted: false,
      code: shields > 0 ? 'SHIELD_USED' : code,
      message: shields > 0 ? `보호막으로 이번 실패를 방어했습니다. ${message}` : message,
      chain,
      requiredChar: required,
      nextChar: required,
      shieldUsed: shields > 0,
      alternatives: []
    };
  }

  practice({ config, difficulty = 'STANDARD', startChar = null, usedWords = [] }) {
    const graph = this.graph(config);
    const used = new Set(usedWords);
    const requested = String(startChar || '').trim();
    const candidates = [...graph.states.keys()].filter((syllable) => {
      const count = graphMoveViews(graph, syllable, used).length;
      if (!count) return false;
      if (difficulty === 'BEGINNER') return count >= 8;
      if (difficulty === 'EXPERT') return count <= 7 || graph.states.get(syllable)?.state === 'WIN';
      return count >= 3 && count <= 20;
    });
    const required = requested && graphMoveViews(graph, requested, used).length
      ? requested
      : candidates[Math.floor(Math.random() * candidates.length)];
    if (!required) throw Object.assign(new Error('이 설정에서 연습 문제를 만들 수 없습니다.'), { code: 'NO_CHALLENGE' });
    const moves = graphMoveViews(graph, required, used);
    const sample = moves[0]?.word || null;
    const sampleWord = sample ? graph.view.byId.get(sample) : null;
    const hint = difficulty === 'BEGINNER'
      ? { firstLetter: sample?.[0] || null, length: sample?.length || null, theme: sampleWord ? [...sampleWord.themes][0] || null : null, sample }
      : difficulty === 'STANDARD'
        ? { firstLetter: sample?.[0] || null, length: sample?.length || null, theme: null, sample: null }
        : { firstLetter: null, length: null, theme: null, sample: null };
    const state = graph.states.get(required)?.state;
    return {
      requiredChar: required,
      difficulty,
      timeLimitSeconds: difficulty === 'BEGINNER' ? 0 : difficulty === 'EXPERT' ? 8 : 15,
      shieldCount: difficulty === 'BEGINNER' ? 3 : difficulty === 'EXPERT' ? 0 : 1,
      availableMoveCount: moves.length,
      hint,
      objective: state === 'WIN' ? '상대가 불리해지는 수를 찾아보세요.' : state === 'LOSS' ? '가능한 한 오래 버티는 방어 수를 찾아보세요.' : '루트를 유지하면서 선택지를 줄여보세요.'
    };
  }

  practiceAnswer({ config, requiredChar, usedWords = [], word: text, shields = 0 }) {
    const view = this.view(config);
    const state = view.chainState(usedWords);
    const candidate = view.byId.get(String(text || '').trim());
    const error = moveError(view, state, requiredChar, candidate, 'NONE', false);
    if (error) {
      const bestMoves = rankStackMoves(availableMoves(view, state, requiredChar, 'NONE', false), 'EXPERT').slice(0, 5);
      return {
        accepted: false,
        code: shields > 0 ? 'SHIELD_USED' : error[0],
        message: shields > 0 ? `보호막으로 이번 실패를 방어했습니다. ${error[1]}` : error[1],
        shieldUsed: shields > 0,
        nextChallenge: requiredChar,
        bestMoves
      };
    }
    return {
      accepted: true,
      code: 'ACCEPTED',
      message: '정상적으로 이어졌습니다.',
      shieldUsed: false,
      nextChallenge: view.destination(candidate),
      bestMoves: []
    };
  }

  analyze({ config, maxPly = 7, routeGroupLimit = 30, criticalWordLimit = 200 }) {
    const graph = this.graph(config);
    const states = {};
    const counts = { WIN: 0, LOSS: 0, ROUTE: 0 };
    for (const [syllable, state] of graph.states) {
      states[syllable] = {
        syllable,
        state: state.state,
        ply: state.ply,
        moveCount: (graph.adjacency.get(syllable) || []).length,
        representativeWord: state.representativeWord
      };
      counts[state.state] += 1;
    }
    const winningWithinPly = {};
    for (let ply = 1; ply <= Math.max(1, Math.min(30, maxPly)); ply += 1) {
      winningWithinPly[ply] = [...graph.states.entries()]
        .filter(([, state]) => state.state === 'WIN' && (state.ply ?? 999) <= ply)
        .map(([syllable]) => syllable)
        .sort();
    }
    const routeGroups = graph.routeGroups.slice(0, Math.max(1, Math.min(200, routeGroupLimit))).map((group) => {
      const words = [];
      const seen = new Set();
      for (const source of group) for (const word of graph.adjacency.get(source) || []) {
        if (group.has(graph.view.destination(word)) && !seen.has(word.word)) { seen.add(word.word); words.push(word); }
      }
      words.sort((a, b) => b.popularity - a.popularity);
      return { syllables: [...group].sort(), edgeCount: words.length, sampleWords: words.slice(0, 12).map((word) => word.word) };
    });
    return {
      corpusSize: graph.view.words.length,
      syllableCount: graph.states.size,
      states,
      counts,
      winningWithinPly,
      criticalWords: criticalWords(graph).slice(0, Math.max(1, Math.min(1000, criticalWordLimit))),
      routeGroups,
      generatedAt: graph.generatedAt
    };
  }

  compare({ base, compared }) {
    const before = this.graph(base);
    const after = this.graph(compared);
    const syllables = new Set([...before.states.keys(), ...after.states.keys()]);
    const changed = [...syllables].sort().flatMap((syllable) => {
      const oldState = before.states.get(syllable);
      const newState = after.states.get(syllable);
      if (oldState?.state === newState?.state && oldState?.ply === newState?.ply) return [];
      return [{ syllable, before: oldState?.state || null, after: newState?.state || null, beforePly: oldState?.ply ?? null, afterPly: newState?.ply ?? null }];
    });
    const beforeCritical = new Map(criticalWords(before).map((item) => [item.word, item]));
    const afterCritical = new Map(criticalWords(after).map((item) => [item.word, item]));
    return {
      baseCorpusSize: before.view.words.length,
      comparedCorpusSize: after.view.words.length,
      changed,
      addedCriticalWords: [...afterCritical.keys()].filter((word) => !beforeCritical.has(word)).slice(0, 500).map((word) => afterCritical.get(word)),
      removedCriticalWords: [...beforeCritical.keys()].filter((word) => !afterCritical.has(word)).slice(0, 500).map((word) => beforeCritical.get(word))
    };
  }

  strategy({ config, startChar, usedWords = [], depth = 10 }) {
    const graph = this.graph(config);
    const start = String(startChar || '').trim().slice(0, 1);
    const used = new Set(usedWords);
    const line = [];
    let current = start;
    for (let turn = 1; turn <= Math.max(1, Math.min(30, depth)); turn += 1) {
      const moves = graphMoveViews(graph, current, used);
      const chosen = moves[0];
      if (!chosen) break;
      line.push({
        turn,
        from: current,
        word: chosen.word,
        to: chosen.to,
        beforeState: graph.states.get(current)?.state || 'LOSS',
        afterState: chosen.resultingState,
        defenseCount: chosen.defenseCount
      });
      used.add(chosen.word);
      current = chosen.to;
    }
    return {
      startChar: start,
      state: graph.states.get(start)?.state || null,
      ply: graph.states.get(start)?.ply ?? null,
      line,
      alternatives: graphMoveViews(graph, start, new Set(usedWords)).slice(0, 30),
      complete: line.length > 0 && graphMoveViews(graph, current, used).length === 0
    };
  }
}
