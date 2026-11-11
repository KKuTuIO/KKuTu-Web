<script>
	import { t } from '../i18n.svelte.js';
	import { cdnImg } from '../../cdn.js';
	import { plainText, cleanBox } from '../protocol.js';
	import { dynItem } from '../shop.js';
	import { lookupWord } from '../dictionary.js';
	import { formMean } from '../wordMean.js';
	import { analyzeTray, addError, ownedPieces, fetchPreview, composeTray, boxGains, rewardCount, pieceKind, pieceChar, MAX_TRAY } from '../charFactory.js';
	import { confirm } from '../ui/confirm.svelte.js';
	import { toast } from '../ui/toast.svelte.js';
	import { playSound } from '../audio.svelte.js';
	import Select from '../ui/Select.svelte';
	import Dialog from './Dialog.svelte';
	import GameIcon from './GameIcon.svelte';
	import ObtainScreen from './ObtainScreen.svelte';
	import ShopThumb from './ShopThumb.svelte';

	/**
	 * 글자 조합소 (the legacy char factory, in the layout of the old lab mock-up): pick up to six letter pieces, see the word they make
	 * with its meaning, what the combination can give and what it costs, and combine. The server (`/cf`) judges everything; the
	 * preview asks the same dictionary and the same reward table.
	 * @type {{open:boolean, store:any, entries:{item:any,count:number,expire?:number}[], details:Record<string,any>, nameOf:(id:string)=>string}}
	 */
	let { open = $bindable(false), store, entries, details, nameOf } = $props();
	let tray = $state([]); // piece keys in the order picked
	let sort = $state('high');
	let preview = $state({ status: 'idle' }); // idle | loading | ok | fail
	let busy = $state(false);
	let resultOpen = $state(false);
	let prizes = $state([]);
	let run = 0;

	const sortOptions = $derived(['high', 'low', 'name'].map((v) => ({ value: v, label: t(`play.lab.sort.${v}`) })));
	const pieces = $derived(ownedPieces(entries, sort));
	const inTray = (key) => tray.filter((k) => k === key).length;
	const word = $derived(analyzeTray(tray).word);
	const money = $derived(store.me?.money ?? 0);

	$effect(() => {
		if (open) reset();
	});

	function reset() {
		tray = [];
		preview = { status: 'idle' };
		run++;
	}

	function pick(e) {
		const err = addError(tray, e.item._id, e.count);
		if (err) return void toast(plainText(store.net.meta.labels[`kkutu.js.error.${err}`] ?? ''), { kind: 'error' });
		playSound('dialog', { ui: true });
		tray = [...tray, e.item._id];
	}
	function unpick(i) {
		tray = tray.filter((_, j) => j !== i);
	}

	// Whenever the tray changes: is the word in the dictionary (or fusible), and what does it give.
	$effect(() => {
		const keys = [...tray];
		const mine = ++run;
		if (!keys.length) return void (preview = { status: 'idle' });
		const a = analyzeTray(keys);
		if (a.malformed) return void (preview = { status: 'fail', msg: t('play.lab.tip.mixed') });
		preview = { status: 'loading' };
		(async () => {
			const dict = await lookupWord(a.word);
			if (mine !== run) return;
			let blend = false;
			if (dict.error) {
				if (a.word.length !== 2) return void (preview = { status: 'fail', msg: t('play.lab.tip.no') });
				blend = true;
			}
			const rew = await fetchPreview(a.word, a.level, blend);
			if (mine !== run) return;
			if (rew.error) return void (preview = { status: 'fail', msg: plainText(store.net.meta.labels[`kkutu.js.error.${rew.error}`] ?? '') || t('play.lab.tip.no') });
			preview = { status: 'ok', blend, word: a.word, mean: blend ? '' : meanText(dict.mean), cost: rew.cost, rewards: rew.data ?? [] };
		})();
	});

	/** A meaning without its numbering marks, as the few lines the bubble has room for. */
	const meanText = (m) => formMean(String(m ?? '').replace(/[＂［（]\d+[＂］）]/g, ' ').replace(/\s+/g, ' ').trim());

	const ok = $derived(preview.status === 'ok');
	const sure = $derived(ok ? preview.rewards.find((r) => r.rate >= 1) : null);
	const tip = $derived(
		preview.status === 'idle'
			? t('play.lab.tip.start')
			: preview.status === 'loading'
				? t('play.lab.searching')
				: preview.status === 'fail'
					? preview.msg
					: preview.blend
						? t('play.lab.tip.blend')
						: sure
							? t('play.lab.tip.sure', { name: nameOf(sure.key) })
							: t('play.lab.tip.ok', { word: preview.word })
	);

	async function compose() {
		if (busy) return;
		if (!ok) return void toast(plainText(store.net.meta.labels['kkutu.js.error.436'] ?? ''), { kind: 'error' });
		if (!(await confirm({ message: plainText(t('play.lab.confirm')) }))) return;
		busy = true;
		const before = store.net.box;
		const res = await composeTray(tray);
		busy = false;
		if (res.error) return void toast(plainText(store.net.meta.labels[`kkutu.js.error.${res.error}`] ?? '') || t('play.error.request', { code: res.error }), { kind: 'error' });
		const mine = store.net.users[store.net.id];
		if (mine) mine.money = res.money;
		store.net.box = cleanBox(res.box);
		store.net.boxSeq++;
		store.conn.send('refresh');
		prizes = boxGains(before, store.net.box);
		reset();
		playSound('success');
		if (prizes.length) resultOpen = true;
		else toast(plainText(t('play.lab.done')), { kind: 'success' });
	}

	const fmt = (n) => Number(n).toLocaleString();
</script>

<Dialog bind:open title={t('play.lab.name')} size="lg">
	<div class="grid gap-x-6 gap-y-5 lg:grid-cols-2">
		<!-- the pieces I own -->
		<section class="flex min-w-0 flex-col gap-2">
			<div class="flex items-center gap-2">
				<h3 class="text-base font-extrabold">{t('play.lab.pick')}</h3>
				<div class="ml-auto w-36"><Select options={sortOptions} bind:value={sort} label={t('play.lab.pick')} /></div>
			</div>
			{#if !pieces.length}
				<p class="rounded-xl bg-(--kk-surface-2) px-3 py-6 text-center text-sm text-(color:--kk-muted)">{t('play.lab.none')}</p>
			{:else}
				<div class="grid max-h-44 grid-cols-[repeat(auto-fill,minmax(3rem,1fr))] gap-1.5 overflow-y-auto p-0.5">
					{#each pieces as e (e.item._id)}
						{@const left = e.count - inTray(e.item._id)}
						<button type="button" onclick={() => pick(e)} disabled={left <= 0} aria-label="{pieceChar(e.item._id)} ({t(`play.lab.kind.${pieceKind(e.item._id)}`)}) ×{left}" class="relative grid aspect-square place-items-center rounded-lg border border-(color:--kk-line) bg-(--kk-surface-2) transition-transform hover:-translate-y-0.5 active:translate-y-0 disabled:opacity-35 disabled:hover:translate-y-0">
							<ShopThumb id={e.item._id} shop={store.shop} class="size-9 text-base" />
							{#if e.count > 1}<span class="absolute -right-1 -bottom-1 rounded-full bg-(--kk-text) px-1 text-[10px] leading-4 font-extrabold text-(--kk-surface)">{left}</span>{/if}
						</button>
					{/each}
				</div>
			{/if}
		</section>

		<!-- the word they make -->
		<section class="relative min-w-0">
			<div class="relative flex min-h-44 flex-col items-center gap-3 rounded-2xl border-2 border-(color:--kk-line-strong) bg-(--kk-surface-2) px-4 py-4">
				<div class="flex min-h-11 flex-wrap items-center justify-center gap-1.5" aria-live="polite">
					{#if !tray.length}
						<span class="text-sm text-(color:--kk-muted)">{t('play.lab.trayHint')}</span>
					{:else}
						{#each tray as key, i (i + key)}
							<button type="button" onclick={() => unpick(i)} aria-label="{pieceChar(key)} – {t('play.lab.remove')}" class="lab-piece rounded-full transition-transform hover:-translate-y-0.5">
								<ShopThumb id={key} shop={store.shop} class="size-11 text-xl" />
							</button>
						{/each}
						<span class="ml-1 text-xs font-bold text-(color:--kk-muted)">{tray.length}/{MAX_TRAY}</span>
					{/if}
				</div>
				{#if preview.status === 'ok' && !preview.blend}
					<p class="max-h-24 w-full overflow-y-auto text-center text-xs leading-relaxed text-(color:--kk-muted)">{@html preview.mean}</p>
				{:else if preview.status === 'loading'}
					<p class="text-xs text-(color:--kk-muted)">{t('play.lab.searching')}</p>
				{:else if preview.status === 'ok' && preview.blend}
					<p class="text-center text-xs leading-relaxed text-(color:--kk-muted)">{t('play.lab.blendHint')}</p>
				{:else if preview.status === 'fail'}
					<p class="text-center text-xs leading-relaxed text-(color:--kk-muted)">{preview.msg}</p>
				{/if}
			</div>
		</section>

		<!-- what can come out -->
		<section class="flex min-w-0 flex-col gap-2">
			<h3 class="text-base font-extrabold">{t('play.lab.rewards')}</h3>
			{#if ok && preview.rewards.length}
				<ul class="grid grid-cols-[repeat(auto-fill,minmax(7.5rem,1fr))] gap-2">
					{#each preview.rewards as r (r.key)}
						<li class="lab-reward flex flex-col items-center gap-0.5 rounded-xl border border-(color:--kk-line) bg-(--kk-surface-2) px-2 py-2.5 text-center">
							<ShopThumb id={r.key} shop={store.shop} class="size-10 text-lg" />
							<b class="w-full truncate text-xs">{nameOf(r.key)}</b>
							<span class="text-[11px] text-(color:--kk-muted)">{rewardCount(r.rate)}</span>
							<span class="text-xs tabular-nums {r.rate >= 1 ? 'font-black text-(--kk-accent-strong)' : 'font-bold'}">{(r.rate * 100).toFixed(1)}%</span>
						</li>
					{/each}
				</ul>
			{:else}
				<p class="rounded-xl bg-(--kk-surface-2) px-3 py-6 text-center text-sm text-(color:--kk-muted)">{t('play.lab.rewardsHint')}</p>
			{/if}
		</section>

		<!-- the cost, the buttons and the lab assistant -->
		<section class="flex min-w-0 flex-col gap-3">
			<h3 class="text-base font-extrabold">{t('play.lab.cost')}</h3>
			<div class="grid grid-cols-[1fr_auto] gap-2">
				<button type="button" class="kk-btn kk-btn-primary inline-flex min-h-12 items-center justify-center gap-2.5 px-6" disabled={!ok || busy || preview.cost > money} onclick={compose}>
					{t('play.lab.compose')}
					<span class="inline-flex items-center gap-1 rounded-full bg-black/20 py-0.5 pr-3 pl-1.5 text-base font-extrabold tabular-nums"><GameIcon name="ping" size={22} />{ok ? fmt(preview.cost) : '–'}</span>
				</button>
				<button type="button" class="kk-btn min-h-12 px-5" disabled={!tray.length || busy} onclick={reset}>{t('play.lab.reset')}</button>
			</div>
			{#if ok && preview.cost > money}<p class="-mt-1 text-xs font-bold text-red-500" role="status">{t('play.lab.short')}</p>{/if}
			<div class="mt-auto flex items-end gap-2">
				<img src={cdnImg('kkutu/membership/GMMorem.png')} alt="" draggable="false" class="lab-gm size-24 shrink-0 object-contain" />
				<p class="relative mb-3 rounded-2xl rounded-bl-sm bg-(--kk-surface-2) px-3.5 py-2 text-[13px] leading-snug font-bold shadow-[0_2px_0_var(--kk-line)]">{tip}</p>
			</div>
		</section>
	</div>
</Dialog>

<ObtainScreen bind:open={resultOpen} title={t('play.obtain.acquired')} notice={plainText(t('play.lab.done'))} items={prizes} ribbon ambient="shine" palette="teal" shop={store.shop} {details} />

<style>
	.lab-piece {
		animation: kk-pop-in 0.25s cubic-bezier(0.34, 1.7, 0.64, 1) both;
	}
	.lab-reward {
		animation: kk-pop-in 0.3s cubic-bezier(0.34, 1.7, 0.64, 1) both;
	}
	@media (prefers-reduced-motion: reduce) {
		.lab-piece,
		.lab-reward {
			animation: none;
		}
	}
</style>
