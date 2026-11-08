<script>
	import { t } from '../i18n.svelte.js';
	import Dialog from './Dialog.svelte';
	import RankingPanel from './RankingPanel.svelte';

	let { store, open = $bindable(false) } = $props();

	// The profile window floats above the page, so the ranking steps aside.
	const showProfile = (id) => {
		store.hooks.openProfile?.(id);
		open = false;
	};
</script>

<Dialog bind:open title={t('play.rank.title')} size="lg">
	<RankingPanel {store} active={open} onprofile={showProfile} />
</Dialog>
