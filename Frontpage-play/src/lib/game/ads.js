/**
 * The banner under the game (Kakao AdFit), shown to players who are not signed in. The units are the ones of the legacy client's bottom
 * banner (`kkutu.html` / `m_kkutu.html`).
 */
const SCRIPT = 'https://t1.daumcdn.net/kas/static/ba.min.js';

export const AD_UNITS = {
	wide: { unit: 'DAN-foUc9eTmMwRJkgLR', width: 728, height: 90 },
	narrow: { unit: 'DAN-ftEmFfVSbwkdD33G', width: 320, height: 50 }
};
/** From this window width the nudge fits beside the wide banner; under it, it takes a line of its own above. */
const BESIDE_FROM = 1040;
const NUDGE_LINE = 32;
const PAD = 4;

/**
 * What the bottom area holds in a window of this size: the banner, whether the sign-in nudge goes with it (a PC-sized window) and where,
 * and the height of the area in px. A phone lying on its side is wide but short, so it keeps the small banner.
 */
export function adLayout(width, height) {
	const wide = width >= 760 && height >= 500;
	const banner = wide ? AD_UNITS.wide : AD_UNITS.narrow;
	const beside = wide && width >= BESIDE_FROM;
	return { banner, nudge: wide, beside, size: banner.height + (wide ? PAD * 2 : 0) + (wide && !beside ? NUDGE_LINE : 0) };
}

let script = null;

/**
 * Show an ad in `ins` (an `ins.kakao_ad_area` that is on the page). The SDK draws every such element it finds when it first runs; one that
 * appears later is handed to it. Returns what takes the ad down again.
 */
export function mountAd(ins) {
	if (window.adfit?.render) window.adfit.render(ins);
	else if (!script) {
		script = document.createElement('script');
		script.async = true;
		script.src = SCRIPT;
		// blocked or offline: the area stays empty, and the next banner tries again
		script.onerror = () => {
			script.remove();
			script = null;
		};
		document.head.appendChild(script);
	}
	return () => window.adfit?.destroy?.(ins);
}
