/**
 * 명성 등급의 색과 묶음. 서버가 주는 등급은 `{id: 'g1'..'g18', name: '글자 Ⅰ', from}`이고 3개가 한 단계(글자, 낱말, ...)다.
 * 뱃지 그림(assets/grade/g1~g18.svg)은 트로피 리그 티어 뱃지와 헷갈리지 않도록 색 조합과 가운데 심볼을 따로 정해 만들었고,
 * 여기의 색은 그 뱃지와 같은 계열이다(링, 등급 이름, 안내창의 띠에 쓴다): [진한 색(글자), 가운데(링·띠), 연한 색(바탕)].
 * 단계가 오를수록 청록 -> 파랑 -> 남보라 -> 자홍 -> 분홍 -> 코랄로 이어진다.
 */
export const STAGE_COLORS = [
	['#0f766e', '#14b8a6', '#ccfbf1'], // 글자
	['#1d4ed8', '#3b82f6', '#dbeafe'], // 낱말
	['#4338ca', '#6366f1', '#e0e7ff'], // 구절
	['#a21caf', '#d946ef', '#fae8ff'], // 문장
	['#be185d', '#ec4899', '#fce7f3'], // 문단
	['#c2410c', '#f97316', '#ffedd5'] // 사전
];
/** 등급이 없을 때(명성이 첫 등급 아래): 흐린 회색. */
export const NO_GRADE_ACCENT = ['#64748b', '#94a3b8', '#f1f5f9'];

/** 등급 id의 단계 번호(0부터), 모르면 -1. */
export function stageIndex(id) {
	const m = /^g(\d+)$/.exec(String(id ?? ''));
	return m ? Math.floor((Number(m[1]) - 1) / 3) : -1;
}

/** 등급 id의 색: [진한 색, 가운데, 연한 색]. */
export const gradeAccent = (id) => STAGE_COLORS[stageIndex(id)] ?? NO_GRADE_ACCENT;

const ROMAN = /\s*[ⅠⅡⅢⅣⅤⅥⅦⅧⅨⅩ]+$/;
/**
 * 등급 표를 단계별로 묶는다: [{index, name: '글자', accent, steps: [{id, name: '글자 Ⅰ', from}]}].
 * 모양이 예상과 다르면(id가 g숫자가 아니면) 빈 목록.
 */
export function stagesOf(grades) {
	const out = [];
	for (const g of Array.isArray(grades) ? grades : []) {
		const i = stageIndex(g.id);
		if (i < 0) return [];
		(out[i] ??= { index: i, name: String(g.name).replace(ROMAN, ''), accent: gradeAccent(g.id), steps: [] }).steps.push({ id: g.id, name: String(g.name), from: Number(g.from) || 0 });
	}
	return out.filter(Boolean);
}
