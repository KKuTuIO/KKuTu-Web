<script>
	import { fly } from 'svelte/transition';
	import { quintOut } from 'svelte/easing';
	import { ms } from '../motion.js';
	import { t } from '../i18n.svelte.js';
	import Icon from '../lobby/Icon.svelte';
	import DictList from './DictList.svelte';
	import DictDetail from './DictDetail.svelte';
	import DictForm from './DictForm.svelte';
	import LicenseCardScreen from './LicenseCardScreen.svelte';
	import { toast } from '../ui/toast.svelte.js';
	import { attempt, bridge, find, joinByLink, loadMe, look, me, nav } from './store.svelte.js';

	/**
	 * 말모이 (words gathered by players): its own page of the game, like the 리플레이 page. Like the other pages it has no title of its own: each view
	 * starts with one header row (the way back, then its tabs). `lead` is the new lobby's back button.
	 * The data is the server's (`/api/malmoi`, see store.svelte.js).
	 */
	let { store, lead } = $props();
	$effect(() => {
		look.name = store?.me?.name ?? '';
		look.equip = store?.me?.equip ?? {};
		look.shop = store?.shop ?? {};
		look.parts = store?.net?.meta?.moremiParts ?? [];
	});
	// Ping is the game's own number: shown from there, and told there when the server took some
	$effect(() => {
		me.ping = store?.me?.money ?? 0;
	});
	bridge.setMoney = (money) => {
		if (store?.net?.users?.[store.net.id] && money != null) store.net.users[store.net.id].money = money;
	};
	loadMe().catch(() => {});
	// an invite link (?join=...) registers the book it belongs to and opens it
	$effect(() => {
		const token = new URL(location.href).searchParams.get('join');
		if (!token) return;
		const url = new URL(location.href);
		url.searchParams.delete('join');
		history.replaceState(history.state, '', url);
		void attempt(() => joinByLink(token), toast).then((id) => id && (toast(t('play.malmoi.common.registeredToast'), { kind: 'success' }), Object.assign(nav, { view: 'detail', id: String(id), tab: 'words' })));
	});
	const dict = $derived(nav.view === 'detail' ? find(nav.id) : null);
	// one way back: from a 말모이 to the list, from the form to where it was opened (the stage's own button leaves the page, from the list)
	const back = () => (nav.view = nav.view === 'form' && find(nav.id) ? 'detail' : 'list');
</script>

{#snippet backBtn()}
	<button type="button" aria-label={t('play.dialog.back')} onclick={back} class="grid size-11 shrink-0 place-items-center rounded-full bg-white/90 text-slate-700 shadow-md transition-transform hover:scale-105 hover:bg-white"><Icon name="arrow_back" size={24} /></button>
{/snippet}

<section aria-label={t('play.nav.malmoi')} class="relative flex min-h-0 flex-1 flex-col {lead ? 'bare' : ''}">
	{#key `${nav.view}:${nav.id}`}
		<div in:fly|global={{ y: 14, duration: ms(240), easing: quintOut }} class="flex min-h-0 flex-1 flex-col">
			{#if nav.view === 'form'}<DictForm lead={backBtn} />
			{:else if dict}<DictDetail {dict} lead={backBtn} />
			{:else}<DictList {lead} />{/if}
		</div>
	{/key}
	<LicenseCardScreen {store} />
</section>

<style>
	/* on the stage the cards are translucent, so the blurred picture shows through (not the dialogs, which are cards too) */
	section.bare :global(.kk-card:not(dialog):not(dialog *):not([role='group'])) {
		background: color-mix(in srgb, var(--kk-surface) 88%, transparent);
	}
</style>
