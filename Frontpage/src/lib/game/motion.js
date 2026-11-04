/** Motion helpers: every animation duration goes through `ms`, so "reduce motion" turns them all off. */
export const reducedMotion = () => typeof matchMedia !== 'undefined' && matchMedia('(prefers-reduced-motion: reduce)').matches;
export const ms = (n) => (reducedMotion() ? 0 : n);
