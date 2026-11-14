<script>
	import { toast } from '../ui/toast.svelte.js';
	import Icon from './Icon.svelte';
	import HoverWave from './HoverWave.svelte';
	import RewardChip from './RewardChip.svelte';
	import RiddleScreen from './RiddleScreen.svelte';
	import SokssokScreen from './SokssokScreen.svelte';
	import { onMount } from 'svelte';
	import { countdown } from '../riddle.js';
	import { t } from '../i18n.svelte.js';

	/**
	 * 아케이드 (the two 리들 are real: their numbers and rewards come from the server's `riddle` summary; the other games are still SAMPLES, with
	 * placeholder numbers and no start). A desktop shows a list and, beside it, the open game's own panel (what it is,
	 * my record, the start button; in the mode screen's card language: light-to-bold fill, white outlined icon, dark ink lettering). A phone has no room for both: the same panel opens inside the row that was touched, one at a
	 * time (an accordion). `lead` is a snippet at the left end of the header (the new lobby's back button). Nothing starts yet.
	 * @type {{store?:any, lead?:import('svelte').Snippet}}
	 */
	let { store, lead } = $props();
	let w = $state(1280);
	const desk = $derived(w >= 768);
	// each game has a light-to-bold colour pair and the dark ink of its hue (the mode cards' way)
	const base = [
		{ id: 'daily', name: 'play.riddle.daily', icon: 'event', m: ['#63d49e', '#cff28c', '#285d30'], rewards: [], c: {d: 'M580-240q-42 0-71-29t-29-71q0-42 29-71t71-29q42 0 71 29t29 71q0 42-29 71t-71 29ZM200-80q-33 0-56.5-23.5T120-160v-560q0-33 23.5-56.5T200-800h40v-40q0-17 11.5-28.5T280-880q17 0 28.5 11.5T320-840v40h320v-40q0-17 11.5-28.5T680-880q17 0 28.5 11.5T720-840v40h40q33 0 56.5 23.5T840-720v560q0 33-23.5 56.5T760-80H200Zm0-80h560v-400H200v400Z', t: ['#059669', '#065f46'], k: ['#6ee7b7', '#10b981']}, note: 'play.riddle.daily.note', desc: 'play.riddle.daily.desc', stats: [] },
		{ id: 'wordle', name: 'play.riddle.free', icon: 'spellcheck', m: ['#7bbde8', '#b6eaff', '#09567c'], rewards: [], c: {d: 'm564-192 198-198q11-11 28-11t28 11q11 11 11 28t-11 28L621-137q-12 12-27 18t-30 6q-15 0-30-6t-27-18l-85-85q-11-11-11-28t11-28q11-11 28-11t28 11l86 86Z', ga: 'M424.1 -804.5H484.5V-338H424.1ZM468.8 -623.3H552V-573.5H468.8ZM302.3 -755.8H361.1Q361.1 -686.7 340.8 -624.8Q320.5 -562.9 274.8 -510.8Q229.2 -458.8 151.5 -420.2L118 -465.9Q180.4 -498.4 221.3 -539.3Q262.2 -580.1 282.2 -631.4Q302.3 -682.7 302.3 -745.6ZM142.9 -755.8H333.2V-707H142.9Z', t: ['#0284c7', '#075985'], k: ['#7dd3fc', '#0ea5e9']}, note: 'play.riddle.free.note', desc: 'play.riddle.free.desc', stats: [] },
		{ id: 'sokssok', name: 'play.arcade.sokssok.name', icon: 'grid_view', m: ['#f6a65c', '#fee273', '#73420c'], rewards: [], c: {d: 'M200-520q-33 0-56.5-23.5T120-600v-160q0-33 23.5-56.5T200-840h160q33 0 56.5 23.5T440-760v160q0 33-23.5 56.5T360-520H200Zm0 400q-33 0-56.5-23.5T120-200v-160q0-33 23.5-56.5T200-440h160q33 0 56.5 23.5T440-360v160q0 33-23.5 56.5T360-120H200Zm400-400q-33 0-56.5-23.5T520-600v-160q0-33 23.5-56.5T600-840h160q33 0 56.5 23.5T840-760v160q0 33-23.5 56.5T760-520H600Zm0 400q-33 0-56.5-23.5T520-200v-160q0-33 23.5-56.5T600-440h160q33 0 56.5 23.5T840-360v160q0 33-23.5 56.5T760-120H600Z', t: ['#d97706', '#92400e'], k: ['#fcd34d', '#f59e0b']}, note: 'play.arcade.sokssok.note', desc: 'play.arcade.sokssok.desc', stats: [] },
		{ id: 'typing', name: 'play.arcade.typing.name', icon: 'keyboard', m: ['#64c7c3', '#a2f4f4', '#005f5c'], rewards: [{ kind: 'ping', q: 10 }], c: {d: 'M160-200q-33 0-56.5-23.5T80-280v-400q0-33 23.5-56.5T160-760h640q33 0 56.5 23.5T880-680v400q0 33-23.5 56.5T800-200H160Zm200-120h240q17 0 28.5-11.5T640-360q0-17-11.5-28.5T600-400H360q-17 0-28.5 11.5T320-360q0 17 11.5 28.5T360-320ZM240-560q17 0 28.5-11.5T280-600q0-17-11.5-28.5T240-640q-17 0-28.5 11.5T200-600q0 17 11.5 28.5T240-560Zm120 0q17 0 28.5-11.5T400-600q0-17-11.5-28.5T360-640q-17 0-28.5 11.5T320-600q0 17 11.5 28.5T360-560Zm120 0q17 0 28.5-11.5T520-600q0-17-11.5-28.5T480-640q-17 0-28.5 11.5T440-600q0 17 11.5 28.5T480-560Zm120 0q17 0 28.5-11.5T640-600q0-17-11.5-28.5T600-640q-17 0-28.5 11.5T560-600q0 17 11.5 28.5T600-560Zm120 0q17 0 28.5-11.5T760-600q0-17-11.5-28.5T720-640q-17 0-28.5 11.5T680-600q0 17 11.5 28.5T720-560ZM240-440q17 0 28.5-11.5T280-480q0-17-11.5-28.5T240-520q-17 0-28.5 11.5T200-480q0 17 11.5 28.5T240-440Zm120 0q17 0 28.5-11.5T400-480q0-17-11.5-28.5T360-520q-17 0-28.5 11.5T320-480q0 17 11.5 28.5T360-440Zm120 0q17 0 28.5-11.5T520-480q0-17-11.5-28.5T480-520q-17 0-28.5 11.5T440-480q0 17 11.5 28.5T480-440Zm120 0q17 0 28.5-11.5T640-480q0-17-11.5-28.5T600-520q-17 0-28.5 11.5T560-480q0 17 11.5 28.5T600-440Zm120 0q17 0 28.5-11.5T760-480q0-17-11.5-28.5T720-520q-17 0-28.5 11.5T680-480q0 17 11.5 28.5T720-440Z', t: ['#0d9488', '#115e59'], k: ['#5eead4', '#14b8a6']}, note: 'play.arcade.typing.note', desc: 'play.arcade.typing.desc', stats: [['play.arcade.stat.best', ['play.arcade.unit.strokes', 412]], ['play.arcade.stat.average', ['play.arcade.unit.strokes', 305]], ['play.arcade.stat.accuracy', '96%']] },
		{ id: 'math', name: 'play.arcade.math.name', icon: 'calculate', m: ['#fa96c3', '#ffbccb', '#753855'], rewards: [{ kind: 'ping', q: 10 }], c: {d: 'M320-320v50q0 13 8.5 21.5T350-240q13 0 21.5-8.5T380-270v-50h50q13 0 21.5-8.5T460-350q0-13-8.5-21.5T430-380h-50v-50q0-13-8.5-21.5T350-460q-13 0-21.5 8.5T320-430v50h-50q-13 0-21.5 8.5T240-350q0 13 8.5 21.5T270-320h50Zm230 50h140q13 0 21.5-8.5T720-300q0-13-8.5-21.5T690-330H550q-13 0-21.5 8.5T520-300q0 13 8.5 21.5T550-270Zm0-100h140q13 0 21.5-8.5T720-400q0-13-8.5-21.5T690-430H550q-13 0-21.5 8.5T520-400q0 13 8.5 21.5T550-370Zm70-208 35 35q9 9 21 9t21-9q8-8 8.5-20.5T698-585l-36-37 35-35q9-9 9-21t-9-21q-9-9-21-9t-21 9l-35 35-35-35q-9-9-21-9t-21 9q-9 9-9 21t9 21l35 35-36 37q-8 9-8 21t9 21q9 9 21 9t21-9l35-35Zm-340-14h140q13 0 21.5-8.5T450-622q0-13-8.5-21.5T420-652H280q-13 0-21.5 8.5T250-622q0 13 8.5 21.5T280-592Zm-80 472q-33 0-56.5-23.5T120-200v-560q0-33 23.5-56.5T200-840h560q33 0 56.5 23.5T840-760v560q0 33-23.5 56.5T760-120H200Z', t: ['#db2777', '#9d174d'], k: ['#f9a8d4', '#ec4899']}, note: 'play.arcade.math.note', desc: 'play.arcade.math.desc', stats: [['play.arcade.stat.best', ['play.arcade.unit.problems', 38]], ['play.arcade.stat.average', ['play.arcade.unit.problems', 27]]] }
	];
	// 리들 (오늘의 리들 and the free one): what the server says about me, in place of the sample numbers
	const riddle = $derived(store?.net.riddle.summary ?? null);
	const sok = $derived(store?.net.sok.summary ?? null);
	let tick = $state(Date.now());
	onMount(() => {
		store?.riddleOpen();
		store?.sokOpen();
		const id = setInterval(() => (tick = Date.now()), 1000);
		return () => clearInterval(id);
	});
	/** a game's texts in the player's language (a stat value is a text, or [locale key, number] for a number with its unit) */
	const localized = (g) => ({
		...g,
		name: t(g.name),
		note: t(g.note),
		desc: t(g.desc),
		stats: g.stats.map(([k, v]) => [t(k), Array.isArray(v) ? t(v[0], { n: v[1] }) : v])
	});
	const games = $derived(
		base.map((g) => {
			const loc = localized(g);
			if (g.id === 'sokssok' && sok) {
				return {
					...loc,
					rewards: sok.reward.pay ? [{ kind: 'xp', q: sok.reward.exp }, { kind: 'ping', q: sok.reward.ping }] : [],
					desc: sok.reward.pay ? t('play.arcade.sokssok.descPay', { time: sok.time, min: sok.minWords }) : loc.desc,
					stats: [
						[t('play.sok.stat.bestScore'), sok.best.score ? t('play.sok.stat.score', { n: sok.best.score }) : '-'],
						[t('play.sok.stat.bestWords'), sok.best.words ? t('play.sok.stat.words', { n: sok.best.words }) : '-'],
						...(sok.reward.pay ? [[t('play.sok.stat.leftToday'), t('play.riddle.stat.leftCount', { n: sok.left })]] : [])
					]
				};
			}
			if (!riddle || (g.id !== 'daily' && g.id !== 'wordle')) return loc;
			const pay = riddle.reward;
			if (g.id === 'daily') {
				const k = (100 + riddle.bonusPercent) / 100;
				return {
					...loc,
					rewards: pay.pay ? [{ kind: 'xp', q: Math.round(pay.exp * k) }, { kind: 'ping', q: Math.round(pay.ping * k) }] : [],
					flag: riddle.daily.status === 'new' ? t('play.riddle.daily.flag') : undefined,
					stats: [
						[t('play.riddle.stat.today'), t(`play.riddle.state.${riddle.daily.status}`)],
						[t('play.riddle.stat.streak'), t('play.riddle.stat.days', { n: riddle.streak })],
						[t('play.riddle.stat.resetIn'), countdown(riddle.resetAt, tick)]
					]
				};
			}
			return {
				...loc,
				rewards: pay.pay ? [{ kind: 'xp', q: riddle.freeReward.exp }, { kind: 'ping', q: riddle.freeReward.ping }] : [],
				desc: pay.pay ? t('play.riddle.free.descPay', { n: riddle.free.perDay }) : loc.desc,
				stats: [
					[t('play.riddle.stat.best'), riddle.free.best ? t('play.riddle.stat.bestTries', { n: riddle.free.best }) : '-'],
					[t('play.riddle.stat.solved'), t('play.riddle.stat.solvedCount', { n: riddle.free.solved })],
					...(pay.pay ? [[t('play.riddle.stat.leftToday'), t('play.riddle.stat.leftCount', { n: riddle.free.left })]] : [])
				]
			};
		})
	);
	/** the game being played (a riddle's screen replaces the list) */
	let playing = $state(/** @type {'daily'|'free'|'sok'|null} */ (null));
	const play = (id) => (id === 'daily' ? (playing = 'daily') : id === 'wordle' ? (playing = 'free') : id === 'sokssok' ? (playing = 'sok') : soon());
	// desktop: one is always open (the first at the start); phone: none until one is touched
	let sel = $state(/** @type {string|null} */ (null));
	const open = $derived(sel ?? (desk ? games[0].id : null));
	const current = $derived(games.find((g) => g.id === open));
	function pick(id) {
		sel = sel === id && !desk ? null : id;
	}
	const soon = () => toast(t('play.arcade.soon'), { kind: 'info', icon: 'event' });
</script>

<svelte:window bind:innerWidth={w} />

{#snippet glyph(g, size, uid, white = false)}
	<span class="relative inline-grid shrink-0">
	<!-- the game's filled shape (리들's 가 is a glyph of Noto Sans KR SemiBold, SIL OFL, in the place of the A, with its corners rounded by a stroke): the FILL 1 drawing of Material Symbols Rounded (Apache 2.0, from google/material-design-icons; the self-hosted icon font is only its unfilled instance), not a square tile: a top-to-bottom gradient in the 600-to-800 of its hue (300-to-500 in a dark theme) -->
	<svg viewBox="0 -960 960 960" width={size} height={size} class="glyph shrink-0 {white ? 'glyph-white' : ''}" style:--t1={g.c.t[0]} style:--t2={g.c.t[1]} style:--k1={g.c.k[0]} style:--k2={g.c.k[1]} aria-hidden="true">
		<defs><linearGradient id="gl-{g.id}-{uid}" x1="0" y1="0" x2="0" y2="1"><stop offset="0" style="stop-color: var(--a)" /><stop offset="1" style="stop-color: var(--b)" /></linearGradient></defs>
		<path fill={white ? '#fff' : `url(#gl-${g.id}-${uid})`} stroke={white ? g.m[2] : undefined} stroke-width={white ? 34 : undefined} stroke-linejoin="round" paint-order="stroke" fill-rule="evenodd" d={g.c.d} />
		{#if g.c.ga}<!-- a letter in the place of the icon's A, with a round stroke of its own colour that rounds the corners -->
			<path fill={white ? '#fff' : `url(#gl-${g.id}-${uid})`} stroke={white ? g.m[2] : `url(#gl-${g.id}-${uid})`} stroke-width={white ? 34 : 16} paint-order="stroke" stroke-linejoin="round" stroke-linecap="round" d={g.c.ga} />{/if}
	</svg>
	{#if g.flag}<span class="dot" role="img" aria-label={g.flag}></span>{/if}
	</span>
{/snippet}

{#snippet info(g, pane)}
	<p class="note rounded-xl px-3 py-2.5 text-sm leading-relaxed font-medium {pane ? 'note-pane' : 'note-row'}">{g.desc}</p>
	{#if g.stats.length}
		<dl class="grid grid-cols-[repeat(auto-fit,minmax(5.5rem,1fr))] gap-2">
			{#each g.stats as [k, v] (k)}
				<div class="note rounded-xl px-3 py-2 {pane ? 'note-pane' : 'note-row'}"><dt class="text-xs font-bold opacity-70">{k}</dt><dd class="text-base font-extrabold tabular-nums">{v}</dd></div>
			{/each}
		</dl>
	{/if}
{/snippet}

{#snippet rewardsBox(g, pane)}
	{#if g.rewards.length}
		<!-- the event page's reward chips, reused (SAMPLE amounts: the reward policy is not decided) -->
		<div class="note flex items-center gap-2 rounded-xl px-3 py-2 {pane ? 'note-pane' : 'note-row'}">
			<span class="text-xs font-bold opacity-70">{t('play.arcade.reward')}</span>
			<div class="flex flex-wrap items-center gap-1.5">{#each g.rewards as r, i (i)}<RewardChip reward={r} shop={store?.shop ?? {}} size={36} />{/each}</div>
		</div>
	{/if}
{/snippet}

{#snippet startBtn(g)}
	<button type="button" disabled={(g.id === 'daily' || g.id === 'wordle') && !riddle} onclick={() => play(g.id)} class="hw kk-btn kk-btn-hero kk-btn-lg w-full"><span class="kk-btn-icon"><Icon name="play_arrow" size={28} fill /></span><span class="kk-btn-label"><HoverWave text={t('play.arcade.start')} /></span></button>
{/snippet}

{#if playing === 'sok'}
	<SokssokScreen {store} onback={() => { playing = null; store?.sokOpen(); }} />
{:else if playing}
	<RiddleScreen {store} mode={playing} onback={() => { playing = null; store?.riddleOpen(); }} />
{:else}
<div class="flex min-h-0 flex-1 flex-col">
	<div class="flex items-center gap-3 px-3 py-2">
		{@render lead?.()}
		<h2 class="title text-xl font-extrabold">{t('play.lobby.arcade')}</h2>
	</div>
	<div class="flex min-h-0 flex-1 gap-3 px-3 pb-3">
		<ul class="flex min-h-0 flex-1 flex-col gap-2 overflow-y-auto md:max-w-sm md:flex-none md:basis-80">
			{#each games as g (g.id)}
				{@const on = open === g.id}
				<li class="row shrink-0 rounded-2xl {on ? 'row-on' : ''}">
					<button type="button" aria-expanded={on} onclick={() => pick(g.id)} class="flex min-h-[4.5rem] w-full items-center gap-3 rounded-2xl px-3 py-2 text-left">
						{@render glyph(g, 44, 'row')}
						<span class="min-w-0 flex-1">
							<span class="grad flex items-center gap-1.5 text-base font-extrabold" style:--t1={g.c.t[0]} style:--t2={g.c.t[1]} style:--k1={g.c.k[0]} style:--k2={g.c.k[1]}><span class="gtext">{g.name}</span></span>
							<span class="block truncate text-xs font-bold text-(color:--kk-muted)">{g.note}</span>
						</span>
						<Icon name="expand_more" size={24} class="shrink-0 text-(color:--kk-muted) transition-transform md:hidden {on ? 'rotate-180' : ''}" />
					</button>
					{#if !desk}
						<!-- phone: the panel opens inside the row -->
						<div class="acc" class:acc-open={on} inert={!on}>
							<div class="min-h-0 overflow-hidden"><div class="flex flex-col gap-3 px-3 pb-3">{@render info(g, false)}{@render rewardsBox(g, false)}{@render startBtn(g)}</div></div>
						</div>
					{/if}
				</li>
			{/each}
		</ul>
		{#if desk && current}
			<div role="region" class="flex min-h-0 flex-1 flex-col gap-3 self-start overflow-y-auto" aria-label={current.name} style:--c1={current.m[0]} style:--c2={current.m[1]} style:--ink={current.m[2]}>
				{#key current.id}
					<!-- 1) the title and the icon: the mode card, with the tiles drifting under it -->
					<div class="hero relative isolate grid place-items-center overflow-hidden py-5">
						<span class="hero-check" aria-hidden="true"><span class="kk-checker"></span></span>
						{@render glyph(current, 84, 'hero', true)}
						<span class="hero-name">{current.name}</span>
					</div>
					<!-- 2) what it is and how I did, on a container of its own -->
					<div class="info flex flex-col gap-3 p-3">{@render info(current, true)}</div>
					<!-- 3) what it pays: the event page's reward container -->
					{#if current.rewards.length}<div class="info flex flex-col gap-2 p-3">{@render rewardsBox(current, true)}</div>{/if}
					<!-- 4) the start button stands alone -->
					{@render startBtn(current)}
				{/key}
			</div>
		{/if}
	</div>
</div>
{/if}

<style>
	.title {
		color: #fff;
		text-shadow: 0 1px 4px rgb(10 14 50 / 0.65);
	}
	/* the buttons are white (a little see-through), as in the stage's other screens; the colour lives in the lettering and the icon's shape */
	.row {
		background: color-mix(in srgb, var(--kk-surface) 94%, transparent);
		box-shadow: 0 2px 6px rgb(20 40 80 / 0.22);
	}
	.row {
		transition: background 0.15s, box-shadow 0.15s;
	}
	.row:hover,
	.row-on {
		background: var(--kk-surface);
	}
	.row-on {
		box-shadow: 0 2px 6px rgb(20 40 80 / 0.22), inset 0 0 0 2px var(--kk-accent);
	}
	/* the nudge: a red dot on the icon's corner */
	.dot {
		position: absolute;
		top: -1px;
		right: -3px;
		width: 0.8rem;
		height: 0.8rem;
		border-radius: 50%;
		background: var(--kk-danger, #e5484d);
		box-shadow: 0 0 0 2px var(--kk-surface);
	}
	.glyph,
	.grad {
		--a: var(--t1);
		--b: var(--t2);
	}
	@media (prefers-color-scheme: dark) {
		.glyph,
		.grad {
			--a: var(--k1);
			--b: var(--k2);
		}
	}
	:global(:root[data-theme='dark']) .glyph,
	:global(:root[data-theme='dark']) .grad {
		--a: var(--k1);
		--b: var(--k2);
	}
	:global(:root[data-theme='light']) .glyph,
	:global(:root[data-theme='light']) .grad {
		--a: var(--t1);
		--b: var(--t2);
	}
	/* gradient lettering: the same top-to-bottom pair, clipped to the text */
	.grad .gtext {
		background-image: linear-gradient(to bottom, var(--a), var(--b));
		-webkit-background-clip: text;
		background-clip: text;
		color: transparent;
	}
	/* the open game's title card: the mode card's look (light-to-bold fill, radius, shadow) with the tiles drifting under the icon */
	.hero {
		border-radius: 20px;
		background: linear-gradient(to bottom, var(--c1), var(--c2));
		box-shadow: 0 3px 6px rgb(20 40 80 / 0.28);
		gap: 0.35rem;
	}
	.hero-check {
		position: absolute;
		inset: 0;
		z-index: -1;
		overflow: hidden;
		pointer-events: none;
	}
	/* the description and the numbers: a translucent container of their own */
	.info {
		border-radius: 20px;
		background: color-mix(in srgb, var(--kk-surface) 72%, transparent);
		color: var(--kk-text);
		box-shadow: 0 3px 6px rgb(20 40 80 / 0.18);
	}
	/* the lettering is One Mobile POP in the ink of the game's hue, as on the mode cards */
	.hero-name {
		font-family: 'ONE-Mobile-POP', 'NanumSquare Neo', sans-serif;
		font-size: 1.9rem;
		line-height: 1.1;
		color: var(--ink);
	}
	.glyph-white {
		filter: drop-shadow(0 3px 0 var(--ink));
	}
	/* the descriptions sit on a translucent container of their own so they read on any fill */
	.note-pane {
		background: color-mix(in srgb, var(--kk-surface) 82%, transparent);
		color: var(--kk-text);
	}
	.note-row {
		background: rgb(20 30 60 / 0.06);
	}
	/* the accordion: the row's panel grows from 0 to its height (a grid row going from 0fr to 1fr) */
	.acc {
		display: grid;
		grid-template-rows: 0fr;
		transition: grid-template-rows 0.28s ease;
	}
	.acc-open {
		grid-template-rows: 1fr;
	}
	@media (prefers-reduced-motion: reduce) {
		.acc {
			transition: none;
		}
	}
</style>
