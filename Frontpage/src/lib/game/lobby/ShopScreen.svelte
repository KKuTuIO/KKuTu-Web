<script>
	import { onMount, untrack } from 'svelte';
	import { quintOut } from 'svelte/easing';
	import { ms } from '../motion.js';
	import { t } from '../i18n.svelte.js';
	import { i18n } from '../i18n.svelte.js';
	import { plainText, cleanBox } from '../protocol.js';
	import { cdnImg } from '../../cdn.js';
	import { loadGoodDetails, loadHome, loadPlacement, gachaMeta, resolveHome, shelfItems, sortItems, filterItems, toggleInCart, setQty, cartPreview, cartTotal, SHOP_TABS, inSub, itemName, gachaItems, gachaPool, drawPrice, tally } from '../shop.js';
	import { playSound } from '../audio.svelte.js';
	import { toast } from '../ui/toast.svelte.js';
	import { confirm, prompt } from '../ui/confirm.svelte.js';
	import Tabs from '../ui/Tabs.svelte';
	import Select from '../ui/Select.svelte';
	import RadioGroup from '../ui/RadioGroup.svelte';
	import EmptyState from './EmptyState.svelte';
	import Icon from './Icon.svelte';
	import ShopCard from './ShopCard.svelte';
	import ShopHome from './ShopHome.svelte';
	import ShopCart from './ShopCart.svelte';
	import ShopBonus from './ShopBonus.svelte';
	import ShopPickup from './ShopPickup.svelte';
	import ObtainScreen from './ObtainScreen.svelte';

	/**
	 * 상점: 메인 (season, banners), 픽업 (gachas), 기능성, 모레미 and 장식 shelves (계정 is not open yet), with an avatar that tries on
	 * what is in the cart. Prices, tooltips, the "already owned" warning and the purchase itself are the legacy shop's.
	 */
	let { store, start = { tab: '', sub: '', n: 0 } } = $props();

	// Comes up from under the chat, like the 사전 page and the waiting room.
	function rise(node, { duration = 520 } = {}) {
		return { duration: ms(duration), easing: quintOut, css: (t, u) => `transform: translateY(${u * 14}%); opacity: ${Math.min(1, t * 2.2)}` };
	}

	// Moving between tabs: the new page slides in from the side it lies on (and fades in); the old one is simply gone, so nothing stacks.
	function swap(node, { dx = 0, duration = 260 } = {}) {
		return { duration: ms(duration), easing: quintOut, css: (t, u) => `opacity:${t};transform:translateX(${u * dx}px)` };
	}

	const HOME_BASE = cdnImg('shop/home/');
	const PLACEMENT_URL = 'https://static.kkutu.io/shop/shop.json'; // as the legacy shop
	const SORTS = ['updatedAt', 'name', 'lowCost', 'highCost', 'hit'];
	const me = $derived(store.me);
	const meta = $derived(store.net.meta);
	let details = $state(null); // item names and descriptions
	const box = $derived(store.net.box); // what I own: the welcome packet and every `obtain` keep it current
	let home = $state(null); // what the home page shows now
	let placement = $state(null); // shop.json: the gachas' names, subtitles and probability links
	let tab = $state('main'); // one of SHOP_TABS
	let subs = $state({}); // the sub-category picked in each tab
	let bonusKind = $state('both'); // 통합 / 경험치 / 핑 in 보너스권
	let query = $state('');
	let sort = $state('updatedAt');
	let cart = $state([]); // [{id, slot, qty}]
	let bare = $state(false); // 모두 벗기: the preview avatar wears nothing (only here: my real profile is not touched), so items can be tried on a plain one
	let buying = $state(false);
	let drawing = $state(false); // a gacha draw is on its way
	let resultOpen = $state(false);
	let prizes = $state([]);
	let rare = $state(new Set()); // prizes of a draw that are rare (under 1%): they come last
	let lastGacha = null;
	let resultNote = $state(''); // the note under the items: the legacy draw note, or the legacy purchase message
	// The shelf has hundreds of cards: the first screenful appears with the page, the rest follows in small batches so the
	// entrance animation never waits for them.
	let limit = $state(32);

	const nameOf = (id) => itemName(details, id);
	const failText = (code) => plainText(i18n.messages[`kkutu.js.error.${code}`] ?? '');
	const fail = (code) => toast(failText(code) || t('play.error.request', { code }), { kind: 'error' });

	onMount(async () => {
		const [d, h, pl] = await Promise.all([loadGoodDetails(), loadHome(HOME_BASE + 'home.json'), loadPlacement(PLACEMENT_URL)]);
		details = d;
		placement = pl;
		home = resolveHome(h);
		const more = () => {
			if (limit >= 2000) return;
			limit += 48;
			setTimeout(more, 40);
		};
		setTimeout(more, 450);
	});

	const items = $derived(shelfItems(store.shop));
	// 교환권 사용 (a coupon's serial number; the answer comes as a message of its own: used, expired, already used ...)
	async function useCoupon() {
		const code = (await prompt({ message: plainText(t('kkutu.js.putCouponCode')), max: 64 }))?.trim();
		if (code) store.useCoupon(code);
	}
	const tabs = $derived(SHOP_TABS.map((x) => ({ id: x.id, label: t(x.labelKey ?? `play.shop.tab.${x.id}`) })));
	const meta2 = $derived(gachaMeta(placement));
	const gachas = $derived(gachaItems(items, placement));
	const gachaName = (id) => meta2[id]?.name ?? nameOf(id);
	const searching = $derived(query.trim() !== '');
	// The sub-categories of the tab: one per gacha on sale in 픽업, the fixed ones elsewhere.
	const subOptions = $derived(
		tab === 'pickup'
			? gachas.map((g) => ({ value: g._id, label: gachaName(g._id) }))
			: (SHOP_TABS.find((x) => x.id === tab)?.subs ?? []).map((x) => ({ value: x.id, label: t(x.labelKey ?? `play.shop.sub.${x.id}`) }))
	);
	const sub = $derived(subOptions.find((o) => o.value === subs[tab])?.value ?? subOptions[0]?.value);
	// What the page shows: searching leaves 메인 and 픽업 for results across the whole shop, and in the other tabs searches the sub-category.
	const view = $derived(searching ? 'grid' : tab === 'main' ? 'main' : tab === 'pickup' ? 'pickup' : tab === 'func' && sub === 'bonus' ? 'bonus' : 'grid');
	const subDef = $derived(SHOP_TABS.find((x) => x.id === tab)?.subs?.find((x) => x.id === sub));
	const scope = $derived(tab === 'main' || tab === 'pickup' ? items : items.filter((g) => inSub(g, subDef)));
	const shown = $derived(filterItems(sortItems(scope, sort, nameOf), null, query, nameOf));
	const bonusItems = $derived(items.filter((g) => g.group === 'BON'));
	const sortOptions = $derived(SORTS.map((value) => ({ value, label: plainText(t(`item.sort.${value}`)) })));

	// Which page is shown, and where it lies relative to the one before (tabs weigh more than sub-categories).
	const pageKey = $derived(`${tab}:${view}:${view === 'main' ? '' : sub}`);
	const pagePos = $derived(SHOP_TABS.findIndex((x) => x.id === tab) * 100 + Math.max(0, subOptions.findIndex((o) => o.value === sub)));
	let dx = $state(0);
	let prevTab = 'main';
	let prevPos = 0;
	$effect.pre(() => {
		const pos = pagePos;
		const tabChanged = untrack(() => tab) !== prevTab;
		dx = Math.sign(pos - prevPos) * (tabChanged ? 32 : 18);
		prevPos = pos;
		prevTab = untrack(() => tab);
	});
	let panel = $state();
	$effect(() => {
		pageKey;
		if (panel) panel.scrollTop = 0;
	});

	const money = $derived(me?.money ?? 0);
	const total = $derived(cartTotal(cart, store.shop));
	const picked = $derived(new Set(cart.map((c) => c.id)));
	const preview = $derived(cartPreview(bare ? {} : (me?.equip ?? {}), cart));
	const owned = (id) => !!box?.[id];
	const pick = (item) => (cart = toggleInCart(cart, item));
	const qty = (item, q) => (cart = setQty(cart, item, q));

	// 계정 is not open yet: choosing it only says so.
	let before = 'main';
	$effect(() => {
		if (tab !== 'account') return void (before = tab);
		tab = before;
		toast(t('play.nav.notReady', { name: t('play.shop.tab.account') }), { kind: 'info' });
	});

	$effect(() => {
		if (start.n && start.tab) untrack(() => go({ tab: start.tab, sub: start.sub }));
	});

	/** What a banner or a "see all" link leads to: a tab (and its sub-category), an item, or a web address. */
	function go(target) {
		if (!target) return;
		if (target.tab) {
			if (target.sub) subs[target.tab] = target.sub;
			tab = target.tab;
			query = '';
		} else if (target.item) {
			const item = items.find((g) => g._id === target.item);
			if (item && !picked.has(item._id)) pick(item);
		} else if (target.url) window.open(target.url, '_blank', 'noopener,noreferrer');
	}

	/** 뽑기: 1 or 10 draws (legacy asks once, then the money goes down at once and the prizes arrive as `obtain` packets). */
	async function draw(gacha, count) {
		const price = drawPrice(gacha.cost, count);
		if (drawing || buying) return;
		if (price > money) return fail(407);
		if (!(await confirm({ message: plainText(t('kkutu.js.surePurchase')), ok: t('kkutu.dialog.purchase.ok'), cancel: t('kkutu.dialog.purchase.cancel') }))) return;
		store.net.gained.length = 0;
		lastGacha = gacha;
		drawing = true;
		store.conn.send('drawGacha', { item: gacha._id, count });
		store.net.users[store.net.id].money -= price;
		// If nothing comes back (the server refused: its error is shown by the connection) the shop stops waiting.
		setTimeout(() => drawing && ((drawing = false), (store.net.gained.length = 0)), 5000);
	}

	// The last packet of a draw carries the whole box: then the prizes are all here.
	let lastSeq = untrack(() => store.net.boxSeq);
	$effect(() => {
		const seq = store.net.boxSeq;
		if (seq === lastSeq) return;
		lastSeq = seq;
		if (!drawing) return;
		drawing = false;
		// the common prizes first, the rarest last (and ringed): the reveal builds up
		const pool = lastGacha ? gachaPool(lastGacha) : [];
		const chance = Object.fromEntries(pool.map((e) => [e.key, e.p]));
		prizes = tally(store.net.gained.splice(0)).sort((a, b) => (chance[b.key] ?? 100) - (chance[a.key] ?? 100));
		rare = new Set(pool.filter((e) => e.p < 1).map((e) => e.key));
		if (!prizes.length) return;
		resultNote = t('kkutu.js.drawObtain');
		resultOpen = true;
		playSound('success');
	});

	/** 일괄 구매: one `/buy` after another; it stops at the first refusal and what is left stays in the cart. */
	async function buyAll() {
		if (!cart.length || total > money || buying) return;
		const message = plainText(t(cart.some((c) => owned(c.id)) ? 'kkutu.js.alreadyGot' : 'kkutu.js.surePurchase'));
		if (!(await confirm({ message, ok: t('kkutu.dialog.purchase.ok'), cancel: t('kkutu.dialog.purchase.cancel') }))) return;
		buying = true;
		const got = []; // what was bought, for the obtain screen
		for (const { id, qty } of [...cart]) {
			const res = await fetch(`/buy/${encodeURIComponent(id)}?count=${qty}`, { method: 'POST' })
				.then((r) => r.json())
				.catch(() => ({ error: 400 }));
			if (res.error) {
				fail(res.error);
				break;
			}
			got.push({ key: id, q: qty });
			if (store.me) store.net.users[store.net.id].money = res.money;
			store.net.box = cleanBox(res.box);
			cart = cart.filter((c) => c.id !== id);
		}
		buying = false;
		if (got.length) {
			prizes = got;
			rare = new Set();
			resultNote = t('kkutu.js.purchased');
			resultOpen = true;
			playSound('success');
			store.conn.send('refresh');
		}
	}
</script>

<section
	class="kk-card flex min-h-0 flex-1 flex-col overflow-hidden lg:flex-row {home?.season ? 'shop-season' : ''}"
	style:--sa-l={home?.season?.accent?.light}
	style:--sa-d={home?.season?.accent?.dark}
	style:--st-l={home?.season?.tint?.light}
	style:--st-d={home?.season?.tint?.dark}
	aria-label={t('play.nav.shop')}
	in:rise|global
>
	<ShopCart
		equip={preview}
		user={me}
		parts={meta.moremiParts}
		shop={store.shop}
		{details}
		{cart}
		{owned}
		{money}
		{total}
		{buying}
		onremove={(id) => (cart = cart.filter((c) => c.id !== id))}
		{bare}
		onbare={() => (bare = true)}
		onreset={() => ((cart = []), (bare = false))}
		onbuy={buyAll}
	/>
	<div class="flex min-h-0 min-w-0 flex-1 flex-col">
		<div class="flex flex-col gap-2 border-b border-(color:--kk-line) p-2">
			<div class="flex flex-wrap items-center gap-2">
				<div class="min-w-0 flex-1 overflow-x-auto [&_.kk-tab]:px-4 [&_.kk-tab]:whitespace-nowrap">
					<Tabs bind:value={tab} {tabs} idPrefix="shop" label={t('play.nav.shop')} />
				</div>
				<button type="button" onclick={useCoupon} class="kk-btn inline-flex h-10 min-h-10 shrink-0 items-center gap-1.5 px-3 text-sm"><Icon name="confirmation_number" size={20} fill />{plainText(t('kkutu.dialog.accountOptions.useCoupon')).replace(/^\s+/, '')}</button>
				<label class="kk-input flex h-10 w-44 items-center gap-2 px-3">
					<Icon name="search" size={20} />
					<input type="text" bind:value={query} placeholder={t('item.search')} aria-label={t('item.search')} class="min-w-0 flex-1 bg-transparent outline-none" />
				</label>
			</div>
			{#if (subOptions.length && tab !== 'main') || view === 'grid'}
				<div class="flex flex-wrap items-center gap-2">
					{#if subOptions.length}<div class="min-w-0 flex-1"><RadioGroup variant="chips" options={subOptions} value={sub} label={tabs.find((x) => x.id === tab)?.label} onchange={(v) => (subs[tab] = v)} /></div>{:else}<div class="flex-1"></div>{/if}
					{#if view === 'grid'}<div class="w-36"><Select options={sortOptions} bind:value={sort} label={plainText(t('item.sort.title'))} /></div>{/if}
				</div>
			{/if}
		</div>
		<div bind:this={panel} role="tabpanel" id="shop-panel-{tab}" aria-labelledby="shop-tab-{tab}" class="shop-stage min-h-0 flex-1 p-3 md:p-4 {view === 'pickup' ? 'overflow-y-auto lg:overflow-hidden' : 'overflow-y-auto'}">
			{#key pageKey}
				<div class="h-full" in:swap={{ dx }}>
				{#if !details || !box || !home}
					<p class="p-8 text-center text-(color:--kk-muted)" role="status">{t('kkutu.js.loading')}</p>
				{:else if view === 'main'}
					<ShopHome {home} base={HOME_BASE} {items} shop={store.shop} {details} gachas={meta2} {picked} onpick={pick} onaction={go} />
				{:else if view === 'pickup'}
					{@const gacha = gachas.find((g) => g._id === sub)}
					{#if gacha}
						{#key gacha._id}<ShopPickup {gacha} meta={meta2[gacha._id]} shop={store.shop} {details} {money} ondraw={draw} />{/key}
					{:else}
						<EmptyState title={t('play.shop.pickup.none')} />
					{/if}
				{:else if view === 'bonus'}
					<ShopBonus items={bonusItems} shop={store.shop} {details} {cart} bind:kind={bonusKind} onqty={qty} />
				{:else if !shown.length}
					<EmptyState title={t('item.search.noResult')} hint={t('item.search.noResult.expl')} />
				{:else}
					<div class="grid grid-cols-[repeat(auto-fill,minmax(9.5rem,1fr))] gap-3 pb-[5px]">
						{#each shown.slice(0, limit) as item (item._id)}
							<ShopCard {item} shop={store.shop} {details} picked={picked.has(item._id)} onpick={pick} />
						{/each}
					</div>
				{/if}
				</div>
			{/key}
		</div>
	</div>
</section>

<ObtainScreen bind:open={resultOpen} title={t('play.obtain.acquired')} notice={resultNote} items={prizes} {rare} ribbon ambient="shine" palette="teal" shop={store.shop} {details} />

<style>
	/* The running season colours the shop: its accent for marks and badges, a tint behind the page. Light / dark follow the theme. */
	.shop-season {
		--shop-accent: var(--sa-l);
		--shop-tint: var(--st-l);
	}
	.shop-season .shop-stage {
		background: linear-gradient(to bottom, var(--shop-tint), transparent 320px);
	}
	@media (prefers-color-scheme: dark) {
		:global(:root:not([data-theme='light'])) .shop-season {
			--shop-accent: var(--sa-d);
			--shop-tint: var(--st-d);
		}
	}
	:global(:root[data-theme='dark']) .shop-season {
		--shop-accent: var(--sa-d);
		--shop-tint: var(--st-d);
	}
</style>
