/**
 * 업적 달성 알림의 큐. 공용 토스트(한 줄 글)와 따로 두는 것은 모양이 다르기 때문이다(아이콘, 머리글과 이름 두 줄).
 * 화면에는 한꺼번에 둘까지만 보이고, 나머지는 차례를 기다린다(한 판에 여럿이 열려도 화면을 덮지 않게).
 * `showAchievements(items)`에 서버의 `achievement.unlock` 항목({n: 이름, r: 희귀 여부}, 나머지 값은 쓰지 않는다)을 넣으면 된다.
 */
const SHOW_MS = 6000;
const MAX_VISIBLE = 2;

let nextId = 1;
/** 지금 보이는 알림. <AchievementToastHost/>가 그린다. */
export const achievementToasts = $state([]);
const waiting = [];

function pump() {
	while (achievementToasts.length < MAX_VISIBLE && waiting.length) {
		const entry = { id: nextId++, ...waiting.shift() };
		achievementToasts.push(entry);
		setTimeout(() => dismissAchievement(entry.id), SHOW_MS);
	}
}

export function dismissAchievement(id) {
	const i = achievementToasts.findIndex((x) => x.id === id);
	if (i >= 0) achievementToasts.splice(i, 1);
	pump();
}

export function showAchievements(items) {
	for (const it of Array.isArray(items) ? items : []) {
		const name = String(it?.n ?? '').trim();
		if (!name) continue;
		waiting.push({ name, rare: !!it.r });
	}
	pump();
}
