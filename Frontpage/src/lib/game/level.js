// Mirrors the legacy client's EXP table so levels match the in-game ones.
export const MAX_LEVEL = 720;

function requiredScore(lv) {
	const m = (!(lv % 5) * 0.3 + 1) * (!(lv % 15) * 0.4 + 1) * (!(lv % 45) * 0.5 + 1);
	const [a, b, c, d] = lv <= 240 ? [60, 120, 180, 120] : lv <= 480 ? [100, 170, 240, 120] : [140, 220, 300, 120];
	return Math.round(m * (d + Math.floor(lv / 5) * a + Math.floor((lv * lv) / 225) * b + Math.floor((lv * lv) / 2025) * c));
}

const EXP = [];
EXP.push(requiredScore(1));
for (let i = 2; i < MAX_LEVEL; i++) EXP.push(EXP[i - 2] + requiredScore(i));
EXP[MAX_LEVEL - 1] = Infinity;
EXP.push(Infinity);

export function getLevel(score) {
	let i = 0;
	while (i < EXP.length && score >= EXP[i]) i++;
	return i + 1;
}

/** Score needed to reach the current and next level, for progress bars. */
export function levelSpan(score) {
	const lv = getLevel(score);
	return { lv, from: lv > 1 ? EXP[lv - 2] : 0, to: EXP[lv - 1] };
}
