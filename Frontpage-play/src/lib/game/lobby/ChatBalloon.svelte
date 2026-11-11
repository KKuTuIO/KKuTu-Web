<script>
	import { backOut, backIn } from 'svelte/easing';
	import { ms } from '../motion.js';
	import { prefs } from '../prefs.svelte.js';
	import { isHidden } from '../chatFilter.js';
	import { maskBad } from '../badwords.js';

	/**
	 * The speech balloon of a player: it pops up for 2.5 s whenever they say something out loud, in the waiting room over their seat
	 * and in a game over their card. Put it inside a positioned box; `class` places it.
	 */
	let { store, id, class: cls = '' } = $props();
	// A speech balloon over the seat for 2.5 s after the player says something (desktop only, like the legacy client).
	let balloon = $state(null); // { id, text }: every new line is a new balloon, so it pops again
	let balloonId = 0;
	let balloonTimer;
	// Pops up from the seat with an overshoot and a little wobble; leaves quickly, shrinking back towards the seat.
	function pop(node, { duration = 260 } = {}) {
		return {
			duration: ms(duration),
			css: (t, u) => {
				// back-out gives the overshoot; the sine adds a settling wobble that fades with t
				const s = 0.35 + 0.65 * backOut(t) + Math.sin(t * Math.PI * 2) * 0.04 * u;
				return `transform-origin:50% 100%;transform:translateY(${u * 10}px) scale(${s});opacity:${Math.min(1, t * 6)}`;
			}
		};
	}
	function drop(node, { duration = 120 } = {}) {
		return {
			duration: ms(duration),
			css: (t, u) => `transform-origin:50% 100%;transform:translateY(${u * 6}px) scale(${1 - 0.25 * backIn(u)});opacity:${t}`
		};
	}
	let seen = -1;
	$effect(() => {
		const chat = store.net.chat;
		const last = chat[chat.length - 1];
		if (seen < 0) seen = chat.length; // messages from before the seat appeared don't get a balloon
		if (chat.length === seen || !last) return;
		seen = chat.length;
		if (last.system || last.uid !== id) return;
		if (last.whisper || isHidden(last, store.net)) return; // whispers aren't said out loud over a seat
		balloon = { id: ++balloonId, text: prefs.filter === 'off' ? last.msg : maskBad(last.shown ?? last.msg) };
		clearTimeout(balloonTimer);
		balloonTimer = setTimeout(() => (balloon = null), 2500);
	});
	$effect(() => () => clearTimeout(balloonTimer));
</script>

{#each balloon ? [balloon] : [] as b (b.id)}
	<p in:pop out:drop class="pointer-events-none absolute left-1/2 z-10 hidden w-max max-w-[92%] -translate-x-1/2 rounded-xl border border-(color:--kk-line) bg-(--kk-surface) px-3 py-1.5 text-center text-sm font-bold break-words text-(--kk-text) shadow-[0_2px_0_var(--kk-line)] md:block {cls}">
		{b.text}
		<span class="absolute top-full left-1/2 size-2.5 -translate-x-1/2 -translate-y-1.5 rotate-45 border-r border-b border-(color:--kk-line) bg-(--kk-surface)"></span>
	</p>
{/each}
