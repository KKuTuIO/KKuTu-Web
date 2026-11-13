/**
 * Is this a folding phone? Only then does the layout branch (the tab bar becomes a side rail, see `MobileTabBar`), the way Apple's
 * guidance for folding phones moves navigation controls to the side; everything else keeps the ordinary phone layout.
 * Three signals, none of them enough alone:
 *  - the device model: `userAgentData.model` (Chrome on Android hides the model from the plain user agent string) or `SM-F9xx` / `Pixel Fold` in it;
 *  - the screen is split by a fold (`viewport-segments`, when the app spans both halves);
 *  - the unfolded inner display's shape: almost square, 600..800 px wide (a cover display is only told apart by the model).
 */
const MODEL = /SM-F9\d\d|Pixel Fold|Galaxy Z Fold/i;

export const fold = $state({ model: false, segments: false });

/** The shape of an unfolded inner display. */
export const innerShaped = (w, h) => w >= 600 && w <= 800 && h > 0 && w / h >= 0.75 && w / h <= 1.4;

/** The branch itself: a fold is known, and the screen is wider than tall (a short screen is short of height, not of width). */
export const foldableWide = (w, h, f = fold) => w >= h && (f.model || f.segments || innerShaped(w, h));

if (typeof window !== 'undefined') {
	try {
		fold.model = MODEL.test(navigator.userAgent ?? '');
		navigator.userAgentData?.getHighEntropyValues?.(['model']).then((v) => (fold.model ||= MODEL.test(v?.model ?? ''))).catch(() => {});
		for (const q of ['(horizontal-viewport-segments: 2)', '(vertical-viewport-segments: 2)']) {
			const m = matchMedia(q);
			if (m.matches) fold.segments = true;
			m.addEventListener?.('change', () => (fold.segments = matchMedia('(horizontal-viewport-segments: 2)').matches || matchMedia('(vertical-viewport-segments: 2)').matches));
		}
	} catch {
		/* no matchMedia (a test): not foldable */
	}
}
