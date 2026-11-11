/**
 * The replay page outside the game: what the shared components (ReplayScreen, the players) need from "the store", without a connection.
 * In the game client the lobby store is that; on the portal (/records) it is this: the locale, the game's metadata, the shop and who I am.
 */
import { i18n, loadLocale } from './i18n.svelte.js';
import { loadShop } from './images.js';
import { loadAuth } from '../session.js';

/** @returns the host: {net: {id, guest, meta}, shop, hooks, replayView, portal} */
export async function createPortalHost() {
	await loadLocale();
	const [meta, shop, auth] = await Promise.all([
		fetch('/api/play/meta').then((r) => {
			if (!r.ok) throw new Error(`meta ${r.status}`);
			return r.json();
		}),
		loadShop().catch(() => ({})),
		loadAuth().catch(() => null)
	]);
	const id = auth?.profileId ?? '';
	return {
		net: { id, guest: !id || auth?.status === 'Guest user', meta: { ...meta, labels: i18n.messages } },
		shop,
		hooks: {},
		replayView: null,
		portal: true
	};
}
