/**
 * Is this a folding phone with its inner display open? Only then does the lobby take the desktop's two columns (side bar beside the room list)
 * at widths where every other device gets the stacked tablet layout: an unfolded fold is nearly square and 750..1000 px wide, and stacking
 * the lists there leaves the rooms two rows. Everything else is left as it was.
 *
 * Two questions, answered separately:
 *  - IS IT A FOLDABLE: the device model (`userAgentData.model`, as Chrome on Android hides it from the plain user agent string; or `SM-F9xx`,
 *    `Pixel ... Fold` in the string, as Samsung Internet sends it), or the screen is split by a fold (`viewport-segments`), or, as a fallback,
 *    an Android touch device whose screen is nearly square.
 *  - IS THE INNER DISPLAY OPEN: the shape of the *screen* (`screen.width / height`), never the window: the address bar and the system bars
 *    take height from the window and would make the ratio depend on the browser. The inner displays are 0.75 (Z Fold 8) to 0.96 (Pixel Fold
 *    series) wide to tall; the cover displays are 0.43..0.63, so the model alone does not decide.
 */
const MODEL = /SM-F9\d\d|Pixel (?:\d+ Pro )?Fold|Galaxy Z Fold/i;

export const fold = $state({ model: false, segments: false, android: false, coarse: false, ratio: 0 });

/** The shape of the inner display of the folding phones (short side / long side). */
export const squareish = (ratio) => ratio >= 0.7 && ratio <= 1;

/** A foldable, and its screen is the nearly square inner one. */
export const innerOpen = (f = fold) => squareish(f.ratio) && (f.model || f.segments || (f.android && f.coarse));

/** The desktop's two columns: from 1024 px on for everyone, and from 768 px on an open fold (below that the phone layout takes over). */
export const SPLIT = 1024;
export const FOLD_SPLIT = 768;
export const twoColumns = (width, f = fold) => width >= SPLIT || (width >= FOLD_SPLIT && innerOpen(f));

if (typeof window !== 'undefined') {
	try {
		const ua = navigator.userAgent ?? '';
		fold.model = MODEL.test(ua);
		fold.android = /Android/i.test(ua);
		fold.coarse = matchMedia('(pointer: coarse)').matches;
		navigator.userAgentData?.getHighEntropyValues?.(['model']).then((v) => (fold.model ||= MODEL.test(v?.model ?? ''))).catch(() => {});
		const segments = () => (fold.segments = matchMedia('(horizontal-viewport-segments: 2)').matches || matchMedia('(vertical-viewport-segments: 2)').matches);
		const shape = () => (fold.ratio = screen.width && screen.height ? Math.min(screen.width, screen.height) / Math.max(screen.width, screen.height) : 0);
		segments();
		shape();
		// folding or unfolding changes the screen, and the window with it
		window.addEventListener('resize', () => (segments(), shape()));
	} catch {
		/* no matchMedia / screen (a test): not foldable */
	}
}
