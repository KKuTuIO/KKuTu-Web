/** Shop rules shared with the legacy client (`loadShop`, `filterShop`, `explainGoods`, `onGoods`). */

const MONTH = 1000 * 60 * 60 * 24 * 30;

let detailsRequest;
/** Item names and descriptions (`/api/play/goods`, the legacy `GoodDetails`) keyed by item id. Cached for the page lifetime. */
export function loadGoodDetails() {
	detailsRequest ??= fetch('/api/play/goods')
		.then((r) => (r.ok ? r.json() : {}))
		.catch(() => {
			detailsRequest = undefined;
			return {};
		});
	return detailsRequest;
}

/** The catalogue as a list for the shelf: the legacy parses `updatedAt` once and hides items with a negative cost. */
export function shelfItems(shop) {
	return Object.values(shop)
		.map((g) => ({ ...g, cost: Number(g.cost), hit: Number(g.hit) || 0, updatedAt: Date.parse(g.updatedAt) || 0 }))
		.filter((g) => g.cost >= 0);
}

/** Legacy sort orders; the default (and any unknown value) is 최근등록순. */
export function sortItems(list, by, nameOf) {
	const out = [...list];
	switch (by) {
		case 'name':
			return out.sort((a, b) => {
				const x = nameOf(a._id);
				const y = nameOf(b._id);
				return x < y ? -1 : x > y ? 1 : 0;
			});
		case 'hit':
			return out.sort((a, b) => b.hit - a.hit);
		case 'highCost':
			return out.sort((a, b) => b.cost - a.cost);
		case 'lowCost':
			return out.sort((a, b) => a.cost - b.cost);
		default:
			return out.sort((a, b) => b.updatedAt - a.updatedAt);
	}
}

/**
 * @param {string[]|null} groups item groups of the chosen category, `null` for 전체
 * @param {string} query substring of the name
 */
export function filterItems(list, groups, query, nameOf) {
	const q = (query ?? '').trim();
	return list.filter((g) => (!q || nameOf(g._id).includes(q)) && (!groups || groups.includes(g.group)));
}

/** Added within the last 30 days. */
export const isNew = (item, now = Date.now()) => item.updatedAt > now - MONTH;

/**
 * The tooltip's option rows. `g…` options are fractions shown as `+x.x%p`, `h…` are `+n`, `gif` is not shown,
 * `gives`/`event` are a single label and `gacha` shows its own text in its own colour.
 * @returns {{label?:string, value?:string, text?:string, color?:string}[]}
 */
export function optionRows(options, label) {
	const rows = [];
	for (const [key, raw] of Object.entries(options ?? {})) {
		if (key === 'gif') continue;
		if (key === 'gives' || key === 'event') rows.push({ text: label(key) });
		else if (key === 'gacha') rows.push({ text: raw.expl, color: raw.explColor || '#f3b819' });
		else {
			const k = key.charAt(0);
			const value = k === 'g' ? `+${(raw * 100).toFixed(1)}%p` : k === 'h' ? `+${raw}` : String(raw);
			rows.push({ label: label(key), value });
		}
	}
	return rows;
}

// ---- try-on and cart ----

/**
 * The Moremi slot an item occupies in the preview: its group, a free hand for hand items (the left one when both are taken),
 * the one badge slot for badges, and the name skin / the banner (one each: `NIK`, `BAN`). Items that are not worn or shown on the
 * avatar's card (consumables, bonus cards) have none.
 * (Legacy writes a badge under its own group, which its renderer ignores, so its preview never showed one.)
 */
export function slotOf(item, cart = []) {
	const group = String(item.group);
	if (group === 'Mhand') {
		const used = new Set(cart.map((c) => c.slot));
		return !used.has('Mlhand') ? 'Mlhand' : !used.has('Mrhand') ? 'Mrhand' : 'Mlhand';
	}
	if (group.startsWith('BDG')) return 'BDG';
	return group === 'NIK' || group === 'BAN' || group.startsWith('M') ? group : null;
}

/** How many of an item can be in the cart: worn items and time-limited ones one, the rest up to 99 (the server's limit). */
export const maxQty = (item) => (slotOf(item) !== null || item.term > 0 ? 1 : 99);

/** Puts an item in the cart, or takes it out when it is there already. A worn item replaces what was in its slot. */
export function toggleInCart(cart, item) {
	return cart.some((c) => c.id === item._id) ? cart.filter((c) => c.id !== item._id) : setQty(cart, item, 1);
}

/** Sets how many of an item are in the cart (0 takes it out). */
export function setQty(cart, item, qty) {
	const q = Math.min(maxQty(item), Math.max(0, Math.floor(Number(qty)) || 0));
	const rest = cart.filter((c) => c.id !== item._id);
	if (!q) return rest;
	if (rest.length < cart.length) return cart.map((c) => (c.id === item._id ? { ...c, qty: q } : c));
	const slot = slotOf(item, cart);
	return [...rest.filter((c) => slot === null || c.slot !== slot), { id: item._id, slot, qty: q }];
}

/** My equipment with everything in the cart put on. */
export function cartPreview(equip, cart) {
	const out = { ...equip };
	for (const c of cart) if (c.slot) out[c.slot] = c.id;
	return out;
}

export const cartTotal = (cart, shop) => cart.reduce((sum, c) => sum + (Number(shop[c.id]?.cost) || 0) * c.qty, 0);

// ---- the tabs ----

/**
 * The shop's tabs and what each shows. A sub-category takes whole item groups (`groups`) and/or single items (`ids`);
 * 픽업 has one sub-category per gacha on sale, 계정 is not open yet. Labels are `play.shop.tab.<id>` / `play.shop.sub.<id>`.
 */
export const SHOP_TABS = [
	{ id: 'main' },
	{ id: 'pickup' },
	{ id: 'func', subs: [{ id: 'bonus', groups: ['BON'] }, { id: 'coin', ids: ['wordToken'] }] },
	{
		id: 'moremi',
		subs: [
			{ id: 'body', groups: ['Mbody'] },
			{ id: 'head', groups: ['Mhead', 'MheadDeco'] },
			{ id: 'eye', groups: ['Meye', 'MeyeDeco', 'MfaceDeco'] },
			{ id: 'mouth', groups: ['Mmouth'] },
			{ id: 'clothes', groups: ['Mclothes', 'MdressDeco'] },
			{ id: 'handfoot', groups: ['Mhand', 'Mshoes'] },
			{ id: 'badge', groups: ['BDG1', 'BDG2', 'BDG3', 'BDG4'] },
			{ id: 'back', groups: ['Mback', 'Mfront'] }
		]
	},
	{ id: 'deco', subs: [{ id: 'name', groups: ['NIK'] }, { id: 'banner', groups: ['BAN'] }] },
	{ id: 'account', stub: true }
];

export const inSub = (item, sub) => !!sub && (sub.groups?.includes(item.group) || sub.ids?.includes(item._id) || false);

// ---- my box (프로필 관리) ----

/** Which sub-category of the box an item is in: worn on the avatar, a skin/banner, or everything else (tickets, boxes, tokens). */
export function inventoryKind(item) {
	const g = String(item.group);
	return g.startsWith('M') || g.startsWith('BDG') ? 'moremi' : g === 'NIK' || g === 'BAN' || g === 'BAD' || g === 'VOP' ? 'deco' : 'func';
}

/**
 * The filter of my box: 전체, 착용 중, and the three kinds with their own sub-categories (the shop's: 몸, 머리..., 이름 스킨, 배너;
 * for 기능성: 보너스권, 주화, 소모성 아이템 and the rest). Labels are locale keys (`labelKey`, else `play.shop.sub.<id>`).
 */
export const INVENTORY_FILTERS = [
	{ id: 'all', labelKey: 'item.category.all' },
	{ id: 'worn', labelKey: 'kkutu.js.equipped' },
	{ id: 'func', labelKey: 'play.shop.tab.func', subs: [{ id: 'bonus', labelKey: 'item.group.BON' }, { id: 'coin' }, { id: 'box', labelKey: 'item.group.CNS' }, { id: 'etc', labelKey: 'play.settings.tab.etc' }] },
	{ id: 'moremi', labelKey: 'play.shop.tab.moremi', subs: SHOP_TABS.find((x) => x.id === 'moremi').subs },
	{ id: 'deco', labelKey: 'play.shop.tab.deco', subs: SHOP_TABS.find((x) => x.id === 'deco').subs }
];

/** Does an item of my box pass the filter (and sub-category; `all` = the whole kind)? */
export function matchesInventory(item, equipped, filter, sub = 'all') {
	if (filter === 'all') return true;
	if (filter === 'worn') return equipped;
	if (inventoryKind(item) !== filter) return false;
	if (sub === 'all') return true;
	if (filter === 'func') {
		if (sub === 'bonus') return item.group === 'BON';
		if (sub === 'coin') return item._id === 'wordToken';
		if (sub === 'box') return item.group === 'CNS' && item._id !== 'wordToken';
		return item.group !== 'BON' && item.group !== 'CNS'; // etc
	}
	return inSub(item, INVENTORY_FILTERS.find((f) => f.id === filter)?.subs?.find((x) => x.id === sub));
}

/** Legacy `AVAIL_EQUIP`: the item groups that can be worn. */
export const isEquippable = (item, equips) => equips.includes(item.group);

/** The slot a worn item goes to: its group; a hand item the chosen hand; any badge the one badge slot. */
export function equipSlot(item, hand) {
	const g = String(item.group);
	if (g === 'Mhand') return hand === 'left' ? 'Mlhand' : 'Mrhand';
	return g.startsWith('BDG') ? 'BDG' : g;
}

/** Is the item worn now? (A hand item can be in either hand.) */
export function isEquipped(item, equip) {
	if (item.group === 'Mhand') return equip.Mlhand === item._id || equip.Mrhand === item._id;
	return equip[equipSlot(item)] === item._id;
}

/**
 * What I own as a list: every item of the box the shop knows (a dynamic one such as a letter piece by its kind), plus what I wear that
 * has no count left (legacy shows those too), by name.
 * @returns {{item:any, count:number, expire?:number}[]}
 */
export function ownedItems(box, equip, shop, nameOf) {
	const out = [];
	const known = (key) => {
		const base = shop[key] ?? (key.charAt(0) === '$' ? shop[key.slice(0, 4)] : undefined);
		return base && { ...base, _id: key, cost: Number(base.cost) };
	};
	const seen = new Set();
	for (const [key, v] of Object.entries(box ?? {})) {
		const item = known(key);
		if (!item) continue;
		seen.add(key);
		out.push({ item, count: v?.value ?? 0, expire: v?.expire });
	}
	for (const key of Object.values(equip ?? {})) {
		if (!key || key === 'default' || seen.has(key)) continue;
		const item = known(key);
		if (item) (seen.add(key), out.push({ item, count: 0 }));
	}
	return out.sort((a, b) => (nameOf(a.item._id) < nameOf(b.item._id) ? -1 : 1));
}

// ---- items and names ----

/** Legacy `iName`: an item's own name, else a dynamic item (`$WPC꽝`: a letter piece) as its kind's name and its content. */
export function itemName(details, key) {
	if (details?.[key]) return details[key].name;
	if (key.charAt(0) === '$' && details?.[key.slice(0, 4)]) return `${details[key.slice(0, 4)].name} - ${key.slice(4)}`;
	return key;
}

/** Legacy `iDynImage`: a letter piece is a coloured circle with its content. Anything else has no picture of its own. */
export function dynItem(key) {
	const i = ['WPC', 'WPB', 'WPA', 'WPE'].indexOf(key.slice(1, 4));
	if (key.charAt(0) !== '$' || i < 0) return null;
	return { bg: ['#DDDDDD', '#A6C5FF', '#FFEF31', '#B39DDB'][i], fg: ['#000000', '#4465C3', '#E69D12', '#000000'][i], text: key.slice(4) };
}

// ---- 보너스권 ----

/** Which bonus a ticket gives: both experience and ping (통합), experience, or ping. Anything else counts as 통합. */
export const bonusKind = (id) => (/^bonusEXPMNY/.test(id) ? 'both' : /^bonusEXP/.test(id) ? 'exp' : /^bonusMNY/.test(id) ? 'mny' : 'both');
/** The number of a ticket (`bonusEXP_15` is 15), for putting a kind's tickets in order. */
export const bonusValue = (id) => Number(id.match(/(\d+)$/)?.[1] ?? 0);
export const BONUS_KINDS = ['both', 'exp', 'mny'];

// ---- gacha (픽업) ----

let placementRequest;
/** `shop.json` of KKuTu-Static (`https://static.kkutu.io/shop/shop.json`, the legacy shop's `shopPlacement`): what the shop lists and how it calls it. */
export function loadPlacement(url) {
	placementRequest ??= fetch(url)
		.then((r) => (r.ok ? r.json() : {}))
		.catch(() => {
			placementRequest = undefined;
			return {};
		});
	return placementRequest;
}

/** The gacha entries of `shop.json` by item id: `{name, subtitle, probability_link, start, ...}`. */
export const gachaMeta = (placement) => Object.fromEntries((placement?.shop?.gacha ?? []).map((g) => [g._id, g]));

/**
 * Gachas on sale: items marked `options.gacha.enabled` (legacy rule), in the order of `shop.json` (those it doesn't list follow),
 * and not before their `start`.
 */
export function gachaItems(list, placement, now = Date.now()) {
	const order = (placement?.shop?.gacha ?? []).map((g) => g._id);
	const rank = (g) => (order.includes(g._id) ? order.indexOf(g._id) : order.length);
	const meta = gachaMeta(placement);
	return list
		.filter((g) => g.options?.gacha?.enabled && (!meta[g._id]?.start || Date.parse(meta[g._id].start) <= now))
		.sort((a, b) => rank(a) - rank(b));
}

/** What a gacha can give and how likely: its weighted item pools, rarest first. `p` is a percentage. */
export function gachaPool(item) {
	const weights = new Map();
	for (const g of item.options?.gives ?? []) {
		if (g.type !== 'item' || g.from !== 'wei' || !g.pool) continue;
		// a pool is {item: weight}, or the older pair of lists: items in `pool`, weights in `weight`
		const entries = Array.isArray(g.pool) ? g.pool.map((key, i) => [key, g.weight?.[i]]) : Object.entries(g.pool);
		for (const [key, w] of entries) weights.set(key, (weights.get(key) ?? 0) + (Number(w) || 0));
	}
	const total = [...weights.values()].reduce((a, b) => a + b, 0);
	if (!total) return [];
	return [...weights].map(([key, w]) => ({ key, p: (w / total) * 100 })).sort((a, b) => a.p - b.p || a.key.localeCompare(b.key));
}

/** Legacy price of a draw: ten at once are 10% cheaper per draw. */
export const drawPrice = (cost, count) => Math.floor(cost * (count >= 10 ? 0.9 : 1)) * count;

/** The prizes of a draw, each item once with how many came. */
export function tally(gains) {
	const out = new Map();
	for (const g of gains) out.set(g.key, (out.get(g.key) ?? 0) + (g.q ?? 1));
	return [...out].map(([key, q]) => ({ key, q }));
}

// ---- the home page (data lives in KKuTu-Static: img/shop/home/home.json) ----

/** An entry is on show between its optional `from` and `to` (ISO dates). */
export function activeAt(entry, now = Date.now()) {
	const from = entry?.from ? Date.parse(entry.from) : -Infinity;
	const to = entry?.to ? Date.parse(entry.to) : Infinity;
	return from <= now && now < to;
}

let homeRequest;
/** The home page's content (season, banners, tiles, sections). Cached; `{}` when there is none. */
export function loadHome(url) {
	homeRequest ??= fetch(url, { cache: 'no-cache' })
		.then((r) => (r.ok ? r.json() : {}))
		.catch(() => {
			homeRequest = undefined;
			return {};
		});
	return homeRequest;
}

/** What is on show now: the running season and the banners, tiles and sections whose dates include `now`. */
export function resolveHome(raw, now = Date.now()) {
	const live = (list) => (Array.isArray(list) ? list.filter((e) => activeAt(e, now)) : []);
	const season = raw?.season && activeAt(raw.season, now) ? raw.season : null;
	return { season, hero: live(raw?.hero), tiles: live(raw?.tiles), sections: live(raw?.sections) };
}

/**
 * The items of a home section. `items` lists ids (in that order), `group` takes whole item groups, `best` the best sellers,
 * `new` what was added within 30 days. Groups and `best` go by 누적판매순, `new` by date.
 */
export function sectionItems(section, list, now = Date.now()) {
	const limit = section.limit ?? 12;
	const byHit = (a, b) => b.hit - a.hit;
	switch (section.kind) {
		case 'items': {
			const byId = new Map(list.map((g) => [g._id, g]));
			return (section.items ?? []).map((id) => byId.get(id)).filter(Boolean).slice(0, limit);
		}
		case 'group':
			return list.filter((g) => (section.groups ?? []).includes(g.group)).sort(byHit).slice(0, limit);
		case 'best':
			return list.filter((g) => g.hit > 0).sort(byHit).slice(0, limit);
		case 'new':
			return list.filter((g) => isNew(g, now)).sort((a, b) => b.updatedAt - a.updatedAt).slice(0, limit);
		default:
			return [];
	}
}
