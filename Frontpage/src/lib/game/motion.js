import { quintOut } from 'svelte/easing';

/** Motion helpers: every animation duration goes through `ms`, so "reduce motion" turns them all off. */
export const reducedMotion = () => typeof matchMedia !== 'undefined' && matchMedia('(prefers-reduced-motion: reduce)').matches;
export const ms = (n) => (reducedMotion() ? 0 : n);

/** A page that comes up from under the chat (the shop, the 사전 page, the waiting room): rises a little while it fades in. */
export function rise(node, { duration = 520 } = {}) {
	return { duration: ms(duration), easing: quintOut, css: (t, u) => `transform: translateY(${u * 14}%); opacity: ${Math.min(1, t * 2.2)}` };
}

/** Moving between the pages of one screen (tabs, entries): the new page slides in from the side it lies on and fades in; the old one is simply gone. */
export function swap(node, { dx = 0, duration = 260 } = {}) {
	return { duration: ms(duration), easing: quintOut, css: (t, u) => `opacity:${t};transform:translateX(${u * dx}px)` };
}
