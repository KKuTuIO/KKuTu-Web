/**
 * 트로피 리그의 티어 뱃지. 티어를 정하고 점수를 매기는 일은 모두 서버가 하고, 여기서는 이름과 그림 주소만 만든다.
 * 뱃지 SVG는 CDN의 play/league/ 에 있다(원본 생성기는 로컬 전용 .dev/league-badges).
 */
import { IMAGE_VERSION } from './images.js';
import { PENDING_CDN } from '../cdn.js';

/** 서버 표(game/league/tiers.ts)와 같은 순서의 티어 id. 랭킹 행은 이 인덱스를 보낸다. */
export const TIER_IDS = [
	'BRONZE_II', 'BRONZE_I', 'SILVER_II', 'SILVER_I', 'GOLD_II', 'GOLD_I', 'EMERALD_II', 'EMERALD_I', 'RUBY_II', 'RUBY_I',
	'CRYSTAL_II', 'CRYSTAL_I', 'DIAMOND_II', 'DIAMOND_I', 'CHALLENGER_II', 'CHALLENGER_I', 'MASTER_II', 'MASTER_I'
];
export const TIER_GROUPS = ['BRONZE', 'SILVER', 'GOLD', 'EMERALD', 'RUBY', 'CRYSTAL', 'DIAMOND', 'CHALLENGER', 'MASTER'];

export const tierIdOf = (index) => TIER_IDS[Number(index)] ?? '';

/** `GOLD_II` -> { group: 'GOLD', step: 2 } (티어가 아닌 값은 group '') */
export function tierParts(id) {
	const m = /^([A-Z]+)_(I{1,2})$/.exec(String(id ?? ''));
	return m && TIER_GROUPS.includes(m[1]) ? { group: m[1], step: m[2] === 'II' ? 2 : 1 } : { group: '', step: 0 };
}

/** 특수 뱃지: 마스터 만렙(숫자 대신 별), 언랭크(-, ?). */
export const SPECIAL_BADGES = ['master-star', 'unranked-dash', 'unranked-question'];

/** 뱃지 파일 이름(확장자 없이). 티어 id 또는 특수 뱃지 이름을 받는다. 알 수 없으면 ''. */
export function badgeName(tierOrSpecial) {
	if (SPECIAL_BADGES.includes(tierOrSpecial)) return tierOrSpecial;
	const { group, step } = tierParts(tierOrSpecial);
	return group ? `${group.toLowerCase()}-${step}` : '';
}

/** 움직이는 판(레벨 751~800의 빛줄기·반짝임)이 있는 뱃지는 마스터뿐이다. */
export const hasBadgeFx = (name) => name.startsWith('master-');

/** CDN 주소. 움직임 감소 설정이면 같은 그림의 정지판을 쓴다(레벨 아이콘의 fxlv.png / newlv.png와 같은 방식). */
export function badgeUrl(tierOrSpecial, { motion = true } = {}) {
	const name = badgeName(tierOrSpecial);
	if (!name) return '';
	return `${PENDING_CDN}/img/play/league/${name}${motion && hasBadgeFx(name) ? '-fx' : ''}.svg${IMAGE_VERSION}`;
}

/** 뱃지 SVG의 가로:세로 비(131:145). */
export const BADGE_RATIO = 131 / 145;

/**
 * 매칭 설정: 이용자가 매칭에서 만날 게임 유형. id는 서버의 모드 코드(rule.json)와 같아서 선택을 그대로 서버에 보낸다.
 * 여러 개를 켜 두면 판마다 그중 하나가 걸리고(참가자 모두가 켠 모드의 교집합에서), 그 결과는 규칙 릴 연출로 보여 준다.
 * 서버의 LEAGUE_MODES(game/league/rules.ts)와 같은 5개다.
 */
export const MATCH_KINDS = [
	{ id: 'KSH', lang: 'ko', mode: 'classic' },
	{ id: 'ESH', lang: 'en', mode: 'classic' },
	{ id: 'KKT', lang: 'ko', mode: 'kkk' },
	{ id: 'KWS', lang: 'ko', mode: 'wordstack' },
	{ id: 'EWS', lang: 'en', mode: 'wordstack' }
];
const MATCH_KEY = 'kkutu-play-league-kinds';

/** 저장된 선택(꺼 둔 유형의 id 목록). 저장된 것이 없거나 깨졌으면 모두 켜짐. */
export function loadOffKinds(storage = typeof localStorage === 'undefined' ? null : localStorage) {
	try {
		const raw = JSON.parse(storage?.getItem(MATCH_KEY) ?? 'null');
		if (Array.isArray(raw)) return raw.filter((id) => MATCH_KINDS.some((k) => k.id === id));
	} catch {
		/* 깨진 값: 모두 켜진 상태로 시작 */
	}
	return [];
}
export function saveOffKinds(off, storage = typeof localStorage === 'undefined' ? null : localStorage) {
	try {
		storage?.setItem(MATCH_KEY, JSON.stringify(off));
	} catch {
		/* 저장할 수 없는 환경 */
	}
}
/** 하나는 반드시 켜 둔다(전부 끄면 매칭할 상대가 없다). 막혔으면 false. */
export function canTurnOff(off, id) {
	return MATCH_KINDS.filter((k) => !off.includes(k.id) && k.id !== id).length > 0;
}

/** 서버의 규칙 코드(rule.json) -> 로케일 키의 이름(game.option.<이름>.name / .expl). nob는 리그 전용 문구를 쓴다. */
export const RULE_LABEL_NAME = { man: 'Manner', saf: 'Safe', ext: 'Injeong', mis: 'Mission', tct: 'Tactical', k32: 'Sami', pwr: 'Power', itm: 'Item' };

/** 릴에서 도는 라운드 시간·라운드 수 후보(서버 rules.ts의 ROUND_TIMES/ROUND_COUNTS와 같다). */
export const REEL_TIMES = [30, 60, 90, 120];
export const REEL_ROUNDS = [3, 5];

/** 시드 없이도 같은 순서가 나오는 간단한 섞기(릴의 겉모습용이라 암호용이 아니다). */
export function shuffled(list, seed) {
	let h = 2166136261;
	for (const ch of String(seed)) h = Math.imul(h ^ ch.charCodeAt(0), 16777619);
	const rnd = () => ((h = Math.imul(h ^ (h >>> 15), 2246822507) ^ Math.imul(h ^ (h >>> 13), 3266489909)), ((h ^ (h >>> 16)) >>> 0) / 4294967296);
	const a = [...list];
	for (let i = a.length - 1; i > 0; i--) {
		const j = Math.floor(rnd() * (i + 1));
		[a[i], a[j]] = [a[j], a[i]];
	}
	return a;
}

/**
 * 리그의 오늘 운영 시계(한국 시간). 이용 시간은 09:30 ~ 03:30(서버 game/league/schedule.ts와 같다).
 * `open`이면 마감(03:30)까지, 아니면 문 여는 시각(09:30)까지 남은 ms를 준다.
 */
export function leagueClock(nowMs) {
	const DAY = 86_400_000;
	const t = nowMs + 9 * 3_600_000;
	const into = ((t % DAY) + DAY) % DAY;
	const minutes = Math.floor(into / 60_000);
	const open = minutes >= 9 * 60 + 30 || minutes < 3 * 60 + 30;
	const target = (open ? 3.5 : 9.5) * 3_600_000;
	return { open, ms: (target - into + DAY) % DAY };
}

/** 오늘의 혜택이 있는 날의 안내 문구 키(일반 운영일은 null). */
export const dayNudgeKey = (kind) => (kind === 'special' || kind === 'defenseFree' || kind === 'rewardBoost' ? `play.league.nudge.${kind}` : null);

/**
 * 티어 계열의 띠 색: [짙은 색(글자 테두리), 가운데, 밝은 색]. 뱃지 SVG의 계열 색(.dev/league-badges)과 같은 결로 맞췄다.
 * 결과 띠가 이 색으로 칠해져서, 올라간 티어가 한눈에 보인다.
 */
export const TIER_ACCENT = {
	BRONZE: ['#7a3f14', '#d98b4a', '#f8cc92'],
	SILVER: ['#46566d', '#8fa1ba', '#cfd9e8'],
	GOLD: ['#8a5a0a', '#f0b429', '#ffe38a'],
	EMERALD: ['#0b5e49', '#2fbf8f', '#97f2cf'],
	RUBY: ['#7a1230', '#e0476b', '#ffa6bb'],
	CRYSTAL: ['#2f2580', '#7d6bf0', '#c9bfff'],
	DIAMOND: ['#0b5380', '#38b6f0', '#aee7ff'],
	CHALLENGER: ['#18307a', '#4f6bf0', '#a5b8ff'],
	MASTER: ['#561a86', '#c04ff0', '#f3b0ff']
};
/** 티어 id(`GOLD_II`)의 띠 색. 티어가 아니면 브랜드 남색. */
export function tierAccent(id) {
	return TIER_ACCENT[tierParts(id).group] ?? ['#1f3166', '#3553a0', '#8da6e8'];
}
