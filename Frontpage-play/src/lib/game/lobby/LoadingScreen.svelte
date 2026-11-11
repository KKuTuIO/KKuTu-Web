<script>
	import GameLoading from './GameLoading.svelte';
	import { onMount } from 'svelte';
	import { t } from '../i18n.svelte.js';

	let { store } = $props();
	const net = $derived(store.net);

	// Stay up until the connection is live AND assets are in, but never flash by in under a moment.
	let minDone = $state(false);
	const show = $derived(net.status === 'connecting' || (net.status === 'ready' && !(net.assetsReady && minDone)));
	const progress = $derived(net.status === 'connecting' ? (net.meta.modes.length ? 35 : 12) : net.assetsReady ? 100 : 78);

	onMount(() => {
		const min = setTimeout(() => (minDone = true), 1200);
		// the page's own covering (app.html) hands over to the veil, which has the same squares
		requestAnimationFrame(() => requestAnimationFrame(() => document.documentElement.classList.remove('kk-boot')));
		return () => clearTimeout(min);
	});
</script>

<GameLoading loading={show} label={t('play.loading.label')} message={t('play.loading.label')} {progress} minShown={0} instant handoff="read" />
