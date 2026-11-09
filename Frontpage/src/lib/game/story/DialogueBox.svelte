<script>
	import { visibleEl } from './focus.js';
	import { untrack } from 'svelte';
	import { t } from '../i18n.svelte.js';
	import { reducedMotion } from '../motion.js';
	import { CAST } from './cast.js';
	import Rich from '../ui/Rich.svelte';
	import Icon from '../lobby/Icon.svelte';
	import Mascot from '../lobby/Mascot.svelte';

	/**
	 * The speech bubble of a dialogue script: the speaker's avatar and name, the line typed out letter by letter (press to show it
	 * at once, press again for the next one), the answers of a choice, and 이전 / 건너뛰기.
	 * `placement`: where on the screen it sits ('bottom', or 'top' when the bottom is where the game is played).
	 * @type {{runner:any, store:any, placement?:'bottom'|'top'}}
	 */
	let { runner, store, placement = 'bottom' } = $props();
	// A phone's screen is short: the bubble goes to the half the pointed part is not in.
	let winW = $state(typeof innerWidth === 'undefined' ? 1280 : innerWidth);
	let spotY = $state(null);
	$effect(() => {
		const sel = runner.state.focus;
		if (winW >= 768 || !sel) {
			spotY = null;
			return;
		}
		let raf = 0;
		const look = () => {
			const r = visibleEl(sel)?.getBoundingClientRect();
			const next = r ? (r.top + r.bottom) / 2 : null;
			if (next !== spotY) spotY = next;
			raf = requestAnimationFrame(look);
		};
		look();
		return () => cancelAnimationFrame(raf);
	});
	// (a script that sits at the top to keep clear of the answer field has the practice board under it: on a phone the board's own gap, under the players, is the place)
	const place = $derived(winW < 768 ? (spotY !== null ? (spotY > innerHeight * 0.5 ? 'top' : 'bottom') : placement === 'top' ? 'mid' : placement) : placement);
	const s = untrack(() => runner.state); // one runner for the life of the bubble
	const now = $derived(s.tip ?? s.line);
	const full = $derived(now ? t(now.text, now.params) : '');

	/** The first `n` visible characters of a line, tags (<b> ...) kept: Rich ignores one that is left open. */
	function cut(html, n) {
		let out = '';
		let seen = 0;
		for (const tok of html.split(/(<[^>]*>)/)) {
			if (/^<[^>]*>$/.test(tok)) out += tok;
			else if (seen < n) {
				out += tok.slice(0, n - seen);
				seen += tok.length;
			}
		}
		return out;
	}
	const total = $derived(full.replace(/<[^>]*>/g, '').length);
	let shown = $state(0);
	const typing = $derived(shown < total);
	$effect(() => {
		now?.n;
		full;
		shown = 0;
		if (!now) return;
		if (reducedMotion()) {
			shown = total;
			return;
		}
		const timer = setInterval(() => {
			shown += 1;
			if (shown >= total) clearInterval(timer);
		}, 26);
		return () => clearInterval(timer);
	});

	/** A press on the bubble: finish typing, else go on. */
	export function press() {
		if (typing) shown = total;
		else runner.advance();
	}
	const name = $derived(now ? t(CAST[now.who]?.name ?? '') : '');
	const mood = $derived(now?.mood ?? 'happy');
</script>

<svelte:window bind:innerWidth={winW} />

{#if now}
	<div class="pointer-events-none fixed inset-x-0 z-20 flex justify-center px-3 {place === 'top' ? 'top-20 max-md:top-16' : place === 'mid' ? 'top-[46%]' : 'bottom-5 max-md:bottom-3'}">
		<div class="pointer-events-auto flex w-[min(48rem,100%)] items-end gap-2 max-md:relative max-md:block max-md:w-full">
			<!-- a phone: the bubble is as wide as the screen, and the character peeks over its top edge (only the upper two thirds of it show) -->
			<div class="relative z-10 -mr-3 flex shrink-0 flex-col items-center max-md:absolute max-md:bottom-full max-md:left-4 max-md:z-0 max-md:mr-0 max-md:h-[37px] max-md:overflow-hidden">
				{#key now.n}<span class="kk-mood-{mood} block"><Mascot {store} who={now.who} size={winW < 768 ? 56 : 92} /></span>{/key}
				{#if winW >= 768}<span class="-mt-2 rounded-full bg-(--kk-accent-strong) px-3 py-0.5 text-xs font-extrabold text-white shadow-[0_2px_0_var(--kk-line-strong)]">{name}</span>{/if}
			</div>
			{#if winW < 768}<span class="absolute bottom-full left-[84px] z-20 translate-y-1/2 rounded-full bg-(--kk-accent-strong) px-3 py-0.5 text-xs font-extrabold text-white shadow-[0_2px_0_var(--kk-line-strong)]">{name}</span>{/if}
			<div class="relative min-w-0 flex-1 rounded-3xl border-[1.5px] border-(color:--kk-line-strong) bg-(--kk-surface) py-4 pr-5 pl-6 max-md:z-10 max-md:pt-4 max-md:pb-3 max-md:pr-3 max-md:pl-5 shadow-[0_3px_0_var(--kk-line-strong)]">
				<!-- a plain button over the whole bubble would swallow the choices: only the text is pressable -->
				<button type="button" onclick={press} class="block min-h-14 w-full cursor-pointer text-left outline-none text-base max-md:min-h-0 max-md:text-[15px] leading-relaxed font-bold text-(color:--kk-text)" aria-live="polite">
					<!-- the typed part over the whole line (invisible), so the bubble has its final height from the first letter -->
					<span class="grid"><span class="col-start-1 row-start-1"><Rich html={cut(full, shown)} /></span><span class="invisible col-start-1 row-start-1" aria-hidden="true"><Rich html={full} /></span></span>
				</button>
				{#if s.choices && !typing}
					<div class="mt-3 flex flex-wrap gap-2">
						{#each s.choices as c, i (c.goto + i)}
							<button type="button" class="kk-btn kk-btn-primary px-4" onclick={() => runner.choose(i)}>{t(c.text)}</button>
						{/each}
					</div>
				{/if}
				<div class="mt-2 flex flex-nowrap items-center justify-end gap-1 text-sm font-bold whitespace-nowrap max-md:mt-1 max-md:[&>button]:shrink-0 max-md:[&>button]:px-1.5 text-(color:--kk-muted)">
					{#if s.canGoBack && !s.tip}
						<button type="button" onclick={() => runner.back()} class="inline-flex h-8 items-center gap-1 rounded-lg px-2 hover:bg-(--kk-surface-2)"><Icon name="arrow_back" size={18} />{t('play.story.ui.back')}</button>
					{/if}
					<button type="button" onclick={() => runner.skip()} class="inline-flex h-8 items-center gap-1 rounded-lg px-2 hover:bg-(--kk-surface-2)"><Icon name="skip_next" size={18} />{t('play.story.ui.skip')}</button>
					{#if s.canAdvance}
						<button type="button" onclick={press} class="kk-btn kk-btn-primary ml-1 inline-flex h-9 min-h-0 items-center gap-1 px-4 text-sm">{typing ? t('play.story.ui.skipTyping') : t('play.story.ui.next')}<Icon name="arrow_forward" size={18} /></button>
					{/if}
				</div>
			</div>
		</div>
	</div>
{/if}

<style>
	@media (prefers-reduced-motion: no-preference) {
		.kk-mood-happy {
			animation: kk-hop 0.5s ease-out;
		}
		.kk-mood-surprised {
			animation: kk-shake 0.5s ease-in-out;
		}
		.kk-mood-think {
			animation: kk-tilt 0.7s ease-in-out;
		}
	}
	@keyframes kk-hop {
		40% {
			translate: 0 -10px;
		}
	}
	@keyframes kk-shake {
		25% {
			translate: -4px 0;
		}
		75% {
			translate: 4px 0;
		}
	}
	@keyframes kk-tilt {
		50% {
			rotate: -6deg;
		}
	}
</style>
