import { fetchEnchant } from './enchant.js';

/**
 * My enchanted copies as the server last told it (`items` of `GET /enchant`: per item the pity and its copies), shared by everything that
 * puts items on or shows them: the inventory, the 옷장 and 해커레미. `items` is null until loaded (or for a guest); `units` says how to write each effect's number.
 */
export const enchantStore = $state({ items: null, units: {} });

/** Asks the server again; returns the fresh `items` (or null). */
export async function refreshEnchants() {
	const res = await fetchEnchant();
	enchantStore.items = res.error ? null : res.items;
	if (!res.error) enchantStore.units = res.effects ?? {}; // effect id -> unit, to write a line out
	return enchantStore.items;
}
