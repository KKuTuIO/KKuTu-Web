<script>
	import { tick } from 'svelte';
	import { t, i18n } from '../i18n.svelte.js';
	import Icon from './Icon.svelte';
	import Tabs from '../ui/Tabs.svelte';
	import { toast } from '../ui/toast.svelte.js';

	let { store } = $props();
	const chat = $derived(store.net.chat);
	let open = $state(false);
	let text = $state('');
	let tab = $state('lobby');
	let list = $state();
	let input = $state();
	let seen = $state(0); // how many messages were on screen when the panel was last open
	const unread = $derived(open ? 0 : chat.slice(seen).filter((m) => !m.mine).length);
	const tabs = $derived([
		{ id: 'lobby', label: t('play.chat.lobbyTab') },
		{ id: 'whisper', label: t('play.chat.whisper') }
	]);
	const time = (at) => new Date(at).toLocaleTimeString(i18n.language, { hour: 'numeric', minute: '2-digit' });

	// Whispers are planned; the tab says so instead of opening an empty view.
	function pickTab(id) {
		if (id === 'whisper') toast(t('play.chat.whisperTbd'), { kind: 'info' });
		else tab = id;
	}

	async function toBottom() {
		await tick();
		if (list) list.scrollTop = list.scrollHeight;
	}
	// Follow new messages while open; remember where we were when it closes.
	$effect(() => {
		chat.length;
		if (open) {
			seen = chat.length;
			toBottom();
		}
	});
	async function show() {
		open = true;
		await tick();
		await toBottom();
		input?.focus();
	}
	function send(e) {
		e.preventDefault();
		store.sendChat(text);
		text = '';
	}
</script>

{#if open}
	<!-- Docked to the bottom-right corner (not floating), taller than a popup: the lobby chat is a main feature. -->
	<!-- Escape is a convenience; keyboard users also have the close button. -->
	<!-- svelte-ignore a11y_no_noninteractive_element_interactions -->
	<section
		aria-label={t('play.chat.title')}
		onkeydown={(e) => e.key === 'Escape' && (open = false)}
		class="kk-card fixed right-0 bottom-0 z-20 flex h-[min(36rem,calc(100dvh-5rem))] w-full flex-col overflow-hidden rounded-b-none border-r-0 border-b-0 shadow-none sm:w-[24rem] sm:rounded-tr-none"
	>
		<div class="flex items-stretch border-b border-(color:--kk-line) pr-1">
			<div class="min-w-0 flex-1">
				<Tabs bind:value={() => tab, pickTab} {tabs} idPrefix="chat" label={t('play.chat.title')} />
			</div>
			<button type="button" aria-label={t('play.chat.close')} onclick={() => (open = false)} class="grid size-11 shrink-0 place-items-center text-(color:--kk-muted) hover:text-(color:--kk-text)"><Icon name="keyboard_arrow_down" size={26} /></button>
		</div>
		<p class="flex items-center gap-1.5 border-b border-(color:--kk-line) bg-(--kk-panel) px-3 py-1.5 text-[11px] font-bold text-(color:--kk-muted)"><Icon name="shield" size={14} />{t('play.chat.filterOn')}</p>

		<div bind:this={list} role="log" aria-live="polite" id="chat-panel-lobby" class="flex min-h-0 flex-1 flex-col gap-2.5 overflow-y-auto p-3 text-sm">
			{#each chat as m, i (i)}
				{#if m.system}
					<p class="flex items-center gap-3 text-xs text-(color:--kk-muted)"><span class="h-px flex-1 bg-(--kk-line)"></span><span>{m.msg}</span><span class="h-px flex-1 bg-(--kk-line)"></span></p>
				{:else if m.mine}
					<div class="flex items-end justify-end gap-1.5">
						<span class="pb-0.5 text-[11px] text-(color:--kk-muted)">{time(m.at)}</span>
						<p class="max-w-[75%] rounded-xl rounded-br-sm bg-(--kk-accent-soft) px-3 py-2 break-words whitespace-pre-wrap">{m.msg}</p>
					</div>
				{:else}
					<div class="flex flex-col items-start gap-0.5">
						<span class="px-1 text-xs font-extrabold">{m.who}</span>
						<div class="flex items-end gap-1.5">
							<p class="max-w-[16rem] rounded-xl rounded-tl-sm bg-(--kk-surface-2) px-3 py-2 break-words whitespace-pre-wrap">{m.msg}</p>
							<span class="pb-0.5 text-[11px] text-(color:--kk-muted)">{time(m.at)}</span>
						</div>
					</div>
				{/if}
			{/each}
		</div>

		<form onsubmit={send} class="flex items-center gap-2 border-t border-(color:--kk-line) bg-(--kk-panel) p-2.5">
			<input bind:this={input} bind:value={text} aria-label={t('play.chat.input')} placeholder={t('play.chat.placeholder')} maxlength="200" autocomplete="off" class="h-11 min-w-0 flex-1 kk-input px-3" />
			<button class="kk-btn kk-btn-primary grid size-11 shrink-0 place-items-center p-0" aria-label={t('play.chat.send')} disabled={!text.trim()}><Icon name="send" size={20} fill /></button>
		</form>
	</section>
{:else}
	<!-- Secondary look on purpose: the chat is never the main action. -->
	<button type="button" aria-label={unread ? `${t('play.chat.toggle')} (${t('play.chat.unread', { count: unread })})` : t('play.chat.toggle')} aria-expanded="false" onclick={show} class="kk-btn fixed right-4 bottom-4 z-20 grid size-14 place-items-center p-0">
		<Icon name="chat" size={26} />
		{#if unread}<span class="absolute -top-1.5 -right-1.5 grid h-5 min-w-5 place-items-center rounded-full bg-(--kk-danger) px-1 text-[11px] font-extrabold text-white">{unread > 99 ? '99+' : unread}</span>{/if}
	</button>
{/if}
