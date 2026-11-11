<script>
	import { t } from '../i18n.svelte.js';
	import Dialog from './Dialog.svelte';
	import NickName from './NickName.svelte';

	/**
	 * Vote on removing someone from the room. Only players who are neither the master nor the accused vote.
	 * Like the legacy client it counts down 10 seconds and then votes "yes" for you.
	 */
	let { store } = $props();
	const SECONDS = 10;
	const vote = $derived(store.net.kickVote);
	const target = $derived(vote ? store.net.users[vote.target] : null);
	const eligible = $derived(!!vote && !!store.currentRoom && vote.target !== store.net.id && store.currentRoom.master !== store.net.id);
	let open = $state(false);
	let left = $state(SECONDS);

	$effect(() => {
		if (!vote || !eligible) {
			open = false;
			return;
		}
		left = SECONDS;
		open = true;
		const timer = setInterval(() => {
			left -= 1;
			if (left <= 0) {
				clearInterval(timer);
				store.answerKickVote(true); // no answer counts as agreement
			}
		}, 1000);
		return () => clearInterval(timer);
	});

	const answer = (agree) => store.answerKickVote(agree);
</script>

<Dialog bind:open title={t('play.kick.title')}>
	<div class="flex flex-col gap-4">
		<p class="text-base font-bold break-keep">{#if target}<NickName user={target} hideTag />{/if}{t('play.kick.text', { nick: '' })}</p>
		<div class="kk-bar h-2" role="progressbar" aria-valuemin="0" aria-valuemax={SECONDS} aria-valuenow={left}><span class="transition-[width] duration-1000 ease-linear" style:width="{(left / SECONDS) * 100}%"></span></div>
		<div class="flex gap-2">
			<button type="button" class="kk-btn kk-btn-primary min-h-12 flex-1" onclick={() => answer(true)}>{t('play.kick.yes')}</button>
			<button type="button" class="kk-btn min-h-12 flex-1" onclick={() => answer(false)}>{t('play.kick.no')}</button>
		</div>
	</div>
</Dialog>
