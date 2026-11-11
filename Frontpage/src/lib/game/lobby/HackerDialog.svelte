<script>
	import { onDestroy } from 'svelte';
	import { t } from '../i18n.svelte.js';
	import { playSound } from '../audio.svelte.js';
	import { cdnImg } from '../../cdn.js';
	import { reducedMotion } from '../motion.js';
	import { fetchHistory } from '../profile.js';
	import { TILE, TILE_SQUARES } from '../gameTile.js';
	import { totals, perGame, baseGame, bestFormula } from '../formula.js';
	import { isEquipped } from '../shop.js';
	import { refreshEnchants } from '../enchantStore.svelte.js';
	import { wearPlan, wearAll } from '../enchant.js';
	import { confirm } from '../ui/confirm.svelte.js';
	import Dialog from './Dialog.svelte';
	import GameIcon from './GameIcon.svelte';
	import Icon from './Icon.svelte';
	import ShopThumb from './ShopThumb.svelte';

	/**
	 * 해커레미의 조합: asks whether I want experience, ping or both, finds the best combination of what I own for that (the server's
	 * additive rule, see formula.js), and shows what it is worth next to what I wear now, with the items to put on. It can put them on.
	 * @type {{onshop?:()=>void, open:boolean, store:any, entries:{item:any,count:number,expire?:number}[], details:Record<string,any>, nameOf:(id:string)=>string}}
	 */
	let { open = $bindable(false), store, entries, details, nameOf, onshop } = $props();
	let step = $state('ask'); // ask | hacking | result
	let banner = $state(null); // the full-width band: null | 'in' (hacking) | 'success' | 'out'
	let goalPicked = $state('xp'); // which currency the particles are, set as soon as a goal is chosen
	// Matrix rain around 해커레미: a few columns of random glyphs, each falling at its own speed (generated once; they only need to look random).
	const GLYPHS = 'ㄱㄴㄷㅏㅓㅗㅜ해킹조합XP핑0123456789ABCDEF<>/#$';
	const rnd = (a, b) => a + Math.random() * (b - a);
	const RAIN = Array.from({ length: 16 }, (_, i) => ({
		x: 2 + i * 6.2 + rnd(-1.5, 1.5),
		text: Array.from({ length: 11 }, () => GLYPHS[Math.floor(Math.random() * GLYPHS.length)]).join('\n'),
		dur: rnd(1.1, 2.1),
		delay: -rnd(0, 2),
		size: rnd(0.8, 1.15)
	}));
	// The currency particles that burst from 해커레미 on success (12 of them, thrown out in random directions, falling a little).
	const BURST = Array.from({ length: 14 }, (_, i) => {
		const a = rnd(-Math.PI * 0.95, -Math.PI * 0.05) + (i % 2 ? 0.2 : -0.2);
		const v = rnd(110, 300);
		return { dx: Math.cos(a) * v, dy: Math.sin(a) * v * 0.55, rot: rnd(-200, 200), size: Math.round(rnd(20, 36)), delay: rnd(0, 0.18) };
	});
	let lost = $state(false); // 해킹 실패 (nothing better to wear): gray band, sweat
	let won = $state(false); // the success look (gold band, 해킹 성공) stays through the exit
	let overlay = $state(null);
	$effect(() => {
		if (banner && overlay && !overlay.matches(':popover-open')) overlay.showPopover();
	});
	let run = 0; // bumped whenever the dialog resets, so a calculation that finishes after that is dropped
	const HACK_MS = 2200;
	const FAIL_MS = 1600; // 해킹 실패 stays a little longer than the success: the sweat needs to run
	const SUCCESS_MS = 1400; // 해킹 성공 stays this long, then the band wipes away
	const OUT_MS = 420; // the "hacking" stage lasts at least this long (the banner needs its time)
	let result = $state(null);
	let shown = $state({ x: 0, m: 0 }); // the percentages counting up
	let raf = 0;
	const equip = $derived(store.me?.equip ?? {});
	const GOALS = [
		{ id: 'xp', icons: ['xp'] },
		{ id: 'ping', icons: ['ping'] },
		{ id: 'both', icons: ['xp', 'ping'] }
	];

	$effect(() => {
		if (open) {
			run++;
			banner = null;
			won = false;
			lost = false;
			step = 'ask';
			result = null;
		}
	});
	onDestroy(() => cancelAnimationFrame(raf));

	const pct = (a, b) => (b > 0 ? Math.max(0, Math.round((a / b - 1) * 100)) : 0);
	const wait = (ms) => new Promise((r) => setTimeout(r, ms));
	async function choose(goal) {
		const mine = ++run;
		goalPicked = goal;
		const t0 = performance.now();
		step = 'hacking';
		result = null;
		won = false;
		lost = false;
		banner = reducedMotion() ? null : 'in';
		let rows = [];
		try {
			rows = (await fetchHistory(store.net.id, 1, 10)).rows;
		} catch {
			/* no record service: the default reference game is used */
		}
		const base = baseGame(rows, totals(equip, store.shop));
		const r = bestFormula({ owned: entries, equip, goal, base, equips: store.net.meta.moremiEquips, shop: store.shop });
		const nowGame = perGame(r.now, base);
		const thenGame = perGame(r.then, base);
		const next = { ...r, goal, played: rows.filter((g) => Number(g.playTime) > 0).length, nowGame, thenGame, upX: pct(thenGame.exp, nowGame.exp), upM: pct(thenGame.money, nowGame.money), anyEffect: entries.some((e) => e.item.options && ['gEXP', 'hEXP', 'gMNY', 'hMNY'].some((k) => e.item.options[k])) };
		if (!reducedMotion()) {
			await wait(HACK_MS - (performance.now() - t0));
			if (mine !== run) return;
			if (next.anyEffect && next.picks.length) {
				won = true;
				banner = 'success';
				playSound('success');
				await wait(SUCCESS_MS);
				if (mine !== run) return;
			} else {
				lost = true;
				banner = 'fail';
				playSound('fail');
				await wait(FAIL_MS);
				if (mine !== run) return;
			}
			banner = 'out';
			await wait(OUT_MS);
			if (mine !== run) return;
		}
		banner = null;
		result = next;
		step = 'result';
		count(result.upX, result.upM);
	}
	function count(x, m) {
		cancelAnimationFrame(raf);
		if (reducedMotion()) return (shown = { x, m });
		const t0 = performance.now();
		const tick = (now) => {
			const k = Math.min(1, (now - t0) / 900);
			const e = 1 - Math.pow(1 - k, 3);
			shown = { x: Math.round(x * e), m: Math.round(m * e) };
			if (k < 1) raf = requestAnimationFrame(tick);
		};
		raf = requestAnimationFrame(tick);
	}

	const effectText = (item) =>
		['gEXP', 'hEXP', 'gMNY', 'hMNY']
			.filter((k) => item.options?.[k])
			.map((k) => `${t(`kkutu.js.opts.${k}`)} ${k.startsWith('g') ? `+${(item.options[k] * 100).toFixed(1)}%p` : `+${item.options[k]}`}`)
			.join(' · ');
	// Nudges toward 보너스권 while I own none: the sentence has the ticket as a link to the shop (`{ticket}` marks where it goes).
	// Owned = one I can still use (count left, not expired) or one I am wearing; the same test the recommendation itself applies.
	const hasTicket = $derived(entries.some((e) => e.item.group === 'BON' && ((e.count > 0 && !(e.expire && e.expire * 1000 < Date.now())) || isEquipped(e.item, equip))));
	const nudge = $derived(result ? t(`play.hacker.bonus.${result.goal}`, { ticket: '\u0001' }).split('\u0001') : []);
	const fmt = (n) => Math.round(n).toLocaleString();
	const wantsX = $derived(result && result.goal !== 'ping');
	const wantsM = $derived(result && result.goal !== 'xp');

	async function apply() {
		const picks = result?.picks ?? [];
		if (!picks.length) return;
		if (!(await confirm({ message: t('play.hacker.applyConfirm', { n: picks.length }) }))) return;
		// The server toggles an item in its slot, so a hand that holds something else is emptied first; any other slot is simply replaced.
		for (const p of picks) if ((p.slot === 'Mlhand' || p.slot === 'Mrhand') && p.from) store.conn.send('consume', { item: p.from, slot: p.slot });
		for (const p of picks) store.conn.send('consume', { item: p.id, slot: p.slot });
		open = false;
		// of an item I own enchanted, the best copy goes on (it is the one whose lines count)
		const ids = picks.map((p) => p.id);
		const counts = Object.fromEntries(entries.map((e) => [e.item._id, e.count]));
		await wearAll(wearPlan(await refreshEnchants(), ids, counts));
		await refreshEnchants();
	}
</script>

<Dialog bind:open title={t('play.hacker.name')} ghost={step === 'hacking'}>
	<div class="flex flex-col gap-4">
		{#if step === 'hacking'}
			<div class="h-28" role="status" aria-live="polite"><span class="sr-only">{t('play.hacker.hacking')}</span></div>
		{:else}
		<div class="flex items-end gap-3">
			<img src={cdnImg('play/hackerremi.png')} alt="" draggable="false" class="size-24 shrink-0 object-contain" />
			<p class="relative mb-2 rounded-2xl rounded-bl-sm bg-(--kk-surface-2) px-4 py-2.5 text-sm leading-snug font-bold shadow-[0_2px_0_var(--kk-line)]">
				{#if step === 'ask'}{t('play.hacker.ask')}
				{:else if !result}{t('kkutu.js.loading')}
				{:else if !result.anyEffect}{t('play.hacker.empty')}
				{:else if !result.picks.length}{t('play.hacker.none')}
				{:else}{t(`play.hacker.formula.${result.goal}`)}{/if}
				{#if step === 'result' && result && !hasTicket}
					<span class="mt-1 block text-[13px] font-semibold text-(color:--kk-muted)">{nudge[0]}<button type="button" onclick={() => onshop?.()} class="cursor-pointer border-0 bg-transparent p-0 font-extrabold text-[#7c3aed] underline underline-offset-2 dark:text-[#c4b5fd]">{t('play.hacker.bonus.ticket')}</button>{nudge[1]}</span>
				{/if}
			</p>
		</div>
		{/if}

		{#if step === 'ask'}
			<div class="grid gap-2 sm:grid-cols-3">
				{#each GOALS as g (g.id)}
					<button type="button" onclick={() => choose(g.id)} class="kk-btn flex h-auto min-h-0 flex-col items-center gap-1.5 py-3">
						<span class="flex items-center">{#each g.icons as icon, i}<span class={i ? '-ml-1.5 rounded-full ring-2 ring-(--kk-surface)' : ''}><GameIcon name={icon} size={26} /></span>{/each}</span>
						<span class="text-sm font-extrabold">{t(`play.hacker.goal.${g.id}`)}</span>
					</button>
				{/each}
			</div>
		{:else if result && result.anyEffect}
			<!-- the headline: what the combination is worth -->
			{#if result.picks.length}
				<div class="flex flex-col gap-2 rounded-2xl bg-[#f3e8ff] px-4 py-3 text-[#6d28d9] dark:bg-[#2e1065] dark:text-[#d8b4fe]">
					<div class="flex flex-wrap gap-x-6 gap-y-3">
						{#each [{ show: wantsX, key: 'xp', icon: 'xp', up: shown.x, gain: result.upX, drop: pct(result.nowGame.exp, result.thenGame.exp), now: result.nowGame.exp, then: result.thenGame.exp }, { show: wantsM, key: 'ping', icon: 'ping', up: shown.m, gain: result.upM, drop: pct(result.nowGame.money, result.thenGame.money), now: result.nowGame.money, then: result.thenGame.money }] as c (c.key)}
							{#if c.show}
								<div class="flex min-w-[12rem] flex-1 basis-0 flex-col gap-1">
									<p class="hacker-up kk-pop-font text-2xl leading-tight">{c.gain > 0 ? t(`play.hacker.up.${c.key}`, { n: c.up.toLocaleString() }) : c.drop > 0 ? t(`play.hacker.down.${c.key}`, { n: c.drop }) : t(`play.hacker.same.${c.key}`)}</p>
									<span class="flex items-center gap-1 text-xs font-bold tabular-nums text-(color:--kk-muted)"><GameIcon name={c.icon} size={14} />{t(`play.hacker.per.${c.key}`)} {fmt(c.now)} → <b class="text-(color:--kk-text)">{fmt(c.then)}</b></span>
								</div>
							{/if}
						{/each}
					</div>
					<span class="text-xs font-bold text-(color:--kk-muted)">{result.played ? t('play.hacker.base.recent', { n: result.played }) : t('play.hacker.base.default')}</span>
				</div>
				<ul class="grid gap-1.5 sm:grid-cols-2">
					{#each result.picks as p, i (p.slot)}
						{@const item = store.shop[p.id]}
						<li style="animation-delay: {500 + i * 260}ms" class="hacker-stomp flex items-center gap-3 rounded-xl border border-(color:--kk-line) bg-(--kk-surface) p-2 shadow-[0_2px_0_var(--kk-line)]">
							<span class="grid size-11 shrink-0 place-items-center rounded-lg bg-(--kk-surface-2)"><ShopThumb id={p.id} shop={store.shop} class="size-9" /></span>
							<span class="min-w-0 flex-1">
								<b class="block truncate text-sm">{nameOf(p.id)}</b>
								<span class="block truncate text-[11px] text-(color:--kk-muted)">{effectText(item)}</span>
							</span>
						</li>
					{/each}
				</ul>
			{/if}
		{/if}

		<div class="flex flex-wrap justify-end gap-2">
			{#if step === 'result'}<button type="button" class="kk-btn inline-flex items-center justify-center gap-1.5" onclick={() => ((step = 'ask'), (result = null))}><Icon name="undo" size={18} />{t('play.hacker.again')}</button>{/if}
			{#if result?.picks?.length}<button type="button" class="kk-btn kk-btn-primary inline-flex items-center justify-center" onclick={apply}>{t('play.hacker.apply')}</button>{/if}
		</div>
	</div>

	{#if banner}
		<!-- the band spans the whole display, like a stage title over the screen. It is a popover (top layer) because the dialog's own pop-in animation would make it the containing block of anything fixed inside it. -->
		<div bind:this={overlay} popover="manual" class="hacker-overlay visible pointer-events-none fixed inset-x-0 top-1/2 bottom-auto m-0 h-44 w-screen max-w-none -translate-y-1/2 overflow-visible border-0 bg-transparent p-0" aria-hidden="true">
			<div class="hacker-band relative h-44 overflow-hidden text-white {banner === 'out' ? 'is-out' : ''}">
				<span class="absolute inset-0 bg-[linear-gradient(90deg,#4338ca,#7c3aed_55%,#a855f7)]"></span>
				<span class="hacker-gold absolute inset-0 bg-[linear-gradient(90deg,#f59e0b,#facc15_50%,#fde047)] {won ? 'on' : ''}"></span>
				<span class="hacker-gray absolute inset-0 bg-[linear-gradient(90deg,#4b5563,#6b7280_50%,#9ca3af)] {lost ? 'on' : ''}"></span>
				<svg class="hacker-tile absolute top-0 left-0 h-full" style="width: calc(100% + {TILE * 2}px)" xmlns="http://www.w3.org/2000/svg">
					<defs>
						<pattern id="hacker-tile" width={TILE} height={TILE} patternUnits="userSpaceOnUse" patternTransform="scale(2)">
							{#each [-TILE, 0, TILE] as dx}{#each [-TILE, 0, TILE] as dy}{#each TILE_SQUARES as sq}<polygon points={sq.map((q) => `${q[0] + dx},${q[1] + dy}`).join(' ')} fill="#fff" fill-opacity="0.16" />{/each}{/each}{/each}
						</pattern>
					</defs>
					<rect width="100%" height="100%" fill="url(#hacker-tile)" />
				</svg>
				<!-- fixed layout: 해커레미 and the title keep their places in every stage (only the text inside changes) -->
				<div class="relative mx-auto h-full max-w-4xl">
					{#if !won && !lost}
						<div class="hacker-rain absolute bottom-0 -left-8 h-full w-[30rem]" aria-hidden="true">
							{#each RAIN as c}
								<span class="hacker-rain-col" style="left: {c.x}%; animation-duration: {c.dur}s; animation-delay: {c.delay}s; font-size: {c.size}rem">{c.text}</span>
							{/each}
						</div>
					{/if}
					<div class="hacker-remi-wrap absolute bottom-[-4rem] left-8 h-48 w-48 {won ? 'jump' : ''} {lost ? 'slump' : ''}">
						<img src={cdnImg('play/hackerremi.png')} alt="" draggable="false" class="hacker-band-remi h-full w-full object-contain" />
						<!-- the lens: a streak of light sweeps over it and a star glints on its corner -->
						{#if !lost}
							<span class="hacker-lens absolute" aria-hidden="true"><span class="hacker-streak"></span></span>
							<i class="hacker-glint {won ? 'big' : ''} absolute not-italic" aria-hidden="true">✦</i>
						{:else}
							<!-- cold sweat running down the temple -->
							{#each [{ x: 74, y: 22, d: 0 }, { x: 80, y: 30, d: 0.35 }, { x: 27, y: 26, d: 0.7 }, { x: 70, y: 18, d: 1.0 }] as d}
								<svg class="hacker-sweat absolute" style="left: {d.x}%; top: {d.y}%; animation-delay: {d.d}s" viewBox="0 0 10 14" width="18" height="25" aria-hidden="true"><path d="M5 .6C5 .6.8 6.2.8 9.1a4.2 4.2 0 0 0 8.4 0C9.2 6.2 5 .6 5 .6Z" fill="#bae6fd" stroke="#38bdf8" stroke-width="1" /><path d="M3 9.4a2.2 2.2 0 0 0 1.6 2" fill="none" stroke="#fff" stroke-width="1" stroke-linecap="round" /></svg>
							{/each}
						{/if}
						{#if won}
							<span class="absolute top-1/3 left-1/2 block size-0">
								{#each BURST as q, i}
									{@const icon = goalPicked === 'both' ? (i % 2 ? 'ping' : 'xp') : goalPicked}
									<i class="hacker-particle absolute block w-max" style="--dx: {q.dx}px; --dy: {q.dy}px; --rot: {q.rot}deg; animation-delay: {q.delay}s"><GameIcon name={icon} size={q.size} /></i>
								{/each}
							</span>
						{/if}
					</div>
					<div class="absolute inset-y-0 right-0 left-60 flex items-center justify-center">
						{#if won}
							<p class="hacker-success kk-pop-font relative text-5xl tracking-tight drop-shadow-[0_3px_0_rgb(0_0_0/0.25)]">
								{t('play.hacker.success')}
								<i class="hacker-spark absolute -top-3 -right-8 text-3xl not-italic">✦</i>
								<i class="hacker-spark absolute top-8 -right-14 text-xl not-italic" style="animation-delay: 0.25s">✦</i>
								<i class="hacker-spark absolute -bottom-1 -left-8 text-2xl not-italic" style="animation-delay: 0.5s">✦</i>
							</p>
						{:else if lost}
							<p class="hacker-success kk-pop-font relative text-5xl tracking-tight drop-shadow-[0_3px_0_rgb(0_0_0/0.3)]">{t('play.hacker.fail')}</p>
						{:else}
							<p class="hacker-text kk-pop-font relative text-5xl tracking-tight drop-shadow-[0_3px_0_rgb(0_0_0/0.25)]">{t('play.hacker.hacking')}<span class="hacker-dots" aria-hidden="true"><i>.</i><i>.</i><i>.</i></span></p>
						{/if}
					</div>
				</div>
			</div>
		</div>
	{/if}
</Dialog>

<style>
	/* the banner: the band wipes in from the left (band and text are clipped together, so the text is masked by the growing width) */
	.hacker-band {
		box-shadow: 0 -2px 0 rgb(0 0 0 / 0.55), 0 2px 0 rgb(0 0 0 / 0.55);
		animation: hacker-wipe 0.7s cubic-bezier(0.22, 1, 0.36, 1) both;
	}
	.hacker-band.is-out {
		animation: hacker-wipe-out 0.42s cubic-bezier(0.64, 0, 0.78, 0) both;
	}
	.hacker-gold {
		opacity: 0;
		transition: opacity 0.25s ease-out;
	}
	.hacker-gold.on {
		opacity: 1;
	}
	.hacker-gray {
		opacity: 0;
		transition: opacity 0.25s ease-out;
	}
	.hacker-gray.on {
		opacity: 1;
	}
	.hacker-tile {
		animation: hacker-drift 4s linear infinite;
	}
	.hacker-tile {
		will-change: transform;
	}
	.hacker-remi-wrap {
		will-change: transform;
		transform-origin: 50% 100%;
	}
	/* 해킹 성공: a hop with a squash on landing, then a smaller second one */
	.hacker-remi-wrap.slump {
		animation: hacker-slump 0.8s cubic-bezier(0.3, 0, 0.4, 1) both;
	}
	.hacker-remi-wrap.slump .hacker-band-remi {
		filter: grayscale(0.55) brightness(0.92);
		transition: filter 0.4s;
	}
	.hacker-sweat {
		opacity: 0;
		animation: hacker-sweat 1.1s ease-in infinite;
	}
	.hacker-remi-wrap.jump {
		animation: hacker-jump 0.9s cubic-bezier(0.3, 0, 0.4, 1) both;
	}
	/* matrix rain: glyph columns fall at their own speeds, bright at the head and fading up the tail, masked to a soft patch around 해커레미 */
	.hacker-rain {
		-webkit-mask-image: radial-gradient(ellipse 65% 80% at 45% 55%, #000 35%, transparent 100%);
		mask-image: radial-gradient(ellipse 65% 80% at 45% 55%, #000 35%, transparent 100%);
	}
	.hacker-rain-col {
		position: absolute;
		top: 0;
		white-space: pre;
		line-height: 1.05;
		font-family: ui-monospace, 'SF Mono', Menlo, monospace;
		font-weight: 800;
		text-align: center;
		color: transparent;
		background: linear-gradient(to bottom, transparent, rgb(255 255 255 / 0.75) 45%, #fff);
		-webkit-background-clip: text;
		background-clip: text;
		will-change: transform;
		animation: hacker-rain 1.6s linear infinite;
	}
	/* the lens sits at about 69% / 30% of the picture */
	.hacker-lens {
		left: 61%;
		top: 22%;
		width: 16%;
		height: 16%;
		overflow: hidden;
		border-radius: 14%;
		transform: rotate(-8deg);
	}
	.hacker-streak {
		position: absolute;
		top: -30%;
		bottom: -30%;
		width: 38%;
		background: linear-gradient(90deg, transparent, rgb(255 255 255 / 0.95), transparent);
		transform: skewX(-22deg) translateX(-160%);
		mix-blend-mode: screen;
		animation: hacker-streak 1.3s ease-in-out infinite;
	}
	.hacker-glint {
		left: 72%;
		top: 21%;
		font-size: 1.7rem;
		line-height: 1;
		color: #fff;
		text-shadow: 0 0 8px #fff, 0 0 16px rgb(190 220 255 / 0.95);
		transform: translate(-50%, -50%) scale(0);
		animation: hacker-glint 1.3s ease-in-out infinite;
		animation-delay: 0.35s;
	}
	.hacker-glint.big {
		font-size: 2.6rem;
		animation-duration: 0.9s;
	}
	.hacker-particle {
		animation: hacker-burst 1.15s cubic-bezier(0.2, 0.7, 0.3, 1) both;
		filter: drop-shadow(0 0 6px rgb(255 255 255 / 0.9));
	}
	.hacker-band-remi {
		animation: hacker-remi 0.55s cubic-bezier(0.34, 1.6, 0.64, 1) 0.6s both;
	}
	/* the title enters de-skewing from the left, and leaves skewing off to the right */
	.hacker-text {
		animation: hacker-text-in 0.5s cubic-bezier(0.22, 1, 0.36, 1) 0.35s both;
	}
	.hacker-success {
		animation: hacker-text-in 0.5s cubic-bezier(0.22, 1, 0.36, 1) both;
	}
	.hacker-band.is-out .hacker-text,
	.hacker-band.is-out .hacker-success {
		animation: hacker-text-out 0.38s cubic-bezier(0.64, 0, 0.78, 0) both;
	}
	.hacker-spark {
		animation: hacker-twinkle 0.9s ease-in-out infinite;
		color: #fff;
		text-shadow: 0 0 10px rgb(255 255 255 / 0.9);
	}
	.hacker-dots i {
		font-style: normal;
		animation: hacker-blink 1s steps(1) infinite;
	}
	.hacker-dots i:nth-child(2) { animation-delay: 0.2s; }
	.hacker-dots i:nth-child(3) { animation-delay: 0.4s; }
	/* the items land one by one: they drop in, squash once, and flash a glow */
	.hacker-stomp {
		animation: hacker-stomp 0.55s cubic-bezier(0.34, 1.5, 0.64, 1) both;
	}
	@keyframes hacker-wipe {
		from { clip-path: inset(0 100% 0 0); opacity: 0; }
		to { clip-path: inset(0 0 0 0); opacity: 1; }
	}
	@keyframes hacker-wipe-out {
		from { clip-path: inset(0 0 0 0); opacity: 1; }
		to { clip-path: inset(0 0 0 100%); opacity: 0; }
	}
	@keyframes hacker-drift { to { transform: translate3d(-400px, 0, 0); } }
	@keyframes hacker-streak {
		0%, 30% { transform: skewX(-22deg) translateX(-160%); }
		70%, 100% { transform: skewX(-22deg) translateX(420%); }
	}
	@keyframes hacker-glint {
		0%, 25% { opacity: 0; transform: translate(-50%, -50%) scale(0) rotate(0); }
		45% { opacity: 1; transform: translate(-50%, -50%) scale(1.15) rotate(25deg); }
		65%, 100% { opacity: 0; transform: translate(-50%, -50%) scale(0.2) rotate(70deg); }
	}
	@keyframes hacker-slump {
		0% { transform: none; }
		35% { transform: translateY(10px) scale(1.04, 0.93) rotate(-2deg); }
		55% { transform: translateY(6px) scale(1.02, 0.96) rotate(1.5deg); }
		100% { transform: translateY(8px) scale(1.03, 0.94) rotate(-1deg); }
	}
	@keyframes hacker-sweat {
		0% { opacity: 0; transform: translateY(-4px) scale(0.5); }
		20% { opacity: 1; transform: translateY(0) scale(1); }
		100% { opacity: 0; transform: translateY(2.6rem) scale(0.9); }
	}
	@keyframes hacker-rain { from { transform: translate3d(0, -100%, 0); } to { transform: translate3d(0, 11rem, 0); } }
	@keyframes hacker-jump {
		0% { transform: none; }
		28% { transform: translateY(-38px) scale(0.96, 1.07); }
		50% { transform: translateY(0) scale(1.08, 0.9); }
		68% { transform: translateY(-14px) scale(0.98, 1.03); }
		84% { transform: translateY(0) scale(1.03, 0.97); }
		100% { transform: none; }
	}
	@keyframes hacker-burst {
		0% { opacity: 0; transform: translate(-50%, -50%) scale(0.3) rotate(0); }
		12% { opacity: 1; }
		70% { opacity: 1; }
		100% { opacity: 0; transform: translate(calc(-50% + var(--dx)), calc(-50% + var(--dy) + 40px)) scale(1) rotate(var(--rot)); }
	}
	@keyframes hacker-text-in {
		from { opacity: 0; transform: translateX(-56px) skewX(-24deg); }
		to { opacity: 1; transform: none; }
	}
	@keyframes hacker-text-out {
		from { opacity: 1; transform: none; }
		to { opacity: 0; transform: translateX(72px) skewX(-24deg); }
	}
	@keyframes hacker-twinkle { 0%, 100% { opacity: 0.2; transform: scale(0.6) rotate(0); } 50% { opacity: 1; transform: scale(1.15) rotate(20deg); } }
	@keyframes hacker-remi {
		from { transform: translateY(110%) scale(0.8); opacity: 0; }
		to { transform: none; opacity: 1; }
	}
	@keyframes hacker-blink { 0%, 100% { opacity: 1; } 50% { opacity: 0.15; } }
	@keyframes hacker-stomp {
		0% { opacity: 0; transform: translateY(-14px) scale(1.35); box-shadow: 0 0 0 0 rgb(168 85 247 / 0); }
		55% { opacity: 1; transform: scale(0.96); box-shadow: 0 0 22px 6px rgb(168 85 247 / 0.65), 0 2px 0 var(--kk-line); }
		100% { opacity: 1; transform: none; box-shadow: 0 0 0 0 rgb(168 85 247 / 0), 0 2px 0 var(--kk-line); }
	}
	.hacker-up {
		animation: kk-pop-in 0.45s cubic-bezier(0.34, 1.7, 0.64, 1) both;
	}
	@media (prefers-reduced-motion: reduce) {
		.hacker-band, .hacker-tile, .hacker-rain-col, .hacker-particle, .hacker-streak, .hacker-glint, .hacker-remi-wrap, .hacker-sweat, .hacker-band-remi, .hacker-text, .hacker-success, .hacker-spark, .hacker-dots i, .hacker-stomp,
		.hacker-up {
			animation: none;
		}
	}
</style>
