/**
 * Spring physics for the web, modelled on Apple's spring family.
 *
 * - Closed-form solution (no integration drift, stable at any frame time).
 * - Retargeting keeps position AND velocity, so interrupting an animation is
 *   continuous, which is what makes iOS UI feel alive.
 * - Mass is 1. `stiffness` = ω0², `damping` = 2ζω0.
 *
 * Config styles (all equivalent, pick whichever you're reading from a spec):
 *   { stiffness, damping }
 *   { response, dampingFraction }   SwiftUI .spring(response:dampingFraction:)
 *   { duration, bounce }            SwiftUI .spring(duration:bounce:)  (bounce -1…1)
 */

const TAU = Math.PI * 2;

export function resolveConfig(c = {}) {
  if (c.stiffness != null && c.damping != null) {
    return { omega: Math.sqrt(c.stiffness), zeta: c.damping / (2 * Math.sqrt(c.stiffness)) };
  }
  if (c.duration != null) {
    const bounce = c.bounce ?? 0;
    // Apple: ζ = 1 - bounce (bounce ≥ 0); negative bounce is overdamped: ζ = 1 / (1 + bounce)
    const zeta = bounce >= 0 ? 1 - bounce : 1 / (1 + bounce);
    return { omega: TAU / c.duration, zeta };
  }
  const response = c.response ?? 0.5;
  return { omega: TAU / response, zeta: c.dampingFraction ?? 1 };
}

export class Spring {
  constructor(value = 0, config = {}, { precision = 0.001 } = {}) {
    this.value = value;
    this.target = value;
    this.velocity = 0;
    this.precision = precision;
    this.configure(config);
  }

  configure(config) {
    const { omega, zeta } = resolveConfig(config);
    this.omega = omega;
    this.zeta = zeta;
    return this;
  }

  /** Retarget. Position and velocity are preserved; pass `velocity` to inject a throw. */
  to(target, { velocity } = {}) {
    this.target = target;
    if (velocity != null) this.velocity = velocity;
    return this;
  }

  /** Jump with no animation. */
  set(value) { this.value = this.target = value; this.velocity = 0; return this; }

  get settled() {
    return Math.abs(this.value - this.target) < this.precision && Math.abs(this.velocity) < this.precision;
  }

  /** Advance by dt seconds. Returns true while still moving. */
  update(dt) {
    if (this.settled) { this.value = this.target; this.velocity = 0; return false; }
    const x0 = this.value - this.target, v0 = this.velocity, w = this.omega, z = this.zeta;
    let x, v;
    if (z < 1 - 1e-6) {
      const wd = w * Math.sqrt(1 - z * z), e = Math.exp(-z * w * dt);
      const A = x0, B = (v0 + z * w * x0) / wd;
      const c = Math.cos(wd * dt), s = Math.sin(wd * dt);
      x = e * (A * c + B * s);
      v = e * (-z * w * (A * c + B * s) + wd * (-A * s + B * c));
    } else if (z <= 1 + 1e-6) {
      const e = Math.exp(-w * dt), C = v0 + w * x0;
      x = e * (x0 + C * dt);
      v = e * (C - w * (x0 + C * dt));
    } else {
      const q = w * Math.sqrt(z * z - 1);
      const r1 = -z * w + q, r2 = -z * w - q;
      const c2 = (v0 - r1 * x0) / (r2 - r1), c1 = x0 - c2;
      const e1 = Math.exp(r1 * dt), e2 = Math.exp(r2 * dt);
      x = c1 * e1 + c2 * e2;
      v = c1 * r1 * e1 + c2 * r2 * e2;
    }
    this.value = this.target + x;
    this.velocity = v;
    return true;
  }
}

/**
 * A named bundle of springs on one requestAnimationFrame loop that sleeps when
 * everything is at rest. `onUpdate(values)` fires every active frame.
 */
export class SpringGroup {
  constructor(initial, configs, onUpdate) {
    this.springs = {};
    this.onUpdate = onUpdate;
    this._raf = 0;
    this._last = 0;
    this._tick = this._tick.bind(this);
    for (const k of Object.keys(initial)) this.springs[k] = new Spring(initial[k], configs[k] ?? configs.default ?? {});
  }
  get values() {
    const o = {};
    for (const k in this.springs) o[k] = this.springs[k].value;
    return o;
  }
  /** group.to({ w: 300, h: 80 }) or group.to({ w: 300 }, { w: { velocity: 500 } }) */
  to(targets, opts = {}) {
    for (const k in targets) this.springs[k].to(targets[k], opts[k]);
    this._wake();
    return this;
  }
  set(values) {
    for (const k in values) this.springs[k].set(values[k]);
    this.onUpdate?.(this.values);
    return this;
  }
  configure(key, config) { this.springs[key].configure(config); return this; }
  _wake() {
    if (this._raf) return;
    this._last = performance.now();
    this._raf = requestAnimationFrame(this._tick);
  }
  _tick(now) {
    // Clamp so a backgrounded tab doesn't produce a giant first step
    const dt = Math.min((now - this._last) / 1000, 1 / 30);
    this._last = now;
    let active = false;
    for (const k in this.springs) active = this.springs[k].update(dt) || active;
    this.onUpdate?.(this.values);
    this._raf = active ? requestAnimationFrame(this._tick) : 0;
  }
  destroy() { cancelAnimationFrame(this._raf); this._raf = 0; }
}
