<script>
	import { t } from '../i18n.svelte.js';
	import Dialog from './Dialog.svelte';
	import PushScreen from './PushScreen.svelte';
	import RankingPanel from './RankingPanel.svelte';

	let { store, open = $bindable(false) } = $props();
	// Phone: the ranking is a screen of its own (a place to go), not a sheet
	let winW = $state(typeof innerWidth === 'undefined' ? 1280 : innerWidth);
	const phone = $derived(winW < 768);

	// The profile floats above the page, so on a desktop the ranking steps aside. On a phone the profile is a screen pushed over this
	// one, so the ranking stays underneath and "back" returns to it.
	const showProfile = (id) => {
		store.hooks.openProfile?.(id);
		if (!phone) open = false;
	};
</script>

<svelte:window bind:innerWidth={winW} />

{#if phone}
	<PushScreen bind:open title={t('play.rank.title')}><div class="p-3"><RankingPanel {store} active={open} onprofile={showProfile} /></div></PushScreen>
{:else}
	<Dialog bind:open title={t('play.rank.title')} size="lg">
		<RankingPanel {store} active={open} onprofile={showProfile} />
	</Dialog>
{/if}
