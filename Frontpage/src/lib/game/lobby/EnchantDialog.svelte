<script>
	import { t } from '../i18n.svelte.js';
	import { cleanBox } from '../protocol.js';
	import { ODDS_URL, copiesOf, applyCopyState, fetchEnchant, tryEnchant, resolveEnchant, buyTicket, ownedCount, lineText, chanceAfter, fakeLine } from '../enchant.js';
	import { reducedMotion } from '../motion.js';
	import { toast } from '../ui/toast.svelte.js';
	import { playSound } from '../audio.svelte.js';
	import Tooltip from '../ui/Tooltip.svelte';
	import Dialog from './Dialog.svelte';
	import GameIcon from './GameIcon.svelte';
	import ShopThumb from './ShopThumb.svelte';
	import NewsExchangeDialog from './NewsExchangeDialog.svelte';

	/**
	 * 강화: pick one copy of an owned avatar item on the left (every copy is listed on its own; an enchanted one carries its +level), then on the right spend a 강화 티켓 to try to put a random line on it. The
	 * stage shows the item, how many of its lines are filled and what they are; below are the success chance, the tickets (일반 /
	 * 고급 / 희귀, shop items bought here at the shop's price after a question) and the use button.
	 * 일반 can fail, but each failure in a row raises the chance of the next one (it starts again after a success); 고급 / 희귀 always
	 * succeed and may / always draw from the best values. A success on a full item waits until it replaces a line or is thrown away.
	 * The server (`/enchant`) rolls and spends the ticket; this only shows what it sends back, and plays the animation while it does.
	 * @type {{open:boolean, store:any, entries:{item:any,count:number}[], details?:any, nameOf:(id:string)=>string}}
	 */
	let { open = $bindable(false), store, entries, details = null, nameOf } = $props();

	const CHARGE_MS = 1700; // the ticket is "being used"
	const RESULT_MS = 1300; // the verdict stays on the stage

	let data = $state(null); // { rules, effects, items: { [item]: { pity, chance, copies: [{ instance, level, lines, pending, worn }] } } }
	let failed = $state(null); // why the data could not be loaded: the server's error key, or 'network'
	let picked = $state(null); // the key of the copy: `item#instance` for an enchanted one, `item#p<n>` for a plain one
	let ticketId = $state(null);
	let busy = $state(false);
	let phase = $state('idle'); // idle | charge | success | fail
	let reel = $state(null); // the made-up line that spins during the charge
	let verdict = $state(null); // { success, line } while the verdict is on the stage
	let buyOpen = $state(false);
	let run = 0;

	const rules = $derived(data?.rules);
	const units = $derived(data?.effects ?? {});
	const copies = $derived(copiesOf(entries, rules?.groups, data?.items, nameOf));
	const cur = $derived(picked ? (copies.find((c) => c.key === picked) ?? null) : null);
	const pickedItem = $derived(cur?.item._id ?? null);
	const tickets = $derived(rules?.tickets ?? []);
	const ticket = $derived(tickets.find((x) => x.id === ticketId) ?? tickets[0] ?? null);
	// the copy with the failures in a row of its kind of item (its copies share them)
	const state = $derived(cur ? { ...cur, pity: data?.items?.[pickedItem]?.pity ?? 0, chance: data?.items?.[pickedItem]?.chance ?? rules?.baseChance ?? 30 } : null);
	const money = $derived(store.me?.money ?? 0);
	const owned = (id) => ownedCount(store.net.box, id);
	const price = (id) => store.shop[id]?.cost;
	const fmt = (n) => Number(n).toLocaleString();
	const text = (line) => lineText(line, units, t);
	const errorText = (key) => t(`play.enchant.error.${key}`) || t('play.enchant.error.bad');
	// the chance of the ticket in hand: a ticket that always succeeds says 100, 일반 follows the item's failures in a row
	const chance = $derived(!ticket ? 0 : ticket.guaranteed ? 100 : (state?.chance ?? rules?.baseChance ?? 30));
	const slots = $derived(Array.from({ length: rules?.maxLines ?? 3 }, (_, i) => state?.lines?.[i]));
	const wait = (ms) => new Promise((r) => setTimeout(r, reducedMotion() ? 0 : Math.max(0, ms)));

	$effect(() => {
		if (!open) return;
		run++;
		phase = 'idle';
		verdict = null;
		failed = null;
		fetchEnchant().then((res) => {
			if (res.error) return void (failed = res.error);
			data = res;
			if (!ticketId) ticketId = res.rules.tickets.find((x) => owned(x.id) > 0)?.id ?? res.rules.tickets[0]?.id ?? null;
		});
	});
	$effect(() => {
		if (picked && !copies.some((c) => c.key === picked)) picked = null;
	});
	// while a try is in flight a made-up line spins on the stage
	$effect(() => {
		if (phase !== 'charge') return;
		reel = fakeLine(units);
		const id = setInterval(() => (reel = fakeLine(units)), 110);
		return () => clearInterval(id);
	});

	// the box (and ping) as the server now has them; the game server is told to read them again
	function takeBox(box, ping) {
		store.net.box = cleanBox(box);
		store.net.boxSeq++;
		if (ping != null) {
			const mine = store.net.users[store.net.id];
			if (mine) mine.money = ping;
		}
		store.conn.send('refresh');
	}

	async function use() {
		if (busy || !cur || !ticket) return;
		if (owned(ticket.id) < 1) return void toast(errorText('noTicket'), { kind: 'error' });
		busy = true;
		const target = cur;
		const mine = ++run;
		const t0 = performance.now();
		phase = 'charge';
		verdict = null;
		playSound('dialog', { ui: true });
		const res = await tryEnchant(target.item._id, target.instance, ticket.id);
		await wait(CHARGE_MS - (performance.now() - t0));
		if (mine !== run) return void (busy = false);
		if (res.error) {
			phase = 'idle';
			busy = false;
			return void toast(errorText(res.error), { kind: 'error' });
		}
		data = { ...data, items: applyCopyState(data.items, res.item, res.state) };
		if (res.state.instance > 0) picked = `${res.item}#${res.state.instance}`; // a plain copy that succeeded is an enchanted one now
		takeBox(res.box);
		verdict = { success: res.success, line: res.line };
		phase = res.success ? 'success' : 'fail';
		playSound(res.success ? 'success' : 'fail');
		await wait(RESULT_MS);
		if (mine === run) {
			phase = 'idle';
			verdict = null;
		}
		busy = false;
	}

	async function settle(slot) {
		if (busy || !cur) return;
		busy = true;
		const res = await resolveEnchant(cur.item._id, cur.instance, slot);
		busy = false;
		if (res.error) return void toast(errorText(res.error), { kind: 'error' });
		data = { ...data, items: applyCopyState(data.items, res.item, res.state) };
	}

	async function buy() {
		if (busy || !ticket) return;
		busy = true;
		const res = await buyTicket(ticket.id);
		busy = false;
		if (res.error) return void toast(res.error === 400 || res.error === 'network' ? t('play.enchant.error.buy') : errorText(res.error), { kind: 'error' });
		takeBox(res.box, res.money);
		playSound('success', { ui: true });
	}

	// the question before buying: the same popup as the exchange of the 행사 pages
	const buyExchange = $derived(ticket && price(ticket.id) != null ? { cost: { ping: Number(price(ticket.id)) }, gives: { [ticket.id]: 1 } } : null);
	const have = (c) => (c.kind === 'ping' ? money : owned(c.id));
	const chanceInfo = $derived(
		!ticket
			? ''
			: ticket.guaranteed
				? `${t('play.enchant.sure')}${ticket.topTierChance ? ` · ${t('play.enchant.top', { chance: ticket.topTierChance })}` : ''}`
				: state?.pity
					? t('play.enchant.pityNext', { pity: state.pity, next: chanceAfter(state.pity + 1, rules) })
					: t('play.enchant.pityRule', { step: rules?.pityStep ?? 10 })
	);
	const ticketInfo = (tk) => `${t(`play.enchant.ticketDesc.${tk.tier}`)}`;
	const burst = Array.from({ length: 14 }, (_, i) => ({ a: (i / 14) * 360, d: 70 + (i % 3) * 22, s: 10 + (i % 4) * 4, t: (i % 5) * 0.04 }));
</script>

{#snippet info(body)}
	<Tooltip text={body} placement="top">
		<button type="button" class="grid size-5 place-items-center rounded-full border border-(color:--kk-line-strong) bg-(--kk-surface) text-[11px] leading-none font-black text-(color:--kk-muted) hover:text-(color:--kk-text)" aria-label={t('play.enchant.info')}>i</button>
	</Tooltip>
{/snippet}

<Dialog bind:open title={t('play.lab.enhance')} size="lg">
	{#if failed}
		<p class="grid place-items-center rounded-xl bg-(--kk-surface-2) px-3 py-6 text-center text-sm text-(color:--kk-muted) md:h-[min(37rem,78vh)]" role="status">{failed === 'guest' ? t('play.enchant.error.guest') : t('play.enchant.loadFailed')}</p>
	{:else if !data}
		<p class="grid place-items-center py-6 text-center text-sm text-(color:--kk-muted) md:h-[min(37rem,78vh)]" role="status">{t('kkutu.js.loading')}</p>
	{:else}
		<!-- one height for every state (nothing picked, a try running, a success waiting): the columns fill it and scroll inside it -->
		<div class="grid gap-3 md:h-[min(37rem,78vh)] md:grid-cols-[12.5rem_minmax(0,1fr)]">
			<!-- the items that can carry lines -->
			<section class="flex max-h-60 min-w-0 flex-col gap-2 rounded-2xl bg-(--kk-surface-2) p-2.5 md:max-h-none md:min-h-0">
				<div class="flex items-center justify-between gap-2 px-1">
					<h3 class="min-w-0 truncate text-xs font-extrabold tracking-wide text-(color:--kk-muted)">{t('play.enchant.pick')}</h3>
					<!-- the odds are published on a page of their own -->
					<a href={ODDS_URL} target="_blank" rel="noopener noreferrer" class="shrink-0 rounded-full border border-(color:--kk-line-strong) bg-(--kk-surface) px-2 py-0.5 text-[11px] font-bold text-(color:--kk-muted) hover:text-(color:--kk-text)">{t('play.enchant.odds')}</a>
				</div>
				{#if !copies.length}
					<p class="px-1 py-6 text-center text-xs text-(color:--kk-muted)">{t('play.enchant.none')}</p>
				{:else}
					<div class="grid min-h-0 flex-1 grid-cols-[repeat(auto-fill,minmax(3.4rem,1fr))] content-start gap-1.5 overflow-y-auto p-0.5">
						{#each copies as c (c.key)}
							<button type="button" disabled={busy} onclick={() => (picked = c.key)} aria-pressed={picked === c.key} aria-label="{nameOf(c.item._id)}{c.level ? ` +${c.level}` : ''}" class="relative grid aspect-square place-items-center rounded-lg border-2 bg-(--kk-surface) transition-transform hover:-translate-y-0.5 {picked === c.key ? 'border-(--kk-accent-strong) shadow-[0_0_0_3px_color-mix(in_srgb,var(--kk-accent-strong)_25%,transparent)]' : 'border-(color:--kk-line)'}">
								<ShopThumb id={c.item._id} shop={store.shop} class="size-9 text-base" />
								{#if c.level}<span class="absolute top-0 left-0 rounded-md bg-amber-400 px-1 text-[10px] leading-4 font-extrabold text-slate-900 tabular-nums">+{c.level}</span>{/if}
								{#if c.pending}<span class="absolute -right-1 -bottom-1 grid size-4 place-items-center rounded-full bg-(--kk-accent-strong) text-[10px] font-black text-white" aria-hidden="true">!</span>{/if}
							</button>
						{/each}
					</div>
				{/if}
			</section>

			<div class="flex min-w-0 flex-col gap-2.5 md:min-h-0 md:overflow-x-hidden md:overflow-y-auto md:pr-0.5">
				<!-- the stage: the item in a dashed frame, its name, its lines filled so far; the animation plays over it -->
				<section class="stage relative flex flex-col items-center gap-2 overflow-hidden rounded-2xl border border-(color:--kk-line) bg-(--kk-surface-2) px-4 pt-3 pb-2.5 {phase === 'fail' ? 'stage-shake' : ''}" data-phase={phase} aria-live="polite">
					{#if !state}
						<p class="py-12 text-center text-sm text-(color:--kk-muted)">{t('play.enchant.hint')}</p>
					{:else}
						<div class="relative grid size-24 place-items-center rounded-2xl border-2 border-dashed border-(color:--kk-line-strong) bg-(--kk-surface) {phase === 'charge' ? 'frame-charge' : ''} {phase === 'success' ? 'frame-win' : ''} {phase === 'fail' ? 'frame-lose' : ''}">
							<ShopThumb id={pickedItem} shop={store.shop} class="size-16 text-2xl" />
							{#if phase === 'charge' && ticket}
								<span class="ticket-fly absolute -top-3 -right-5 size-16" aria-hidden="true"><ShopThumb id={ticket.id} shop={store.shop} class="size-16" /></span>
							{/if}
							{#if phase === 'success'}
								<div class="pointer-events-none absolute inset-0" aria-hidden="true">
									{#each burst as b, i (i)}<i class="burst absolute top-1/2 left-1/2 text-amber-400 not-italic" style="--a:{b.a}deg; --d:{b.d}px; font-size:{b.s}px; animation-delay:{b.t}s">✦</i>{/each}
								</div>
							{/if}
							{#if phase === 'fail'}<span class="absolute inset-0 grid place-items-center text-5xl font-black text-red-500/80" aria-hidden="true">✕</span>{/if}
						</div>
						<div class="w-full max-w-xs truncate rounded-full bg-(--kk-surface) px-4 py-1.5 text-center text-sm font-extrabold shadow-[0_2px_0_var(--kk-line)]">{nameOf(pickedItem)}{#if state.level} <b class="text-amber-500">+{state.level}</b>{/if}</div>

						<!-- the lines: filled slots show pips like the scroll slots of a spell book -->
						<div class="flex items-center gap-1.5" role="img" aria-label="{state.lines.length}/{rules.maxLines}">
							{#each slots as line, i (i)}<span class="size-3 rounded-sm border border-(color:--kk-line-strong) {line ? (line.premium ? 'bg-amber-400' : 'bg-(--kk-accent-strong)') : 'bg-(--kk-surface)'}"></span>{/each}
						</div>

						<div class="min-h-[7.5rem] w-full">
							{#if phase === 'charge'}
								<p class="reel mt-2 rounded-xl bg-(--kk-surface) px-3 py-3 text-center text-sm font-extrabold text-(color:--kk-muted)">{reel ? text(reel) : '…'}</p>
								<p class="mt-2 text-center text-xs font-bold text-(color:--kk-muted)">{t('play.enchant.charging')}</p>
							{:else if verdict}
								<p class="verdict mt-2 rounded-xl px-3 py-3 text-center text-sm font-extrabold {verdict.success ? 'bg-green-500/15 text-green-600' : 'bg-red-500/10 text-red-500'}" role="status">
									{#if verdict.success}{#if verdict.line.premium}<span class="text-amber-500">★ </span>{/if}{text(verdict.line)}{:else}{t('play.enchant.fail')}{/if}
								</p>
							{:else}
								<ul class="flex flex-col gap-1.5" aria-label={t('play.enchant.lines')}>
									{#each slots as line, i (i)}
										<li class="flex min-h-9 items-center gap-2 rounded-xl bg-(--kk-surface) px-3 py-1.5 text-sm {line ? 'font-bold' : 'text-(color:--kk-muted)'}">
											{#if line}
												{#if line.premium}<span class="text-amber-500" title={t('play.enchant.premiumLine')} aria-label={t('play.enchant.premiumLine')}>★</span>{/if}
												<span class="min-w-0 flex-1">{text(line)}</span>
												{#if state.pending}<button type="button" class="kk-btn min-h-7 px-2.5 text-xs" disabled={busy} onclick={() => settle(i)}>{t('play.enchant.replace')}</button>{/if}
											{:else}
												{t('play.enchant.empty')}
											{/if}
										</li>
									{/each}
								</ul>
							{/if}
						</div>
					{/if}
				</section>

				{#if state}
					{#if state.pending}
						<section class="rounded-2xl border-2 border-(--kk-accent-strong) px-3 py-2.5" role="status">
							<p class="text-xs font-bold text-(color:--kk-muted)">{t('play.enchant.pending')}</p>
							<p class="my-1 text-sm font-extrabold">{#if state.pending.premium}<span class="text-amber-500">★ </span>{/if}{text(state.pending)}</p>
							<button type="button" class="kk-btn min-h-9 px-4 text-sm" disabled={busy} onclick={() => settle(null)}>{t('play.enchant.discard')}</button>
						</section>
					{:else}
						<!-- the success chance of the ticket in hand; how it works is behind the (i) -->
						<section class="rounded-2xl border border-(color:--kk-line) bg-(--kk-surface-2) px-3 py-2">
							<div class="flex items-center justify-between text-xs font-bold text-(color:--kk-muted)">
								<span class="inline-flex items-center gap-1">{t('play.enchant.chance')}{@render info(chanceInfo)}</span>
								<span class="text-lg font-extrabold text-(color:--kk-text) tabular-nums">{chance}%</span>
							</div>
							<div class="mt-1 h-2 overflow-hidden rounded-full bg-(--kk-surface)" role="progressbar" aria-valuemin="0" aria-valuemax="100" aria-valuenow={chance} aria-label={t('play.enchant.chance')}>
								<div class="h-full rounded-full bg-(--kk-accent-strong) transition-[width] duration-500" style="width:{chance}%"></div>
							</div>
						</section>

						<!-- the tickets, one column each: pick one; the (i) at its top right says what it does -->
						<ul class="grid grid-cols-3 gap-2" role="radiogroup" aria-label={t('play.enchant.tickets')}>
							{#each tickets as tk (tk.id)}
								<li class="relative">
									<button type="button" role="radio" aria-checked={ticketId === tk.id} disabled={busy} onclick={() => (ticketId = tk.id)} class="flex w-full flex-col items-center gap-0.5 rounded-xl border-2 px-1.5 pt-2.5 pb-1.5 transition-colors {ticketId === tk.id ? 'border-(--kk-accent-strong) bg-(--kk-accent-strong)/10' : 'border-(color:--kk-line) bg-(--kk-surface-2)'}">
										<ShopThumb id={tk.id} shop={store.shop} class="size-10" />
										<span class="w-full truncate text-center text-xs font-extrabold">{t(`play.enchant.ticket.${tk.tier}`)}</span>
										<span class="text-[11px] font-bold tabular-nums {owned(tk.id) ? '' : 'text-(color:--kk-muted)'}">{t('play.enchant.owned', { n: owned(tk.id) })}</span>
									</button>
									<span class="absolute top-1 right-1">{@render info(ticketInfo(tk))}</span>
								</li>
							{/each}
						</ul>

						<!-- one button for both: use a ticket, or buy one when there is none (the price is the shop's; a question comes first) -->
						{#if ticket && owned(ticket.id) < 1}
							<button type="button" class="kk-btn kk-btn-primary nudge flex min-h-12 w-full items-center justify-center gap-2.5 px-6 text-base font-extrabold" disabled={busy || !buyExchange || money < price(ticket.id)} onclick={() => (buyOpen = true)} aria-label="{t('play.enchant.buy')} {price(ticket.id) != null ? fmt(price(ticket.id)) : ''}{buyExchange && money < price(ticket.id) ? `, ${t('play.lab.short')}` : ''}">
								<span>{t('play.enchant.buy')}</span>
								{#if price(ticket.id) != null}<span class="inline-flex items-center gap-1 rounded-full bg-black/20 py-0.5 pr-3 pl-1.5 text-base font-extrabold tabular-nums {money < price(ticket.id) ? 'text-red-200' : ''}" title={money < price(ticket.id) ? t('play.lab.short') : undefined}><GameIcon name="ping" size={22} />{fmt(price(ticket.id))}</span>{/if}
							</button>
						{:else}
							<button type="button" class="kk-btn kk-btn-primary nudge flex min-h-12 w-full items-center justify-center gap-2.5 px-6 text-base font-extrabold" disabled={busy || !ticket} onclick={use}>
								<span>{t('play.enchant.use')}</span>
								{#if ticket}<span class="inline-flex items-center gap-1 rounded-full bg-black/20 py-0.5 pr-3 pl-1 text-base font-extrabold tabular-nums"><ShopThumb id={ticket.id} shop={store.shop} class="size-6" />×{owned(ticket.id)}</span>{/if}
							</button>
						{/if}
					{/if}
				{/if}
			</div>
		</div>
	{/if}
</Dialog>

{#if ticket}
	<NewsExchangeDialog bind:open={buyOpen} exchange={buyExchange} title={t(`play.enchant.ticket.${ticket.tier}`)} shop={store.shop} {details} {have} onconfirm={buy} dialogTitle={t('play.enchant.buyDialog')} doLabel={t('play.enchant.buy')} />
{/if}

<style>
	/* the main button nudges with a shine that sweeps across now and then (a transform only: it never touches layout or the hover paint) */
	.nudge:not(:disabled) {
		position: relative;
		overflow: hidden;
	}
	.nudge:not(:disabled)::after {
		content: '';
		position: absolute;
		inset: 0 auto 0 0;
		width: 30%;
		background: linear-gradient(100deg, transparent, rgb(255 255 255 / 0.55), transparent);
		transform: translateX(-160%) skewX(-20deg);
		animation: enc-shine 2.8s ease-in-out infinite;
		pointer-events: none;
	}
	@keyframes enc-shine {
		0%,
		55% {
			transform: translateX(-160%) skewX(-20deg);
		}
		100% {
			transform: translateX(450%) skewX(-20deg);
		}
	}
	/* the frame hums while the ticket is being used, flashes on a success, dims on a failure */
	.frame-charge {
		animation: enc-hum 0.5s ease-in-out infinite alternate;
	}
	.frame-win {
		animation: enc-flash 0.9s ease-out both;
	}
	.frame-lose {
		filter: grayscale(0.7);
	}
	.stage-shake {
		animation: enc-shake 0.5s cubic-bezier(0.36, 0.07, 0.19, 0.97) both;
	}
	.ticket-fly {
		animation: enc-fly 1.7s cubic-bezier(0.22, 1, 0.36, 1) both;
		filter: drop-shadow(0 4px 6px rgb(0 0 0 / 0.3));
	}
	.reel {
		animation: enc-pulse 0.4s ease-in-out infinite alternate;
	}
	.verdict {
		animation: kk-pop-in 0.35s cubic-bezier(0.34, 1.7, 0.64, 1) both;
	}
	.burst {
		opacity: 0;
		animation: enc-burst 1s cubic-bezier(0.2, 0.7, 0.3, 1) both;
	}
	@keyframes enc-hum {
		from {
			transform: scale(1);
			box-shadow: 0 0 0 0 color-mix(in srgb, var(--kk-accent-strong) 0%, transparent);
		}
		to {
			transform: scale(1.06);
			box-shadow: 0 0 22px 6px color-mix(in srgb, var(--kk-accent-strong) 55%, transparent);
		}
	}
	@keyframes enc-flash {
		0% {
			transform: scale(1);
			box-shadow: 0 0 0 0 #fde047;
		}
		30% {
			transform: scale(1.14);
			box-shadow: 0 0 40px 16px #fde047;
		}
		100% {
			transform: scale(1);
			box-shadow: 0 0 12px 2px color-mix(in srgb, #fde047 60%, transparent);
		}
	}
	@keyframes enc-shake {
		10%,
		90% {
			transform: translateX(-2px);
		}
		20%,
		80% {
			transform: translateX(4px);
		}
		30%,
		50%,
		70% {
			transform: translateX(-6px);
		}
		40%,
		60% {
			transform: translateX(6px);
		}
	}
	@keyframes enc-fly {
		0% {
			opacity: 0;
			transform: translate(70px, -50px) rotate(25deg) scale(0.6);
		}
		25% {
			opacity: 1;
			transform: translate(0, 0) rotate(-8deg) scale(1);
		}
		85% {
			opacity: 1;
			transform: translate(-30px, 22px) rotate(8deg) scale(0.9);
		}
		100% {
			opacity: 0;
			transform: translate(-48px, 34px) rotate(14deg) scale(0.4);
		}
	}
	@keyframes enc-pulse {
		from {
			opacity: 0.55;
		}
		to {
			opacity: 1;
		}
	}
	@keyframes enc-burst {
		0% {
			opacity: 1;
			transform: translate(-50%, -50%) rotate(var(--a)) translateX(0) scale(0.4);
		}
		100% {
			opacity: 0;
			transform: translate(-50%, -50%) rotate(var(--a)) translateX(var(--d)) scale(1.2);
		}
	}
	@media (prefers-reduced-motion: reduce) {
		.frame-charge,
		.frame-win,
		.stage-shake,
		.ticket-fly,
		.reel,
		.verdict,
		.burst,
		.nudge:not(:disabled)::after {
			animation: none;
		}
		.burst {
			opacity: 0;
		}
	}
</style>
