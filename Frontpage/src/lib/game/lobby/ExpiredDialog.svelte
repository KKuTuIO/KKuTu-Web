<script>
	import { onMount } from 'svelte';
	import { t } from '../i18n.svelte.js';
	import { itemName, loadGoodDetails } from '../shop.js';
	import { plainText } from '../protocol.js';
	import Dialog from './Dialog.svelte';
	import ShopThumb from './ShopThumb.svelte';

	/**
	 * The items that ran out while I was away (the server lists them when I connect; the legacy client only put a line per item
	 * in the chat): a plain list, shown once I am in, then the list is cleared.
	 */
	let { store } = $props();
	let details = $state(null);
	let open = $state(false);
	let ids = $state([]);
	onMount(async () => (details = await loadGoodDetails()));

	$effect(() => {
		if (store.net.status !== 'ready' || !store.net.expired.length || open) return;
		ids = [...new Set(store.net.expired)];
		store.net.expired = [];
		open = true;
	});
</script>

<Dialog bind:open title={t('play.expired.title')}>
	<div class="flex flex-col gap-4">
		<p class="text-sm leading-relaxed">{plainText(t('play.expired.message'))}</p>
		<ul class="flex max-h-72 flex-col gap-1.5 overflow-y-auto">
			{#each ids as id (id)}
				<li class="flex items-center gap-3 rounded-xl border border-(color:--kk-line) bg-(--kk-surface) p-2 shadow-[0_2px_0_var(--kk-line)]">
					<span class="grid size-11 shrink-0 place-items-center rounded-lg bg-(--kk-surface-2)"><ShopThumb {id} shop={store.shop} class="size-9" /></span>
					<b class="min-w-0 flex-1 truncate text-sm">{itemName(details, id)}</b>
				</li>
			{/each}
		</ul>
		<button type="button" onclick={() => (open = false)} class="kk-btn kk-btn-primary min-h-12 text-base">{t('play.cmd.code.ok')}</button>
	</div>
</Dialog>
