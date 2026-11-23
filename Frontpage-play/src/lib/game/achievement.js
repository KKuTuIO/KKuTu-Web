/**
 * 업적(명성)을 화면에 그리는 데 쓰는 계산. 서버가 보낸 목록(`achievements` 패킷의 `list`)을 카드 한 장 분량의 값으로 바꾼다.
 * 서버가 주는 한 항목: {k, n: 이름, c: 조건 문구({n}은 표시하는 단계의 목표), ti: 달성한 단계 수, tt: [[목표, 명성, 단계 이름|0]],
 * v: 지금 값, f: 'n'|'hours'(값과 목표를 초로 보내고 시간으로 보여 준다), h: 숨김 업적이면 1, at: 마지막 달성 시각(ms)}
 */

const ROMAN = ['', 'Ⅰ', 'Ⅱ', 'Ⅲ', 'Ⅳ', 'Ⅴ', 'Ⅵ', 'Ⅶ', 'Ⅷ', 'Ⅸ', 'Ⅹ'];

const hoursOf = (seconds) => Math.round((seconds / 3600) * 10) / 10;
/** 숫자를 보여 줄 형태로: 시간 단위면 초를 시간으로 바꾼다. */
export const formatValue = (n, fmt) => Number(fmt === 'hours' ? hoursOf(n) : n).toLocaleString('ko-KR');

/** i번째(1부터) 단계의 이름: 단계마다 이름이 있으면 그것, 없으면 업적 이름에 로마 숫자(단계가 하나뿐이면 이름 그대로). */
export function tierTitle(item, tier) {
	const tt = item.tt ?? [];
	const row = tt[Math.max(1, Math.min(tt.length, tier)) - 1];
	if (row && row[2]) return String(row[2]);
	return tt.length > 1 ? `${item.n} ${ROMAN[tier] ?? tier}` : item.n;
}

/**
 * 카드 한 장에 필요한 값. 진행 중이면 다음 단계(`ti + 1`)를, 모두 달성했으면 마지막 단계를 보여 준다.
 * @returns {{key:string, title:string, cond:string, done:boolean, tier:number, count:number, points:number, target:number, text:string, pct:number, chips:{label:string, state:'done'|'active'|'todo'}[], rare:boolean, hidden:boolean, at:number}}
 */
export function view(item) {
	const tt = item.tt ?? [];
	const count = tt.length;
	const done = count > 0 && item.ti >= count;
	const tier = done ? count : Math.min(count, item.ti + 1);
	const [target, points] = tt[tier - 1] ?? [0, 0];
	const value = done ? target : Math.min(Number(item.v) || 0, target);
	return {
		key: item.k,
		title: tierTitle(item, tier),
		cond: String(item.c ?? '').replaceAll('{n}', formatValue(target, item.f)),
		done,
		tier,
		count,
		points,
		target,
		text: `${formatValue(value, item.f)} / ${formatValue(target, item.f)}`,
		pct: done ? 100 : target > 0 ? Math.min(100, Math.floor((value / target) * 100)) : 0,
		chips: tt.map((_, i) => ({ label: ROMAN[i + 1] ?? String(i + 1), state: i < item.ti ? 'done' : i === item.ti ? 'active' : 'todo' })),
		rare: !!item.h || points >= 70,
		hidden: !!item.h,
		at: Number(item.at) || 0
	};
}

/** 완료한 업적 수와 전체 수(숨김 업적은 달성한 것만 목록에 오므로 전체에 이미 그렇게 들어 있다). */
export function summarize(list) {
	let done = 0;
	for (const item of list ?? []) if (view(item).done) done++;
	return { done, total: (list ?? []).length };
}

const norm = (s) => String(s ?? '').toLowerCase().replace(/\s+/g, '');
/** 이름(단계 이름 포함)이나 조건에 검색어가 들어 있는 것만. 빈 검색어는 전부. */
export function filterItems(list, query) {
	const q = norm(query);
	if (!q) return list;
	return list.filter((item) => {
		const v = view(item);
		return norm(v.title).includes(q) || norm(item.n).includes(q) || norm(v.cond).includes(q);
	});
}

/**
 * 정렬. `progress`: 진행 중인 것을 진행률이 높은 순으로, 완료한 것은 그 뒤에 최근에 달성한 순.
 * `recent`: 최근에 달성한 순(달성한 적이 있는 것 먼저), 나머지는 진행률 순.
 */
export function sortItems(list, mode) {
	const rows = list.map((item) => ({ item, v: view(item) }));
	const byProgress = (a, b) => b.v.pct - a.v.pct || b.v.points - a.v.points || a.v.title.localeCompare(b.v.title, 'ko');
	const byRecent = (a, b) => b.v.at - a.v.at;
	if (mode === 'recent') {
		return rows.sort((a, b) => (a.v.at > 0) !== (b.v.at > 0) ? (a.v.at > 0 ? -1 : 1) : a.v.at > 0 ? byRecent(a, b) : byProgress(a, b)).map((r) => r.item);
	}
	return rows.sort((a, b) => (a.v.done !== b.v.done ? (a.v.done ? 1 : -1) : a.v.done ? byRecent(a, b) : byProgress(a, b))).map((r) => r.item);
}

/** 등급 막대: 지금 등급의 시작점부터 다음 등급까지 얼마나 왔는지(0~100). 다음 등급이 없으면 100. */
export function gradeProgress(points, grade, next) {
	if (!next) return 100;
	const from = grade?.from ?? 0;
	return Math.max(0, Math.min(100, Math.floor(((points - from) / Math.max(1, next.from - from)) * 100)));
}
