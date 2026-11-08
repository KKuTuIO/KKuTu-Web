/** 강화 (item enchant): the pure helpers of the dialog and the three server calls. The server (`/enchant`) judges everything. */

/** A permille amount as the number the player reads: 10 → "1", 15 → "1.5", 2 → "0.2". */
export function permille(v) {
	return String(Math.round(Number(v)) / 10);
}

/** The number in a line for the unit of its effect (`PERMILLE`, `FLAT`, `SECONDS`). */
export function lineValue(line, units) {
	return units?.[line.effect] === 'PERMILLE' ? permille(line.value) : String(line.value);
}

/** The words of one line, from `play.enchant.effect.<id>` ({mode} is the game type's name, {value} the number). */
export function lineText(line, units, t) {
	const mode = line.mode ? t(`game.mode.${line.mode}.name`) : '';
	return t(`play.enchant.effect.${line.effect}`, { mode, value: lineValue(line, units) });
}

/** The items that can be enchanted: owned, in an enchantable group, by name. */
export function enchantableItems(entries, groups, nameOf) {
	const ok = new Set(groups ?? []);
	return entries.filter((e) => ok.has(e.item.group)).sort((a, b) => nameOf(a.item._id).localeCompare(nameOf(b.item._id), 'ko'));
}

/** Success chance of the next regular try, in percent, after `pity` failures in a row (mirrors the server; the server's number is shown when it has one). */
export function chanceAfter(pity, rules) {
	return Math.min(100, (rules?.baseChance ?? 30) + (rules?.pityStep ?? 10) * Math.max(0, pity));
}

const REEL_MODES = ['KKT', 'EKT', 'KSH', 'ESH', 'KAP', 'KDA'];
const REEL_VALUES = { PERMILLE: [10, 15, 20, 30, 40, 60], FLAT: [1, 2, 3], SECONDS: [15, 30, 45, 60] };

/** A made-up line for the reel that spins while a try is in flight (it only looks like a result; the real one replaces it). */
export function fakeLine(units, rnd = Math.random) {
	const ids = Object.keys(units ?? {});
	if (!ids.length) return null;
	const effect = ids[Math.floor(rnd() * ids.length)];
	const values = REEL_VALUES[units[effect]] ?? REEL_VALUES.FLAT;
	return { effect, value: values[Math.floor(rnd() * values.length)], mode: REEL_MODES[Math.floor(rnd() * REEL_MODES.length)] };
}

async function call(path, body, fetchFn) {
	try {
		const res = await fetchFn(path, body ? { method: 'POST', headers: { 'Content-Type': 'application/x-www-form-urlencoded' }, body: new URLSearchParams(body) } : undefined);
		return await res.json();
	} catch {
		return { error: 'network' };
	}
}

export const fetchEnchant = (fetchFn = fetch) => call('/enchant', null, fetchFn);
/** One try on a copy of an item (`instance` 0: a plain copy, else the number of one of my enchanted copies). */
export const tryEnchant = (item, instance, ticket, fetchFn = fetch) => call('/enchant', { item, instance: String(instance), ticket }, fetchFn);
/** Tells the server which copy of an item I now wear (0: a plain one), so that copy's lines are the ones that count. */
export const wearCopy = (item, instance, fetchFn = fetch) => call('/enchant/wear', { item, instance: String(instance) }, fetchFn);
/** Buys one ticket with the shop's own endpoint (the price is the shop price). */
export const buyTicket = (id, fetchFn = fetch) => call(`/buy/${encodeURIComponent(id)}`, { count: '1' }, fetchFn);

/** How many of an item my box holds (a box entry is `{value}` or a bare number). */
export const ownedCount = (box, id) => Number(box?.[id]?.value ?? box?.[id] ?? 0);
/** `slot` is the line to replace with the pending one; null throws the pending line away. */
export const resolveEnchant = (item, instance, slot, fetchFn = fetch) => call('/enchant/resolve', slot == null ? { item, instance: String(instance) } : { item, instance: String(instance), slot: String(slot) }, fetchFn);

// ---- copies: the box only counts them, an enchanted one is told apart by its number ----

/** The most plain copies of one item the enchant window lists one by one (they are all the same). */
export const MAX_PLAIN_LISTED = 10;

/**
 * Every copy of my enchantable items, one by one: the enchanted ones (by their number) first, then the plain ones.
 * `items` is the server's `items` (`{[item]: {pity, copies:[{instance, level, ...}]}}`).
 * @returns {{key:string, item:any, instance:number, level:number, lines:any[], pending:any, plainIndex?:number}[]}
 */
export function copiesOf(entries, groups, items, nameOf) {
	const ok = new Set(groups ?? []);
	const out = [];
	for (const e of [...entries].filter((x) => ok.has(x.item.group) && x.count > 0).sort((a, b) => nameOf(a.item._id).localeCompare(nameOf(b.item._id), 'ko'))) {
		const made = (items?.[e.item._id]?.copies ?? []).slice(0, e.count);
		for (const c of made) out.push({ key: `${e.item._id}#${c.instance}`, item: e.item, instance: c.instance, level: c.level, lines: c.lines, pending: c.pending });
		for (let i = 0; i < Math.min(e.count - made.length, MAX_PLAIN_LISTED); i++) out.push({ key: `${e.item._id}#p${i}`, item: e.item, instance: 0, level: 0, lines: [], pending: null, plainIndex: i });
	}
	return out;
}

/** The server's new state of one copy folded into `items`: the item's pity, and the copy replaced (or added when a plain copy became enchanted). */
export function applyCopyState(items, itemId, state) {
	const cur = items?.[itemId] ?? { copies: [] };
	const copies = state.instance > 0 ? (cur.copies.some((c) => c.instance === state.instance) ? cur.copies.map((c) => (c.instance === state.instance ? state : c)) : [...cur.copies, state]) : cur.copies;
	return { ...items, [itemId]: { pity: state.pity, chance: state.chance, copies } };
}

/**
 * My box as the inventory shows it: an item's enchanted copies each get a tile of their own (level on it, never merged into the `×n`),
 * the plain copies stay one tile with what is left of the count. `equipped(item)` says whether the item is worn at all; of its
 * copies only the worn one (or the plain stack when no enchanted copy is worn) shows as worn.
 * @returns {{key:string, item:any, count:number, expire?:number, instance?:number, level?:number, lines?:any[], equipped:boolean}[]}
 */
export function splitEnchanted(entries, items, equipped) {
	const out = [];
	for (const e of entries) {
		const id = e.item._id;
		const worn = equipped(e.item);
		const made = e.count > 0 ? (items?.[id]?.copies ?? []).slice(0, e.count) : [];
		const anyWorn = made.some((c) => c.worn);
		for (const c of made) out.push({ item: e.item, count: 1, expire: e.expire, key: `${id}#${c.instance}`, instance: c.instance, level: c.level, lines: c.lines, equipped: worn && c.worn });
		if (!made.length || e.count > made.length) out.push({ ...e, count: e.count - made.length, key: id, equipped: worn && !anyWorn });
	}
	return out;
}

// ---- which copy is worn: recorded when an outfit goes on ----

/** Where the 확률 정보 button of the 강화 window leads (a placeholder until the real page exists). */
export const ODDS_URL = 'https://google.com';

/** The enchanted copy with the highest level among the `count` I hold (the lowest number on a tie); 0 when there is none (a plain copy). */
export function bestCopy(items, itemId, count) {
	const made = (items?.[itemId]?.copies ?? []).slice(0, count);
	return made.reduce((best, c) => (c.level > (best?.level ?? 0) ? c : best), null)?.instance ?? 0;
}

/**
 * The copy of each worn item that is enchantable-and-enchanted, as `{item: instance}` (0: a plain copy is worn). Only items that have
 * enchanted copies are listed: for the others there is nothing to tell apart.
 */
export function wornCopies(items, wornIds) {
	const out = {};
	for (const id of new Set(wornIds)) {
		const copies = items?.[id]?.copies ?? [];
		if (copies.length) out[id] = copies.find((c) => c.worn)?.instance ?? 0;
	}
	return out;
}

/**
 * Which copy to put on with an item: the one an outfit remembers (`wanted`, a number) when I still have it, a plain one when the
 * outfit remembers that (0), else (an outfit from before copies, `wanted` undefined) the best enchanted one.
 */
export function copyToWear(items, itemId, count, wanted) {
	const made = (items?.[itemId]?.copies ?? []).slice(0, count);
	if (!made.length) return 0;
	if (wanted === 0) return 0;
	if (wanted > 0 && made.some((c) => c.instance === wanted)) return wanted;
	return bestCopy(items, itemId, count);
}

/** The `wear` calls for a set of items just put on: `ids` with `counts` (what the box holds) and the outfit's remembered `copies` (or undefined). */
export function wearPlan(items, ids, counts, copies) {
	return [...new Set(ids)]
		.filter((id) => (items?.[id]?.copies ?? []).length > 0)
		.map((id) => ({ item: id, instance: copyToWear(items, id, counts[id] ?? 0, copies?.[id]) }));
}

/** Sends a wear plan one by one (each is a row update on the server). */
export async function wearAll(plan, fetchFn = fetch) {
	for (const { item, instance } of plan) await wearCopy(item, instance, fetchFn);
}
