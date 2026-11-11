<script>
	import { tick } from 'svelte';
	import { t, i18n } from '../i18n.svelte.js';
	import { prefs } from '../prefs.svelte.js';
	import { maskBad } from '../badwords.js';
	import { isHidden } from '../chatFilter.js';
	import Checkbox from '../ui/Checkbox.svelte';
	import Dialog from './Dialog.svelte';
	import Icon from './Icon.svelte';

	/** How many of the latest lines the log shows when it opens, and how many more each scroll up adds. */
	export const LOG_LINES = 100;
	/** The server keeps 2000 characters of a reported chat. */
	const MAX_CHARS = 2000;

	/**
	 * 대화 기록 (`/f`, `/ㄹ`): the latest lines of the chat I can see, as plain text to read back. 대화 신고 turns the list into a choice, a
	 * checkbox on each line; 신고 then opens the report with exactly the picked lines attached. A report has one target, so once a line is
	 * picked the lines of everyone else wait; my own lines are not reportable. The list is a snapshot taken when the log opens, so it does
	 * not shift under the pointer. Whispers and system lines are not part of it. It opens on the newest lines, scrolled to the bottom; scrolling
	 * up to the top brings in 100 older ones, as far back as the client has kept the chat.
	 */
	let { open = $bindable(false), store } = $props();
	let all = $state([]); // every line the log can show, oldest first
	let count = $state(LOG_LINES); // how many of the newest are drawn
	let list = $state();
	const lines = $derived(all.slice(-count));
	const older = $derived(all.length - lines.length);
	let selecting = $state(false);
	let picked = $state([]); // keys of the picked lines (their place in the snapshot)

	$effect(() => {
		if (!open) return;
		const net = store.net;
		all = net.chat.filter((m) => !m.system && !m.whisper && !isHidden(m, net)).map((m, i) => ({ ...m, key: i }));
		count = LOG_LINES;
		selecting = false;
		picked = [];
		toBottom();
	});

	async function toBottom() {
		await tick();
		await tick(); // the dialog's list is mounted a moment after it opens
		if (list) list.scrollTop = list.scrollHeight;
	}
	/** Near the top: draw 100 older lines, and keep what the reader was looking at where it was. */
	async function onscroll() {
		if (!list || list.scrollTop > 40 || older <= 0) return;
		const before = list.scrollHeight - list.scrollTop;
		count += LOG_LINES;
		await tick();
		list.scrollTop = list.scrollHeight - before;
	}

	const time = (at) => new Date(at).toLocaleTimeString(i18n.language, { hour: 'numeric', minute: '2-digit' });
	const words = (m) => (prefs.filter === 'off' ? m.msg : maskBad(m.shown ?? m.msg));
	const reportable = (m) => !m.mine && !!m.uid;
	const author = $derived(lines.find((m) => picked.includes(m.key))?.uid ?? null);
	const waiting = (m) => !reportable(m) || (author !== null && m.uid !== author);
	const chosen = $derived(lines.filter((m) => picked.includes(m.key)));

	const set = (m, on) => (picked = on ? [...picked, m.key] : picked.filter((k) => k !== m.key));
	function stop() {
		selecting = false;
		picked = [];
	}
	function report() {
		if (!chosen.length) return;
		// what they actually typed, not what the word filter shows
		const chat = chosen.map((m) => m.msg).join('\n').slice(0, MAX_CHARS);
		open = false;
		store.hooks.openReport?.({ type: 'chat', id: chosen[0].uid, name: chosen[0].who, chat });
	}
</script>

{#snippet text(m)}
	<span class="min-w-0 flex-1 break-words whitespace-pre-wrap"><b class="mr-1.5 font-extrabold">{m.who}</b>{words(m)}</span>
	<span class="shrink-0 pt-0.5 text-[11px] text-(color:--kk-muted)">{time(m.at)}</span>
{/snippet}

<Dialog bind:open title={t('play.chatLog.title')} size="lg">
	<div class="flex flex-col gap-3">
		{#if all.length}
			{#if selecting}<p class="text-xs text-(color:--kk-muted)">{t(author ? 'play.chatLog.sameAuthor' : 'play.chatLog.hint')}</p>{/if}
			<ul bind:this={list} {onscroll} role="log" aria-label={t('play.chatLog.title')} class="flex max-h-[55vh] flex-col gap-1 overflow-y-auto rounded-xl border-[1.5px] border-(color:--kk-line) p-2 text-sm">
				{#each lines as m (m.key)}
					<li data-line={m.key} class="flex items-start gap-2 rounded-lg px-2 {selecting ? '' : 'py-1'} {selecting && picked.includes(m.key) ? 'bg-(--kk-accent-soft)' : ''}">
						{#if selecting}
							<Checkbox dense checked={picked.includes(m.key)} disabled={waiting(m)} onchange={(on) => set(m, on)}>{@render text(m)}</Checkbox>
						{:else}
							{@render text(m)}
						{/if}
					</li>
				{/each}
			</ul>
			<div class="flex items-center justify-end gap-3">
				{#if selecting}
					<span class="mr-auto text-sm text-(color:--kk-muted)" aria-live="polite">{chosen.length ? t('play.chatLog.selected', { count: chosen.length }) : ''}</span>
					<button type="button" onclick={stop} class="kk-btn min-h-11 px-5">{t('play.confirm.cancel')}</button>
					<button type="button" disabled={!chosen.length} onclick={report} class="kk-btn kk-btn-primary inline-flex min-h-11 items-center gap-1.5 px-5"><Icon name="flag" size={19} />{t('play.chatLog.report')}</button>
				{:else}
					<button type="button" onclick={() => (selecting = true)} class="kk-btn inline-flex min-h-11 items-center gap-1.5 px-5"><Icon name="flag" size={19} />{t('play.chatLog.report')}</button>
				{/if}
			</div>
		{:else}
			<p class="py-10 text-center text-sm text-(color:--kk-muted)">{t('play.chatLog.empty')}</p>
		{/if}
	</div>
</Dialog>
