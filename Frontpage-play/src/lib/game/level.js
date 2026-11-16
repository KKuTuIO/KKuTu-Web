// Mirrors the game server's EXP table (`KKuTuLevel.ts`) so levels match the in-game ones.
/** The cap and curve everyone had so far; levels up to here never change. */
export const BASE_MAX_LEVEL = 720;
/**
 * The new client always counts with the extended curve (721..800), so a score above the old cap shows its real level here. Levels up
 * to 720 are the same on every server; what a server pays or allows above 720 is up to that server (`config/level.json`).
 */
export const MAX_LEVEL = 800;

function baseScore(lv) {
	const m = (!(lv % 5) * 0.3 + 1) * (!(lv % 15) * 0.4 + 1) * (!(lv % 45) * 0.5 + 1);
	const [a, b, c, d] = lv <= 240 ? [60, 120, 180, 120] : lv <= 480 ? [100, 170, 240, 120] : [140, 220, 300, 120];
	return Math.round(m * (d + Math.floor(lv / 5) * a + Math.floor((lv * lv) / 225) * b + Math.floor((lv * lv) / 2025) * c));
}

/**
 * Experience that takes level `lv` to level `lv + 1`. Above 720 a level costs `1 + 0.2 * (lv - 720)` times the old curve, written as
 * `(5 + lv - 720) / 5` so it rounds exactly like the server.
 */
export function requiredScore(lv) {
	const base = baseScore(lv);
	return lv > BASE_MAX_LEVEL ? Math.round((base * (5 + lv - BASE_MAX_LEVEL)) / 5) : base;
}

export const EXP = [];
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
