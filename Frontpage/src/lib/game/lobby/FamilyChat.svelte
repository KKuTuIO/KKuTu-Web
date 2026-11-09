<script>
	import { tick } from 'svelte';
	import { t, i18n } from '../i18n.svelte.js';
	import { family } from '../familyStore.svelte.js';
	import { maskBad } from '../badwords.js';
	import { prefs } from '../prefs.svelte.js';
	import Icon from './Icon.svelte';
	import EmptyState from './EmptyState.svelte';

	/**
	 * The 패밀리 tab of the chat: the family's own line, only for its members. A line shows the profile that spoke, and the 대표 프로필
	 * beside it when that is a different one (the member is the account, so people know who they are talking to).
	 * @type {{store:any, uid:string}}
	 */
	let { store, uid } = $props();
	const fam = $derived(family.mine);
	const rep = $derived(fam?.members.find((m) => m.accountId === family.me)?.name ?? '');
	let text = $state('');
	let log = $state();
	const words = (m) => (prefs.filter === 'off' ? m.msg : maskBad(m.msg));
	const clock = (at) => new Date(at).toLocaleTimeString(i18n.language, { hour: 'numeric', minute: '2-digit' });
	const label = (m) => (m.rep && m.rep !== m.who ? t('play.family.chat.as', { who: m.who, rep: m.rep }) : m.who);

	// Being on this tab is reading it.
	$effect(() => {
		family.watch(true);
		return () => queueMicrotask(() => family.watch(false));
	});
	$effect(() => {
		family.chat.length;
		tick().then(() => log?.scrollTo({ top: log.scrollHeight }));
	});

	function send(e) {
		e.preventDefault();
		const who = store.me?.name ?? rep;
		if (family.send(text, who, rep)) text = '';
	}
</script>

{#if !fam}
	<div class="grid min-h-0 flex-1 place-items-center p-3"><EmptyState compact size={90} title={t('play.family.chat.none')} /></div>
{:else}
	<div bind:this={log} role="log" aria-live="polite" id="{uid}-family-log" class="flex min-h-0 flex-1 flex-col gap-2.5 overflow-y-auto p-3 text-sm">
		{#each family.chat as m, i (i)}
			{#if m.system}
				<p class="flex items-center gap-3 text-xs text-(color:--kk-muted)"><span class="h-px flex-1 bg-(--kk-line)"></span><span>{t(`play.family.chat.sys.${m.msg}`, { name: fam.name })}</span><span class="h-px flex-1 bg-(--kk-line)"></span></p>
			{:else if m.mine}
				<div class="flex items-end justify-end gap-1.5">
					<span class="pb-0.5 text-[11px] text-(color:--kk-muted)">{clock(m.at)}</span>
					<p class="max-w-[75%] rounded-xl rounded-br-sm bg-(--kk-accent-soft) px-3 py-2 break-words whitespace-pre-wrap">{words(m)}</p>
				</div>
			{:else}
				<div class="flex flex-col items-start gap-0.5">
					<span class="px-1 text-xs font-extrabold">{label(m)}</span>
					<div class="flex items-end gap-1.5">
						<p class="max-w-[16rem] rounded-xl rounded-tl-sm bg-(--kk-surface-2) px-3 py-2 break-words whitespace-pre-wrap">{words(m)}</p>
						<span class="pb-0.5 text-[11px] text-(color:--kk-muted)">{clock(m.at)}</span>
					</div>
				</div>
			{/if}
		{/each}
	</div>
	<form onsubmit={send} class="flex items-center gap-2 border-t border-(color:--kk-line) bg-(--kk-panel) p-2.5">
		<input bind:value={text} aria-label={t('play.family.chat.input')} placeholder={t('play.family.chat.placeholder')} maxlength="200" autocomplete="off" class="h-11 min-w-0 flex-1 kk-input px-3" />
		<button class="kk-btn kk-btn-primary grid size-11 shrink-0 place-items-center p-0" aria-label={t('play.chat.send')} disabled={!text.trim()}><Icon name="send" size={20} fill /></button>
	</form>
{/if}
