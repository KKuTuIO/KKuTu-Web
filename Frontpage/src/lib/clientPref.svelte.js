/** Which game client the home page opens: the new Svelte client (`/play/{n}`) or the legacy one (`/game/server/{n}`). */
const KEY = 'kkutu.client';

export const clientPref = $state({ value: 'new' });

/** Read the saved choice. Call from onMount (storage is unavailable during prerender). */
export function loadClientPref() {
	try {
		if (localStorage.getItem(KEY) === 'legacy') clientPref.value = 'legacy';
	} catch {
		/* private mode / blocked storage: keep the default */
	}
}

export function setClientPref(value) {
	clientPref.value = value === 'legacy' ? 'legacy' : 'new';
	try {
		localStorage.setItem(KEY, clientPref.value);
	} catch {
		/* not persisted, still applies for this visit */
	}
}

/** URL that enters channel `n`. */
export const channelUrl = (n) => (clientPref.value === 'legacy' ? `/game/server/${n}` : `/play/${n}`);

/** URL that enters the recommended channel (the server picks it). */
export const recommendedUrl = () => (clientPref.value === 'legacy' ? '/game/recommended' : '/game/recommended?client=new');
