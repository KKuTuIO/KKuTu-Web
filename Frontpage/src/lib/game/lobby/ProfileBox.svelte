<script>
	import { onMount, untrack } from 'svelte';
	import { t } from '../i18n.svelte.js';
	import { plainText, cleanBox } from '../protocol.js';
	import { loadGoodDetails, INVENTORY_FILTERS, matchesInventory, isConsumable, isEquippable, equipSlot, isEquipped, itemName, ownedItems, tally } from '../shop.js';
	import { playSound } from '../audio.svelte.js';
	import { confirm, prompt, choose } from '../ui/confirm.svelte.js';
	import { toast } from '../ui/toast.svelte.js';
	import ChipRow from './ChipRow.svelte';
	import Tooltip from '../ui/Tooltip.svelte';
	import HackerDialog from './HackerDialog.svelte';
	import CharFactoryDialog from './CharFactoryDialog.svelte';
	import EnchantDialog from './EnchantDialog.svelte';
	import { splitEnchanted, wearCopy } from '../enchant.js';
	import { enchantStore, refreshEnchants } from '../enchantStore.svelte.js';
	import BonusSlot from './BonusSlot.svelte';
	import EmptyState from './EmptyState.svelte';
	import ItemTile from './ItemTile.svelte';
	import Icon from './Icon.svelte';
	import ObtainScreen from './ObtainScreen.svelte';
	import { cdnImg } from '../../cdn.js';

	/**
	 * 프로필 관리: what I own, as a tab of my profile. Clicking an item does what the legacy box did: a wearable item is put on or taken
	 * off (asking first, and which hand for a hand item), a consumable (a box) is used (how many, asking first) and its prizes are shown
	 * on the obtain screen; items are small icon tiles (the name and details are in the tooltip), a worn item has its mark and cannot be
	 * deleted, anything else has a delete button. What I wear shows at once on the avatar beside this tab.
	 * @type {{store:any}}
	 */
	let { store } = $props();
	let details = $state(null);
	let filter = $state('all'); // 전체 / 착용 중 / 기능성 / 모레미 / 장식
	let sub = $state('all'); // the sub-category inside the kind
	let query = $state('');
	let hackerOpen = $state(false);
	let labOpen = $state(false);
	let enchantOpen = $state(false);
	let labTip = $state(1); // which of the lab assistant's lines the tooltip shows
	let tipLine = $state(1); // which of 해커레미's lines the tooltip shows (drawn anew whenever the pointer comes)
	let using = $state(false); // a box is being opened: its prizes are on their way
	let resultOpen = $state(false);
	let prizes = $state([]);
	const me = $derived(store.me);
	const equip = $derived(me?.equip ?? {});
	const equips = $derived(store.net.meta.moremiEquips);
	const nameOf = (id) => itemName(details, id);
	const entries = $derived(ownedItems(store.net.box, equip, store.shop, nameOf));
	// my enchanted copies: each is a tile of its own (with its +level) and never part of the `×n` of the plain ones
	const rows = $derived(splitEnchanted(entries, enchantStore.items, (item) => isEquipped(item, equip)));
	const worn = (e) => e.equipped;
	const loadEnchants = refreshEnchants;
	$effect(() => {
		store.net.boxSeq; // the box changed (a ticket spent, an item bought)
		if (!enchantOpen) untrack(loadEnchants); // while the window is open it keeps its own copy of this
	});
	const labelOf = (x, fallback) => {
		const text = t(x.labelKey ?? fallback);
		return x.labelKey === 'kkutu.js.equipped' ? plainText(text).replace(/[()]/g, '').trim() : text;
	};
	const q = $derived(query.trim());
	const searched = $derived(rows.filter((e) => !q || nameOf(e.item._id).includes(q)));
	const count = (f, s = 'all') => searched.filter((e) => matchesInventory(e.item, worn(e), f, s)).length;
	const kinds = $derived(INVENTORY_FILTERS.map((f) => ({ value: f.id, label: labelOf(f), count: count(f.id) })));
	const current = $derived(INVENTORY_FILTERS.find((f) => f.id === filter));
	const subs = $derived(current?.subs ? [{ value: 'all', label: t('item.category.all'), count: count(filter) }, ...current.subs.map((x) => ({ value: x.id, label: labelOf(x, `play.shop.sub.${x.id}`), count: count(filter, x.id) }))] : []);
	const list = $derived(searched.filter((e) => matchesInventory(e.item, worn(e), filter, sub)));
	// the 보너스권 I wear now (it is outside the outfits of the wardrobe)
	const wornTicket = $derived(equip.BON ? (store.shop[equip.BON] ?? null) : null);
	// and the 테마 (also outside the outfits: it recolours the client, not the avatar)
	const wornTheme = $derived(equip.THM ? (store.shop[equip.THM] ?? null) : null);
	const wornTitle = $derived(equip.TTL ? (store.shop[equip.TTL] ?? null) : null);
	const failText = (code) => plainText(store.net.meta.labels[`kkutu.js.error.${code}`] ?? '');

	onMount(async () => (details = await loadGoodDetails()));

	async function use(e) {
		const { item, count } = e;
		// 단어 토큰: nothing to open, it is spent on 어인정 조회 in the 사전
		if (item._id === 'wordToken') return store.hooks.openInjeong?.();
		if (isConsumable(item)) {
			let n = 1;
			if (count > 1) {
				const v = await prompt({ message: t('kkutu.js.consume', { count }), value: '1', max: 4 });
				if (v === null) return;
				n = parseInt(v, 10);
				if (isNaN(n)) return void toast(plainText(t('kkutu.js.invalidConsume')), { kind: 'error' });
				if (n < 1) return;
				if (n > count) return void toast(plainText(t('kkutu.js.notEnoughConsume')), { kind: 'error' });
			}
			if (!(await confirm({ message: plainText(t('kkutu.js.sureConsume')) }))) return;
			if (using) return;
			store.net.gained.length = 0;
			using = true;
			store.conn.send('consume', { item: item._id, count: n }); // its prizes arrive as `obtain` packets
			setTimeout(() => using && ((using = false), (store.net.gained.length = 0)), 5000);
			return;
		}
		if (!isEquippable(item, equips)) return;
		const copyNo = e.instance ?? 0;
		// another copy of an item I already wear: nothing is taken off, only which copy counts changes
		if (isEquipped(item, equip) && !e.equipped) {
			if (!(await confirm({ message: `${plainText(t('kkutu.js.sureEquip'))}: ${nameOf(item._id)}` }))) return;
			await wearCopy(item._id, copyNo);
			return void loadEnchants();
		}
		let hand;
		if (item.group === 'Mhand') {
			hand = await choose({ message: plainText(t('kkutu.js.dressWhichHand')), options: [{ value: 'left', label: t('kkutu.dialog.message.left') }, { value: 'right', label: t('kkutu.dialog.message.right') }] });
			if (!hand) return;
		}
		const slot = equipSlot(item, hand);
		const already = equip[slot] === item._id;
		if (!(await confirm({ message: `${plainText(t(already ? 'kkutu.js.sureUnequip' : 'kkutu.js.sureEquip'))}: ${nameOf(item._id)}` }))) return;
		store.conn.send('consume', { item: item._id, slot }); // the server answers with my new equipment (`obtain`)
		if (!already) wearCopy(item._id, copyNo).then(loadEnchants); // and which copy of it counts
	}

	async function remove(item) {
		if (!(await confirm({ message: plainText(t('kkutu.js.surePayback')) }))) return;
		const res = await fetch(`/payback/${encodeURIComponent(item._id)}`, { method: 'POST' })
			.then((r) => r.json())
			.catch(() => ({ error: 400 }));
		if (res.error) return void toast(failText(res.error) || t('play.error.request', { code: res.error }), { kind: 'error' });
		store.net.box = cleanBox(res.box);
		toast(plainText(t('kkutu.js.payback')), { kind: 'success' });
		store.conn.send('refresh');
	}

	// The last packet of an opened box carries the whole box: then its prizes are all here.
	let lastSeq = untrack(() => store.net.boxSeq);
	$effect(() => {
		const seq = store.net.boxSeq;
		if (seq === lastSeq) return;
		lastSeq = seq;
		if (!using) return;
		using = false;
		prizes = tally(store.net.gained.splice(0));
		if (!prizes.length) return;
		resultOpen = true;
		playSound('success');
	});
</script>

<div class="flex flex-col gap-2">
	<!-- the worn 보너스권 as a slot of its own (the wardrobe button is on the avatar preview) -->
	<div class="flex justify-start gap-3">
		<BonusSlot item={wornTicket} shop={store.shop} {details} onopen={() => ((filter = 'func'), (sub = 'bonus'))} />
		<BonusSlot kind="skintheme" item={wornTheme} shop={store.shop} {details} onopen={() => ((filter = 'deco'), (sub = 'theme'))} />
		<BonusSlot kind="skintitle" item={wornTitle} shop={store.shop} {details} onopen={() => ((filter = 'deco'), (sub = 'title'))} />
	</div>
	<ChipRow options={kinds} bind:value={filter} onchange={() => (sub = 'all')} label={t('kkutu.dialog.profile.dress')} />
	{#if subs.length}<ChipRow options={subs} bind:value={sub} size="sm" scroll label={current && labelOf(current)} />{/if}
	<label class="kk-input flex h-10 w-full items-center gap-2 px-3"><Icon name="search" size={16} /><input type="text" bind:value={query} placeholder={t('item.search')} aria-label={t('item.search')} class="min-w-0 flex-1 bg-transparent text-sm outline-none" /></label>
	{#if !details}
		<p class="py-6 text-center text-sm text-(color:--kk-muted)" role="status">{t('kkutu.js.loading')}</p>
	{:else if !list.length}
		<EmptyState compact size={110} title={t('play.profile.box.empty')} />
	{:else}
		<div class="grid grid-cols-[repeat(auto-fill,minmax(3.6rem,1fr))] gap-2 pb-[5px]">
			{#each list as e (e.key)}
				<ItemTile item={e.item} shop={store.shop} {details} own={{ count: e.count, expire: e.expire, equipped: e.equipped, level: e.level, lines: e.lines }} onpick={() => use(e)} onremove={e.instance ? undefined : remove} />
			{/each}
		</div>
	{/if}
</div>

<!-- The bar at the foot of the list: its backdrop is solid at the bottom and clear at the top, so the list fades into it instead of being cut off; 분해 is not built yet (a disabled secondary button), 강화 opens the enchant dialog. 글자 조합소 and 해커레미의 조합 keep their characters hidden behind the button until it is hovered or focused. -->
<div class="pointer-events-none sticky bottom-0 z-10 mt-3 flex items-end justify-end gap-3 bg-gradient-to-t from-(--kk-surface) from-30% to-transparent pt-14 pb-0.5">
	<span class="pointer-events-auto" title={t('play.lab.tbd')}>
		<button type="button" disabled aria-label="{t('play.lab.dismantle')} – {t('play.lab.tbd')}" class="kk-btn inline-flex h-12 min-h-12 items-center px-5 text-base">{t('play.lab.dismantle')}</button>
	</span>
	<span class="pointer-events-auto">
		<button type="button" onclick={() => (enchantOpen = true)} class="kk-btn inline-flex h-12 min-h-12 items-center px-5 text-base">{t('play.lab.enhance')}</button>
	</span>
	<div class="group pointer-events-auto relative">
		<img src={cdnImg('kkutu/membership/GMMorem.png')} alt="" draggable="false" class="cta-peek absolute -top-10 left-3 z-0 h-[64px] w-[64px] object-contain" />
		<Tooltip text={t(`play.lab.cta.${labTip}`)} placement="top">
			<button type="button" onpointerenter={() => (labTip = 1 + Math.floor(Math.random() * 2))} onfocus={() => (labTip = 1 + Math.floor(Math.random() * 2))} onclick={() => (labOpen = true)} class="relative z-10 inline-flex h-12 items-center rounded-xl border-0 bg-[#16a34a] px-5 text-base font-extrabold text-white shadow-[0_3px_0_#166534] active:translate-y-px active:shadow-[0_1px_0_#166534]">{t('play.lab.name')}</button>
		</Tooltip>
	</div>
	<div class="group pointer-events-auto relative">
		<img src={cdnImg('play/hackerremi.png')} alt="" draggable="false" class="cta-peek absolute -top-11 left-2 z-0 h-[72px] w-[72px] object-contain" />
		<Tooltip text={t(`play.hacker.tip.${tipLine}`)} placement="top">
			<button type="button" onpointerenter={() => (tipLine = 1 + Math.floor(Math.random() * 3))} onfocus={() => (tipLine = 1 + Math.floor(Math.random() * 3))} onclick={() => (hackerOpen = true)} class="relative z-10 inline-flex h-12 items-center rounded-xl border-0 bg-[#7c3aed] px-5 text-base font-extrabold text-white shadow-[0_3px_0_#5b21b6] active:translate-y-px active:shadow-[0_1px_0_#5b21b6]">{t('play.hacker.name')}</button>
		</Tooltip>
	</div>
</div>

<EnchantDialog bind:open={enchantOpen} {store} {entries} {details} {nameOf} />
<CharFactoryDialog bind:open={labOpen} {store} {entries} {details} {nameOf} />
<HackerDialog bind:open={hackerOpen} {store} {entries} {details} {nameOf} onshop={() => ((hackerOpen = false), store.hooks.openShop?.('func', 'bonus'))} />

<ObtainScreen bind:open={resultOpen} title={t('play.obtain.acquired')} notice={t('kkutu.js.drawObtain')} items={prizes} ribbon ambient="shine" palette="teal" shop={store.shop} {details} />

<style>
	/* the characters stay hidden behind their button and rise out of it while the button is hovered or focused */
	.cta-peek {
		opacity: 0;
		transform: translateY(2.2rem);
		transition:
			transform 0.28s cubic-bezier(0.34, 1.6, 0.64, 1),
			opacity 0.15s ease-out;
		pointer-events: none;
	}
	:global(.group:hover) > .cta-peek,
	:global(.group:focus-within) > .cta-peek {
		opacity: 1;
		transform: translateY(0);
	}
	@media (prefers-reduced-motion: reduce) {
		.cta-peek {
			transition: opacity 0.01ms;
			transform: none;
		}
	}
</style>
