<script>
	import { onMount } from 'svelte';

	/**
	 * 개봉 연출의 입자: 천천히 떠오르는 유리 조각과 반짝이는 별(항상), `burst(x, y, power)`로 터지는 파편·별·고리·궤도선.
	 * 조각은 두 색의 그라데이션에 흰 하이라이트를 얹은 반투명 다각형이고, 뒤집히듯 좁아졌다 넓어지며 돌아서 빛을 받는 유리처럼 보인다.
	 * canvas에 그린다(수십 개의 DOM 요소를 움직이지 않는다). 깊이별로 세 겹이다: 먼 조각(크게 흐리게), 가까운 조각과 튀는 파편(살짝 흐리게),
	 * 별과 빛(선명하게). 흐림은 canvas 전체에 CSS blur를 주는 것이라 조각마다 필터를 걸지 않는다. 움직임 줄이기에서는 한 장면만 그린다.
	 * 좌표는 이 캔버스(부모 크기)의 왼쪽 위 기준 px.
	 * @type {{colors?:string[], active?:boolean}}
	 */
	let { colors = [], active = true } = $props();

	const FALLBACK = ['#8fe9ff', '#ffb3e6', '#c9b6ff', '#fff3a8', '#b9ffe0'];
	const palette = $derived([...(colors.length ? colors : []), ...FALLBACK]);

	let far = $state(); // blurred hard: the shards that are far away
	let soft = $state(); // blurred a little: the near shards and what a burst throws
	let top = $state(); // sharp: stars and light
	let ctx; // the context being drawn on (switched between the three layers)
	let cFar, cSoft, cTop;
	const canvas = $derived(far);
	let w = 0;
	let h = 0;
	let dpr = 1;
	let raf = 0;
	let last = 0;
	let running = false;
	const reduce = typeof matchMedia !== 'undefined' && matchMedia('(prefers-reduced-motion: reduce)').matches;

	/** @type {any[]} */ const drift = []; // the shards that float all the time
	/** @type {any[]} */ const bits = []; // shards and stars that a burst threw
	/** @type {any[]} */ const rings = [];
	/** @type {any[]} */ const arcs = [];
	/** @type {any[]} */ const stars = []; // the twinkling ones

	const rnd = (a, b) => a + Math.random() * (b - a);
	const pick = () => palette[Math.floor(Math.random() * palette.length)];

	function shape() {
		// three or four corners at uneven angles and radii: a splinter, not a regular triangle
		const n = Math.random() < 0.6 ? 3 : 4;
		const base = rnd(0, Math.PI * 2);
		return Array.from({ length: n }, (_, i) => {
			const a = base + (i / n) * Math.PI * 2 + rnd(-0.5, 0.5);
			const r = rnd(0.55, 1.15);
			return [Math.cos(a) * r, Math.sin(a) * r * rnd(0.6, 1)];
		});
	}
	function shard(x, y, size) {
		return { x, y, size, pts: shape(), rot: rnd(0, 6.28), vr: rnd(-0.9, 0.9), fp: rnd(0, 6.28), fv: rnd(0.8, 2.6), c1: pick(), c2: pick(), a: rnd(0.4, 0.8) };
	}
	function seed() {
		drift.length = stars.length = 0;
		const count = Math.round(Math.min(34, Math.max(14, (w * h) / 38000)));
		for (let i = 0; i < count; i++) {
			const deep = i % 5 < 2; // two in five are far: bigger, slower and blurred
			const s = shard(rnd(0, w), rnd(0, h), deep ? rnd(30, 76) : rnd(10, 46));
			s.deep = deep;
			s.vx = rnd(-8, 8) * (deep ? 0.5 : 1);
			s.vy = rnd(-26, -8) * (deep ? 0.55 : 1);
			s.a *= deep ? 0.6 : 0.8;
			drift.push(s);
		}
		for (let i = 0; i < 16; i++) stars.push({ x: rnd(0, w), y: rnd(0, h), s: rnd(5, 13), p: rnd(0, 6.28), v: rnd(1.2, 3) });
	}

	/** A burst at (x, y): `power` 1 is the opening of a pack, more is for a rare card. */
	export function burst(x, y, power = 1) {
		if (reduce || !ctx) return;
		const n = Math.round(46 * power);
		for (let i = 0; i < n; i++) {
			const ang = rnd(0, Math.PI * 2);
			const sp = rnd(180, 760) * power;
			const star = i % 3 === 0;
			bits.push({ ...shard(x, y, star ? rnd(6, 15) : rnd(8, 40)), star, vx: Math.cos(ang) * sp, vy: Math.sin(ang) * sp, life: rnd(1.2, 2.4), t: 0, a: 1 });
		}
		rings.push({ x, y, t: 0, life: 0.9, r: 260 * power }, { x, y, t: -0.12, life: 1.1, r: 420 * power });
		for (let i = 0; i < 3; i++) arcs.push({ x, y, rx: rnd(90, 180) * power, ry: rnd(40, 90) * power, rot: rnd(0, 3.14), vr: rnd(-2.2, 2.2), t: -i * 0.07, life: 1.3, from: rnd(0, 6.28), len: rnd(2.2, 4) });
		wake();
	}

	const rgba = (alpha) => alpha;

	function drawShard(s, alpha) {
		const flip = Math.cos(s.fp);
		ctx.save();
		ctx.translate(s.x, s.y);
		ctx.rotate(s.rot);
		ctx.scale(0.2 + 0.8 * Math.abs(flip), 1);
		ctx.beginPath();
		s.pts.forEach(([px, py], i) => (i ? ctx.lineTo(px * s.size, py * s.size) : ctx.moveTo(px * s.size, py * s.size)));
		ctx.closePath();
		const g = ctx.createLinearGradient(-s.size, -s.size, s.size, s.size);
		g.addColorStop(0, s.c1);
		g.addColorStop(0.55, s.c2);
		g.addColorStop(1, '#ffffff');
		ctx.globalAlpha = rgba(alpha * s.a);
		ctx.fillStyle = g;
		ctx.fill();
		// the glint on the edge that catches the light
		ctx.globalAlpha = rgba(alpha * (0.35 + 0.5 * Math.abs(flip)));
		ctx.strokeStyle = '#fff';
		ctx.lineWidth = 1.2;
		ctx.stroke();
		ctx.restore();
	}
	function drawStar(x, y, r, rot, alpha) {
		ctx.save();
		ctx.translate(x, y);
		ctx.rotate(rot);
		ctx.globalAlpha = rgba(alpha);
		ctx.beginPath();
		for (let i = 0; i < 8; i++) {
			const rr = i % 2 ? r * 0.28 : r;
			const a = (i / 8) * Math.PI * 2;
			ctx.lineTo(Math.cos(a) * rr, Math.sin(a) * rr);
		}
		ctx.closePath();
		ctx.fillStyle = '#fff';
		ctx.shadowColor = '#fff';
		ctx.shadowBlur = r * 1.4;
		ctx.fill();
		ctx.restore();
	}

	function frame(now) {
		raf = 0;
		if (!ctx || !running) return;
		const dt = Math.min(0.05, (now - (last || now)) / 1000);
		last = now;
		for (const c of [cFar, cSoft, cTop]) c.clearRect(0, 0, w, h);

		for (const s of drift) {
			s.x += s.vx * dt;
			s.y += s.vy * dt;
			s.rot += s.vr * dt;
			s.fp += s.fv * dt;
			if (s.y < -s.size * 1.4) ((s.y = h + s.size * 1.4), (s.x = rnd(0, w)));
			if (s.x < -s.size * 1.4) s.x = w + s.size;
			if (s.x > w + s.size * 1.4) s.x = -s.size;
			ctx = s.deep ? cFar : cSoft;
			drawShard(s, 1);
		}
		ctx = cTop;
		ctx.globalCompositeOperation = 'lighter';
		for (const st of stars) {
			st.p += st.v * dt;
			drawStar(st.x, st.y, st.s, st.p * 0.3, Math.max(0, Math.sin(st.p)) * 0.9);
		}
		ctx.globalCompositeOperation = 'source-over';

		for (let i = bits.length - 1; i >= 0; i--) {
			const b = bits[i];
			b.t += dt;
			if (b.t >= b.life) {
				bits.splice(i, 1);
				continue;
			}
			const drag = Math.pow(0.07, dt); // the throw slows down fast
			b.vx *= drag;
			b.vy = b.vy * drag + 60 * dt;
			b.x += b.vx * dt;
			b.y += b.vy * dt;
			b.rot += b.vr * dt * 3;
			b.fp += b.fv * dt * 3;
			const k = 1 - b.t / b.life;
			if (b.star) {
				ctx = cTop;
				ctx.globalCompositeOperation = 'lighter';
				drawStar(b.x, b.y, b.size * (0.5 + k), b.rot, Math.min(1, k * 1.6));
				ctx.globalCompositeOperation = 'source-over';
			} else {
				ctx = cSoft;
				drawShard(b, Math.min(1, k * 1.6));
			}
		}

		ctx = cTop;
		ctx.globalCompositeOperation = 'lighter';
		for (let i = rings.length - 1; i >= 0; i--) {
			const r = rings[i];
			r.t += dt;
			if (r.t >= r.life) {
				rings.splice(i, 1);
				continue;
			}
			if (r.t < 0) continue;
			const k = r.t / r.life;
			const e = 1 - Math.pow(1 - k, 3);
			// a soft disc of light that grows (no outline: it just fades out at its rim)
			const g = ctx.createRadialGradient(r.x, r.y, 0, r.x, r.y, r.r * e);
			g.addColorStop(0, 'rgba(255,255,255,0.8)');
			g.addColorStop(1, 'rgba(255,255,255,0)');
			ctx.globalAlpha = (1 - k) * 0.7;
			ctx.fillStyle = g;
			ctx.beginPath();
			ctx.arc(r.x, r.y, r.r * e, 0, Math.PI * 2);
			ctx.fill();
		}
		ctx.lineCap = 'round';
		for (let i = arcs.length - 1; i >= 0; i--) {
			const a = arcs[i];
			a.t += dt;
			if (a.t >= a.life) {
				arcs.splice(i, 1);
				continue;
			}
			if (a.t < 0) continue;
			const k = a.t / a.life;
			a.rot += a.vr * dt;
			ctx.save();
			ctx.translate(a.x, a.y);
			ctx.rotate(a.rot);
			const grow = 0.4 + 0.6 * (1 - Math.pow(1 - k, 2));
			ctx.globalAlpha = Math.sin(Math.PI * Math.min(1, k * 1.15)) * 0.9;
			ctx.strokeStyle = '#fff';
			ctx.lineWidth = 2.4;
			ctx.shadowColor = '#fff';
			ctx.shadowBlur = 10;
			ctx.beginPath();
			const s0 = a.from + k * 2.5;
			ctx.ellipse(0, 0, a.rx * grow, a.ry * grow, 0, s0, s0 + a.len * Math.min(1, k * 2.2 + 0.2));
			ctx.stroke();
			ctx.restore();
		}
		ctx.globalCompositeOperation = 'source-over';
		ctx.globalAlpha = 1;

		if (!reduce) raf = requestAnimationFrame(frame);
	}

	function wake() {
		if (!running || raf || reduce) return;
		last = 0;
		raf = requestAnimationFrame(frame);
	}
	function resize() {
		if (!canvas?.parentElement) return;
		const r = canvas.parentElement.getBoundingClientRect();
		dpr = Math.min(2, window.devicePixelRatio || 1);
		w = r.width;
		h = r.height;
		canvas.width = Math.round(w * dpr);
		canvas.height = Math.round(h * dpr);
		for (const c of [soft, top]) {
			c.width = canvas.width;
			c.height = canvas.height;
		}
		[cFar, cSoft, cTop] = [far, soft, top].map((c) => {
			const g = c.getContext('2d');
			g.setTransform(dpr, 0, 0, dpr, 0, 0);
			return g;
		});
		ctx = cTop;
		seed();
		if (reduce) frame(performance.now());
	}

	$effect(() => {
		running = active && !!far && !!soft && !!top;
		if (running) wake();
		else if (raf) (cancelAnimationFrame(raf), (raf = 0));
	});

	onMount(() => {
		resize();
		const ro = new ResizeObserver(resize);
		ro.observe(canvas.parentElement);
		return () => {
			ro.disconnect();
			cancelAnimationFrame(raf);
			running = false;
		};
	});
</script>

<!-- three layers, far to near: the far shards are blurred hard, the near ones a little, the stars and light not at all -->
<canvas bind:this={far} class="pointer-events-none absolute inset-0 size-full" style="filter:blur(5px)" aria-hidden="true"></canvas>
<canvas bind:this={soft} class="pointer-events-none absolute inset-0 size-full" style="filter:blur(1.8px)" aria-hidden="true"></canvas>
<canvas bind:this={top} class="pointer-events-none absolute inset-0 size-full" aria-hidden="true"></canvas>
