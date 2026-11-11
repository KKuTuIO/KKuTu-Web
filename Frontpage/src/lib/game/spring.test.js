import { describe, expect, it } from 'vitest';
import { Spring, resolveConfig } from './spring.js';

// Ported from ios-physics/spring.test.mjs
const run = (s, secs, dt = 1 / 120) => {
	let peak = s.value;
	for (let t = 0; t < secs; t += dt) {
		s.update(dt);
		peak = Math.max(peak, s.value);
	}
	return peak;
};

describe('Spring', () => {
	it('converges for under, critically and over damped springs', () => {
		for (const dampingFraction of [0.4, 1, 2.5]) {
			const s = new Spring(0, { response: 0.5, dampingFraction }).to(100);
			run(s, 4);
			expect(s.value).toBeCloseTo(100, 1);
		}
	});
	it('overshoots as the theory says', () => {
		for (const z of [0.3, 0.5, 0.7, 0.84]) {
			const s = new Spring(0, { response: 0.5, dampingFraction: z }).to(100);
			const peak = run(s, 3, 1 / 2000);
			expect(Math.abs((peak - 100) / 100 - Math.exp((-Math.PI * z) / Math.sqrt(1 - z * z)))).toBeLessThan(0.002);
		}
	});
	it('does not depend on the frame rate', () => {
		const a = new Spring(0, { response: 0.4, dampingFraction: 0.6 }).to(1);
		const b = new Spring(0, { response: 0.4, dampingFraction: 0.6 }).to(1);
		run(a, 0.37, 1 / 30);
		run(b, 0.37, 1 / 240);
		expect(Math.abs(a.value - b.value)).toBeLessThan(0.02);
	});
	it('keeps position and velocity when retargeted', () => {
		const s = new Spring(0, { response: 0.5, dampingFraction: 0.7 }).to(100);
		run(s, 0.2);
		const x = s.value;
		const v = s.velocity;
		s.to(-50);
		expect(s.value).toBe(x);
		expect(s.velocity).toBe(v);
	});
	it('reads the three config styles alike', () => {
		const r = resolveConfig({ response: 0.5, dampingFraction: 0.7 });
		const w = resolveConfig({ stiffness: ((2 * Math.PI) / 0.5) ** 2, damping: (4 * Math.PI * 0.7) / 0.5 });
		expect(r.omega).toBeCloseTo(w.omega, 9);
		expect(r.zeta).toBeCloseTo(w.zeta, 9);
		expect(resolveConfig({ duration: 0.5, bounce: 0.16 }).zeta).toBeCloseTo(0.84, 9);
	});
	it('settles exactly and sleeps', () => {
		const s = new Spring(0, { response: 0.3, dampingFraction: 1 }).to(1);
		run(s, 3);
		expect(s.settled).toBe(true);
		expect(s.value).toBe(1);
	});
});
