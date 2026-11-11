/**
 * 해커레미의 조합: the best combination of what I own for experience or ping, and what it would be worth.
 * The rule is the server's (`applyEquipOptions`): every worn item adds `gEXP` / `gMNY` as a fraction of the base reward of a game
 * and `hEXP` / `hMNY` as a flat amount per minute, all added up. So every slot can be decided on its own: the best owned item of
 * that slot, or what I wear now when nothing owned is better.
 */
import { equipSlot, isEquippable } from './shop.js';

const num = (v) => (typeof v === 'number' && isFinite(v) ? v : 0);

/** What an item adds: `gx`/`gm` (fractions of the base reward) and `hx`/`hm` (per minute). */
export function itemEffect(item) {
	const o = item?.options ?? {};
	return { gx: num(o.gEXP), hx: num(o.hEXP), gm: num(o.gMNY), hm: num(o.hMNY) };
}
const hasEffect = (e) => e.gx > 0 || e.hx > 0 || e.gm > 0 || e.hm > 0;

/** The total of a set of worn items (an equipment map `{slot: item id}`). */
export function totals(equip, shop) {
	const t = { gx: 0, hx: 0, gm: 0, hm: 0 };
	for (const id of Object.values(equip ?? {})) {
		const e = itemEffect(shop[id]);
		t.gx += e.gx;
		t.hx += e.hx;
		t.gm += e.gm;
		t.hm += e.hm;
	}
	return t;
}

/** A game of the base reward `base` ({exp, money, minutes}, bonus-free) with these totals. */
export const perGame = (t, base) => ({ exp: base.exp * (1 + t.gx) + t.hx * base.minutes, money: base.money * (1 + t.gm) + t.hm * base.minutes });

/**
 * A reference game from what I played lately: the average of `games` ({exp, money, playTime ms}), with what I wear now taken out
 * (`current` = the totals of my equipment), else sensible defaults.
 */
export function baseGame(games, current = { gx: 0, hx: 0, gm: 0, hm: 0 }) {
	const ok = (games ?? []).filter((g) => g && Number(g.playTime) > 0);
	if (!ok.length) return { exp: 300, money: 100, minutes: 5 };
	const avg = (k) => ok.reduce((s, g) => s + Number(g[k] || 0), 0) / ok.length;
	const minutes = avg('playTime') / 60000;
	return {
		exp: Math.max(1, (avg('exp') - current.hx * minutes) / (1 + current.gx)),
		money: Math.max(1, (avg('money') - current.hm * minutes) / (1 + current.gm)),
		minutes
	};
}

/**
 * The best combination.
 * @param {{item:any, count:number, expire?:number}[]} owned my box as a list (`ownedItems`)
 * @param {Record<string,string>} equip what I wear now
 * @param {'xp'|'ping'|'both'} goal
 * @param {{exp:number, money:number, minutes:number}} base
 * @param {string[]} equips the groups that can be worn
 * @returns {{picks:{slot:string, id:string, from:string|undefined}[], now:object, then:object}} `picks` are only the slots that change
 */
export function bestFormula({ owned, equip, goal, base, equips, shop, now = Date.now() }) {
	const worth = (e) => {
		const x = e.gx * base.exp + e.hx * base.minutes;
		const m = e.gm * base.money + e.hm * base.minutes;
		return goal === 'xp' ? x : goal === 'ping' ? m : x / base.exp + m / base.money;
	};
	const live = owned.filter((o) => isEquippable(o.item, equips) && hasEffect(itemEffect(o.item)) && !(o.expire && o.expire * 1000 < now) && o.count > 0);
	const bySlot = {};
	for (const o of live) {
		const slot = equipSlot(o.item, 'left');
		const key = slot === 'Mlhand' ? 'hand' : slot;
		(bySlot[key] ??= []).push(o);
	}
	const target = { ...equip };
	const picks = [];
	const set = (slot, id) => {
		if (target[slot] === id) return;
		picks.push({ slot, id, from: target[slot] });
		target[slot] = id;
	};
	const w = (id) => worth(itemEffect(shop[id]));
	for (const [key, list] of Object.entries(bySlot)) {
		if (key === 'hand') continue;
		const best = list.slice().sort((a, b) => worth(itemEffect(b.item)) - worth(itemEffect(a.item)))[0];
		const mine = equip[key];
		if (best && (!mine || worth(itemEffect(best.item)) > w(mine) + 1e-9)) set(key, best.item._id);
	}
	if (bySlot.hand) {
		// two hands: the two best "units" (an item I own twice can fill both), against what is in my hands now
		const units = bySlot.hand.flatMap((o) => Array.from({ length: Math.min(2, o.count) }, () => o.item._id)).sort((a, b) => w(b) - w(a));
		const want = units.slice(0, 2);
		const now2 = ['Mlhand', 'Mrhand'].map((s) => equip[s]).filter(Boolean);
		const sum = (ids) => ids.reduce((s, id) => s + w(id), 0);
		if (want.length && sum(want) > sum(now2) + 1e-9) {
			const slots = ['Mlhand', 'Mrhand'];
			const left = [...want];
			// an item that is already in a hand stays in it
			const assigned = {};
			for (const s of slots) {
				const i = left.indexOf(equip[s]);
				if (i >= 0) (assigned[s] = left[i]), left.splice(i, 1);
			}
			for (const s of slots) if (!assigned[s] && left.length) assigned[s] = left.shift();
			for (const s of slots) if (assigned[s]) set(s, assigned[s]);
		}
	}
	return { picks, now: totals(equip, shop), then: totals(target, shop) };
}
