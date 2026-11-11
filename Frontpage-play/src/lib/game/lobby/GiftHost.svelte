<script>
	import { onMount } from 'svelte';
	import { t } from '../i18n.svelte.js';
	import { loadGoodDetails, tally } from '../shop.js';
	import ObtainScreen from './ObtainScreen.svelte';

	/**
	 * What an admin gave me with `#give` (the server's `obtain` with `gift`): shown on the shared reward screen, on top of whatever is open.
	 * Several gifts that arrive while it is open are added to it.
	 */
	let { store } = $props();
	let open = $state(false);
	let items = $state([]);
	let details = $state(null);
	onMount(() => void loadGoodDetails().then((d) => (details = d)));
	$effect(() => {
		if (!store.net.gifts.length) return;
		const taken = store.net.gifts.splice(0);
		items = tally([...(open ? items : []), ...taken]);
		open = true;
	});
</script>

{#if details}<ObtainScreen bind:open title={t('play.obtain.acquired')} {items} shop={store.shop} {details} ribbon ambient="shine" palette="teal" />{/if}
