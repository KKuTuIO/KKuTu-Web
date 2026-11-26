<script>
	import { t } from '../i18n.svelte.js';
	import Icon from '../lobby/Icon.svelte';
	import Dialog from '../lobby/Dialog.svelte';
	import Tooltip from '../ui/Tooltip.svelte';
	import BookList from './BookList.svelte';
	import RewardMark from './RewardMark.svelte';
	import { rewardShare } from './model.js';

	/**
	 * The 말모이 rule chip, with an (i) that says there is more: pointing at it lists the 말모이 that are on. Touch has no pointing (and a phone
	 * little room), so a tap opens the list as a sheet; so does the keyboard's Enter.
	 */
	let { books, label = t('play.malmoi.common.name'), class: cls = '' } = $props();
	const SHOWN = 8; // a tooltip cannot be scrolled; the sheet shows them all
	let open = $state(false);
	let width = $state(1280);
	let touch = false;
	const share = $derived(rewardShare(books));
	function press(e) {
		if (!e.detail || touch || width < 768) open = true;
	}
</script>

<svelte:window bind:innerWidth={width} />

{#if books.length}
	{#snippet tip()}
		<BookList {books} limit={SHOWN} />
		<div class="mt-1.5 border-t border-current/20 pt-1.5"><RewardMark {share} size={28} ring="var(--kk-text)" /></div>
	{/snippet}
	<Tooltip content={tip} placement="bottom">
		<button type="button" aria-haspopup="dialog" onpointerdown={(e) => (touch = e.pointerType === 'touch')} onclick={press} class="kk-chip relative inline-flex cursor-pointer items-center gap-1 whitespace-nowrap after:absolute after:-inset-2 {cls}">{label}<Icon name="info" size={14} /></button>
	</Tooltip>
	<Dialog bind:open title={t('play.malmoi.chip.sheetTitle')}>
		<BookList {books} />
		<div class="mt-3 border-t border-(color:--kk-line) pt-3"><RewardMark {share} size={34} /></div>
	</Dialog>
{/if}
