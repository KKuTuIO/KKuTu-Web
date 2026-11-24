/**
 * A short test of how smoothly this device draws, run once after the lobby is up, to choose the default of 움직임 감소.
 *
 * The test is the pack opening in miniature, at 2% opacity so nobody sees it: two full-screen canvases of translucent gradient shards, redrawn every
 * frame under a CSS blur (a hard one and a light one), a canvas of stars, and a pack-sized layer that bobs under a drop-shadow filter with a gradient
 * that is painted anew each frame. Those are what the opening costs, so a device that cannot hold the frame time here would drop frames there.
 * (Measured on a 2018 phone: about 22 fps in the pack opening, 60 fps with 움직임 감소; a light canvas alone ran at 60 fps on it, which is why the test
 * has to be this heavy to tell such a device apart.)
 *
 * The answer is kept in this browser (`kkutu-play-bench`) and used again for two weeks. It only ever sets the default: once the player has
 * changed 움직임 감소 themselves (`motion.auto` is false) the result is ignored, and the system's own setting is left alone.
 */
import { applyAutoMotion, motion } from './prefs.svelte.js';
import { systemReducedMotion } from './motion.js';

const KEY = 'kkutu-play-bench';
const VERSION = 2; // 2: the heavier test (a result of the first, light test says nothing)
const MAX_AGE = 14 * 24 * 60 * 60 * 1000;
/**
 * Slow: the frames take 20 ms on average (under 50 fps) or one in twenty takes 45 ms or more. Measured: a 60 Hz screen that keeps up shows 16.7 ms
 * (a desktop, and the 2018 phone with 움직임 감소 on); that phone with the pack opening running shows 24 ms in the test and 45 ms in the real thing.
 * A faster screen (90, 120 Hz) shows less, one held to 30 fps by battery saving shows 33.
 */
export const SLOW_AVG_MS = 20;
export const SLOW_P95_MS = 45;
const MIN_FRAMES = 8; // a slow device draws few frames in the time given: that is an answer, not a missing one (a hidden or stalled tab is `measureFrames` returning null)

/** @param {number[]} frames the time between frames, ms. Returns null when there are too few to tell (fewer than a handful in 1.5 s would be under 5 fps: the page was busy with something else). */
export function judge(frames) {
	if (frames.length < MIN_FRAMES) return null;
	const sorted = [...frames].sort((a, b) => a - b);
	const avg = frames.reduce((a, b) => a + b, 0) / frames.length;
	const p95 = sorted[Math.floor(sorted.length * 0.95)];
	return { avg, p95, slow: avg > SLOW_AVG_MS || p95 > SLOW_P95_MS };
}

const rnd = (a, b) => a + Math.random() * (b - a);

/** Draws the test for `duration` ms. Resolves to the frame times, or to null when the tab was hidden or stalled (that says nothing about the device). */
export function measureFrames(duration = 1600) {
	return new Promise((resolve) => {
		const dpr = Math.min(2, window.devicePixelRatio || 1);
		const w = window.innerWidth;
		const h = window.innerHeight;
		const root = document.createElement('div');
		root.setAttribute('aria-hidden', 'true');
		root.style.cssText = 'position:fixed;inset:0;pointer-events:none;z-index:2147483000;opacity:0.02;overflow:hidden';
		const layer = (blur) => {
			const canvas = document.createElement('canvas');
			canvas.width = Math.round(w * dpr);
			canvas.height = Math.round(h * dpr);
			canvas.style.cssText = `position:absolute;inset:0;width:100%;height:100%;${blur ? `filter:blur(${blur}px)` : ''}`;
			root.appendChild(canvas);
			const ctx = canvas.getContext('2d');
			ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
			return ctx;
		};
		const far = layer(5);
		const soft = layer(1.8);
		const top = layer(0);
		const pack = document.createElement('div');
		const pw = Math.min(264, w * 0.64);
		pack.style.cssText = `position:absolute;left:${(w - pw) / 2}px;top:${h * 0.12}px;width:${pw}px;height:${pw * 5 / 3}px;border-radius:12px;filter:drop-shadow(0 18px 22px rgba(0,0,0,0.35))`;
		root.appendChild(pack);
		document.body.appendChild(root);

		const colors = ['#8fe9ff', '#ffb3e6', '#c9b6ff', '#fff3a8'];
		const mkShards = (n, lo, hi) => Array.from({ length: n }, () => ({ x: rnd(0, w), y: rnd(0, h), s: rnd(lo, hi), r: rnd(0, 6.28), v: rnd(-0.9, 0.9), f: rnd(0, 6.28) }));
		const farShards = mkShards(14, 30, 76);
		const softShards = mkShards(22, 10, 46);
		const stars = Array.from({ length: 16 }, () => ({ x: rnd(0, w), y: rnd(0, h), r: rnd(5, 13), p: rnd(0, 6.28) }));
		const drawShards = (ctx, list) => {
			ctx.clearRect(0, 0, w, h);
			for (const [i, s] of list.entries()) {
				s.r += s.v * 0.02;
				s.f += 0.03;
				ctx.save();
				ctx.translate(s.x, s.y);
				ctx.rotate(s.r);
				ctx.scale(0.2 + 0.8 * Math.abs(Math.cos(s.f)), 1);
				const g = ctx.createLinearGradient(-s.s, -s.s, s.s, s.s);
				g.addColorStop(0, colors[i % 4]);
				g.addColorStop(0.55, colors[(i + 1) % 4]);
				g.addColorStop(1, '#ffffff');
				ctx.fillStyle = g;
				ctx.globalAlpha = 0.7;
				ctx.beginPath();
				ctx.moveTo(-s.s, -s.s * 0.6);
				ctx.lineTo(s.s, -s.s * 0.2);
				ctx.lineTo(s.s * 0.3, s.s);
				ctx.closePath();
				ctx.fill();
				ctx.strokeStyle = '#fff';
				ctx.globalAlpha = 0.5;
				ctx.stroke();
				ctx.restore();
			}
		};

		const frames = [];
		const t0 = performance.now();
		let last = 0;
		const finish = (value) => {
			root.remove();
			resolve(value);
		};
		const tick = (now) => {
			if (document.visibilityState !== 'visible') return finish(null);
			if (last) {
				const dt = now - last;
				if (dt > 400) return finish(null); // the browser stopped drawing for a while (another tab, a lock screen)
				frames.push(dt);
			}
			last = now;
			drawShards(far, farShards);
			drawShards(soft, softShards);
			top.clearRect(0, 0, w, h);
			top.fillStyle = '#fff';
			top.shadowColor = '#fff';
			for (const st of stars) {
				st.p += 0.05;
				top.shadowBlur = st.r * 1.4;
				top.globalAlpha = Math.max(0, Math.sin(st.p));
				top.fillRect(st.x - st.r / 2, st.y - st.r / 2, st.r, st.r);
			}
			const t = (now - t0) / 1000;
			pack.style.transform = `translateY(${Math.sin(t * 1.8) * 14}px)`;
			pack.style.background = `conic-gradient(from ${(t * 51) % 360}deg at 50% 49%, #8fe9ff, #ffb3e6, #c9b6ff, #b9ffe0, #8fe9ff)`;
			if (now - t0 < duration) requestAnimationFrame(tick);
			else finish(frames.slice(6)); // the first frames carry the page's own start-up work
		};
		requestAnimationFrame(tick);
	});
}

function read() {
	try {
		const r = JSON.parse(localStorage.getItem(KEY) ?? 'null');
		return r && r.v === VERSION && typeof r.slow === 'boolean' && Date.now() - r.at < MAX_AGE ? r : null;
	} catch {
		return null;
	}
}
function write(result) {
	try {
		localStorage.setItem(KEY, JSON.stringify({ v: VERSION, at: Date.now(), avg: Math.round(result.avg * 10) / 10, p95: Math.round(result.p95), slow: result.slow }));
	} catch {
		/* private mode */
	}
}

let scheduled = false;
const idle = (fn) => (typeof requestIdleCallback === 'function' ? requestIdleCallback(fn, { timeout: 4000 }) : setTimeout(fn, 1500));

/**
 * Decides the default of 움직임 감소 for this device (once per page load at most, and only when the player has not chosen). `onEnabled` is called when
 * the test has just turned it on, so the player can be told.
 * @param {{onEnabled?: () => void}} [options]
 */
export function scheduleMotionBench({ onEnabled } = {}) {
	if (scheduled || typeof window === 'undefined') return;
	scheduled = true;
	if (!motion.auto || systemReducedMotion()) return; // chosen by the player, or already reduced by the system
	const known = read();
	if (known) {
		applyAutoMotion(known.slow); // tested lately: the same answer (it also restores the option if the settings were cleared)
		return;
	}
	const attempt = async (retry) => {
		const result = judge((await measureFrames()) ?? []);
		if (!result) return retry ? setTimeout(() => attempt(false), 12000) : undefined; // hidden or stalled: once more later, then until the next visit
		write(result);
		if (applyAutoMotion(result.slow) && result.slow) onEnabled?.();
	};
	idle(() => setTimeout(() => attempt(true), 2500)); // after the lobby has settled
}
