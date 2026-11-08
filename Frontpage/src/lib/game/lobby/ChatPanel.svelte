<script>
	import { tick, untrack } from 'svelte';
	import { backOut, cubicOut } from 'svelte/easing';
	import { ms, reducedMotion } from '../motion.js';
	import { t, i18n } from '../i18n.svelte.js';
	import Icon from './Icon.svelte';
	import Tabs from '../ui/Tabs.svelte';
	import { toast } from '../ui/toast.svelte.js';
	import { prefs } from '../prefs.svelte.js';
	import { isHidden } from '../chatFilter.js';
	import { maskBad } from '../badwords.js';
	import { whisperQuery } from '../dm.js';
	import DmPanel from './DmPanel.svelte';
	import DmMembers from './DmMembers.svelte';

	/**
	 * `inline`: a permanent panel in the lobby layout (desktop, where the old client kept its chat: under the room list).
	 * Otherwise: a button that opens a docked bottom-right sheet (mobile, and on desktop wherever the page wants the chat
	 * out of the way, `fab`, like in the 아카데미).
	 */
	let { store, inline = false, fab = false, height = null, idSuffix = '' } = $props();
	const uid = $derived(`chat-${inline ? 'inline' : 'sheet'}${idSuffix}`); // two instances can coexist in the DOM, so ids must differ
	// While a replay plays the chat has one more tab: the chat of that game (read only)
	const rnet = $derived(store.replayView?.net);
	const chat = $derived(tab === 'replay' && rnet ? rnet.chat : store.net.chat);
	// What I get to see: not blocked players, 손님 채팅 거부, 관전 채팅 무시, 귓속말 거부 (see chatFilter.js).
	const visible = $derived(chat.filter((m) => !isHidden(m, store.net)));
	const words = (m) => (prefs.filter === 'off' ? m.msg : maskBad(m.shown ?? m.msg));
	let openSheet = $state(false);
	const open = $derived(inline || openSheet);
	let text = $state('');
	let tab = $state('lobby');
	let list = $state();
	let input = $state();
	let seen = $state(0); // how many messages were on screen when the panel was last open
	const unread = $derived(open ? 0 : chat.slice(seen).filter((m) => !m.mine).length + store.dmUnread);
	const tabs = $derived([{ id: 'lobby', label: store.net.myRoom ? t('play.chat.roomTab') : t('play.chat.lobbyTab') }, { id: 'dm', label: t('play.dm.tab'), count: store.dmUnread, alert: true }, ...(rnet ? [{ id: 'replay', label: t('kkutu.js.replay') }] : [])]);
	const time = (at) => new Date(at).toLocaleTimeString(i18n.language, { hour: 'numeric', minute: '2-digit' });


	let hadReplay = false;
	$effect(() => {
		const has = !!rnet;
		if (has && !hadReplay) tab = 'replay';
		else if (!has && tab === 'replay') tab = 'lobby';
		hadReplay = has;
	});

	// The docked sheet slides up from the bottom edge and back down; the inline panel never animates.
	function sheet(node) {
		if (inline) return { duration: 0 };
		return { duration: ms(220), easing: cubicOut, css: (t, u) => `transform:translateY(${u * 28}%);opacity:${Math.min(1, t * 1.6)}` };
	}

	// Follow new lines only while the reader is at the bottom: someone who scrolled up to read stays where they are,
	// and gets a button back down with the number of lines that arrived meanwhile.
	let stick = $state(true);
	let lastRead = $state(null); // the newest line that was on screen while following
	const newest = $derived(visible[visible.length - 1] ?? null);
	const fresh = $derived(stick || !lastRead ? 0 : Math.max(0, visible.length - 1 - visible.lastIndexOf(lastRead)));
	const nearBottom = () => list.scrollHeight - list.scrollTop - list.clientHeight < 24;
	function onscroll() {
		stick = nearBottom();
		if (stick) lastRead = newest;
	}
	function jump(node) {
		return { duration: ms(260), easing: backOut, css: (t, u) => `transform:translateY(${u * 16}px) scale(${0.8 + 0.2 * t});opacity:${Math.min(1, t * 2)}` };
	}

	async function toBottom(smooth = false) {
		await tick();
		if (!list) return;
		list.scrollTo({ top: list.scrollHeight, behavior: smooth && !reducedMotion() ? 'smooth' : 'auto' });
		stick = true;
		lastRead = newest;
	}
	// New line (or the panel opening): follow it if we are following. `newest` also changes when the 200-line cap
	// drops old lines, when the length stays the same.
	$effect(() => {
		newest;
		chat.length;
		if (!open) return;
		seen = chat.length;
		if (stick) toBottom();
	});
	// Resizing the panel must not lose the bottom either.
	$effect(() => {
		if (!list || typeof ResizeObserver === 'undefined') return;
		const ro = new ResizeObserver(() => stick && (list.scrollTop = list.scrollHeight));
		ro.observe(list);
		return () => ro.disconnect();
	});
	// In a game the box warns against typing help (legacy placeholder).
	const mobileInput = typeof matchMedia !== 'undefined' && !matchMedia('(min-width: 1024px)').matches;
	// Something (the user menu's 귓속말) put text into the chat box: take it and put the cursor there.
	let appliedDraft = untrack(() => store.draft.nonce);
	$effect(() => {
		const d = store.draft;
		if (d.nonce === appliedDraft) return;
		appliedDraft = d.nonce;
		const desktop = matchMedia('(min-width: 1024px)').matches;
		// The desktop panel answers on desktop, the sheet elsewhere (and on desktop too when the chat is only a button).
		if (inline ? !desktop : desktop && !fab) return;
		text = d.text;
		if (inline) input?.focus();
		else show();
	});
	// Something asked for a conversation (the user menu's 귓속말): show the 귓속말 tab, open at that person.
	let appliedDm = untrack(() => store.dmRequest.nonce);
	$effect(() => {
		const r = store.dmRequest;
		if (r.nonce === appliedDm) return;
		appliedDm = r.nonce;
		const desktop = matchMedia('(min-width: 1024px)').matches;
		if (inline ? !desktop : desktop && !fab) return;
		tab = 'dm';
		if (!inline) show();
	});
	// After "/e ": the people online, to pick from (arrow keys, Tab / Enter, or click) as Discord does after a slash command
	let sIdx = $state(0);
	let dismissed = $state('');
	const wq = $derived(whisperQuery(text));
	const suggest = $derived(wq !== null && text !== dismissed ? store.dmMembers(wq, 8) : []);
	$effect(() => {
		wq;
		sIdx = 0;
	});
	function suggestPick(r) {
		const cmd = /^\/\S+/.exec(text)?.[0] ?? '/e';
		text = `${cmd} ${r.handle} `;
		input?.focus();
	}
	function suggestKey(e) {
		if (!suggest.length) return false;
		if (e.key === 'ArrowDown' || e.key === 'ArrowUp') {
			e.preventDefault();
			sIdx = (sIdx + (e.key === 'ArrowDown' ? 1 : -1) + suggest.length) % suggest.length;
			return true;
		}
		if (e.key === 'Tab' || (e.key === 'Enter' && !e.isComposing)) {
			e.preventDefault();
			suggestPick(suggest[sIdx]);
			return true;
		}
		if (e.key === 'Escape') {
			e.stopPropagation();
			dismissed = text;
			return true;
		}
		return false;
	}
	async function show() {
		openSheet = true;
		stick = true;
		await tick();
		await toBottom();
		input?.focus();
	}
	// the chat box doubles as the answer box: guarded and traced like the answer field
	const watch = (node) => store.watchField(node, 'chat');
	function send(e) {
		e.preventDefault();
		store.sendChat(text);
		toBottom(); // my own line always takes me back to the bottom
		text = '';
	}
</script>

{#if open}
	<!-- Docked to the bottom-right corner (not floating), taller than a popup: the lobby chat is a main feature. -->
	<!-- Escape is a convenience; keyboard users also have the close button. -->
	<!-- svelte-ignore a11y_no_noninteractive_element_interactions -->
	<section
		aria-label={t('play.chat.title')}
		transition:sheet
		onkeydown={(e) => !inline && e.key === 'Escape' && (openSheet = false)}
		style:height={inline && height ? `${height}px` : undefined}
		class="kk-card flex overflow-hidden {inline ? '' : 'flex-col'} {inline ? 'h-60 shrink-0' : `fixed bottom-0 z-20 h-[min(36rem,calc(100dvh-5rem))] w-full rounded-b-none border-b-0 shadow-none sm:w-[24rem] ${prefs.hand === 'left' ? 'left-0 border-l-0 sm:rounded-tl-none' : 'right-0 border-r-0 sm:rounded-tr-none'}`}"
	>
		{#if inline}
			<!-- Beside the lobby the tabs run down the left edge: chat lines need the width more than the height. -->
			<div class="flex w-[4.5rem] shrink-0 flex-col justify-between border-r border-(color:--kk-line) bg-(--kk-panel)">
				<Tabs bind:value={tab} {tabs} idPrefix={uid} label={t('play.chat.title')} orientation="vertical" />
			</div>
		{:else}
			<!-- The sheet is narrow and tall, so its tabs lie along the top, with the way out at the right. -->
			<div class="flex shrink-0 items-center border-b border-(color:--kk-line) bg-(--kk-panel) pr-1 pl-2">
				<div class="min-w-0 flex-1"><Tabs bind:value={tab} {tabs} idPrefix={uid} label={t('play.chat.title')} /></div>
				<button type="button" aria-label={t('play.chat.close')} onclick={() => (openSheet = false)} class="grid size-11 shrink-0 place-items-center text-(color:--kk-muted) hover:text-(color:--kk-text)"><Icon name="keyboard_arrow_down" size={26} /></button>
			</div>
		{/if}

		<div class="flex min-h-0 min-w-0 flex-1 flex-col">
		{#if tab === 'dm'}
			<DmPanel {store} {uid} />
		{:else}
		<div class="relative flex min-h-0 flex-1 flex-col">
		<div bind:this={list} {onscroll} role="log" aria-live="polite" id="{uid}-log" class="flex min-h-0 flex-1 flex-col gap-2.5 overflow-y-auto p-3 text-sm">
			{#each visible as m, i (i)}
				{#if m.system}
					<p class="flex items-center gap-3 text-xs text-(color:--kk-muted)"><span class="h-px flex-1 bg-(--kk-line)"></span><span>{m.msg}</span><span class="h-px flex-1 bg-(--kk-line)"></span></p>
				{:else if m.mine}
					<div class="flex items-end justify-end gap-1.5">
						<span class="pb-0.5 text-[11px] text-(color:--kk-muted)">{time(m.at)}</span>
						<p class="max-w-[75%] rounded-xl rounded-br-sm bg-(--kk-accent-soft) px-3 py-2 break-words whitespace-pre-wrap">{#if m.whisper}<b class="mr-1 text-(color:--kk-whisper)">{t('play.chat.whisperLabel')} {m.who}</b>{/if}{words(m)}</p>
					</div>
				{:else}
					<div class="flex flex-col items-start gap-0.5">
						<span class="flex items-center gap-1 px-1 text-xs font-extrabold"><button type="button" onclick={() => m.uid && store.hooks.openProfile?.(m.uid)} class="hover:underline">{m.who}</button>{#if prefs.spectatorTag && m.spectator}<span class="kk-chip">{t('play.room.status.spectate')}</span>{/if}</span>
						<div class="flex items-end gap-1.5">
							<p class="max-w-[16rem] rounded-xl rounded-tl-sm bg-(--kk-surface-2) px-3 py-2 break-words whitespace-pre-wrap">{#if m.whisper}<b class="mr-1 text-(color:--kk-whisper)">{t('play.chat.whisperLabel')}</b>{/if}{words(m)}</p>
							<span class="pb-0.5 text-[11px] text-(color:--kk-muted)">{time(m.at)}</span>
						</div>
					</div>
				{/if}
			{/each}
		</div>
		{#if fresh}
			<!-- Bounces in from below; clicking jumps to the newest line. -->
			<button type="button" onclick={() => toBottom(true)} transition:jump class="kk-btn kk-btn-primary absolute bottom-3 left-1/2 inline-flex min-h-9 -translate-x-1/2 items-center gap-1 rounded-full px-3.5 pl-2.5 text-xs font-extrabold whitespace-nowrap">
				<Icon name="keyboard_arrow_down" size={20} />{t('play.chat.newMessages', { count: fresh > 99 ? '99+' : fresh })}
			</button>
		{/if}
		</div>

		{#if tab !== 'replay'}
		<div class="relative">
		{#if suggest.length}
			<div class="kk-card absolute inset-x-2 bottom-full z-10 mb-1 max-h-72 overflow-y-auto p-1.5">
				<p class="px-2.5 pb-1 text-xs font-extrabold text-(color:--kk-muted)">{t('play.dm.members')}</p>
				<DmMembers {store} rows={suggest} active={sIdx} onpick={suggestPick} id="{uid}-sugg" />
			</div>
		{/if}
		<form onsubmit={send} class="flex items-center gap-2 border-t border-(color:--kk-line) bg-(--kk-panel) p-2.5">
			<input use:watch bind:this={input} bind:value={text} aria-label={t('play.chat.input')} placeholder={store.inGame ? t(mobileInput ? 'kkutu.product.chat.warningMobile' : 'kkutu.product.chat.warning') : t('play.chat.placeholder')} maxlength="200" autocomplete="off" role={suggest.length ? 'combobox' : undefined} aria-expanded={suggest.length ? true : undefined} aria-controls={suggest.length ? `${uid}-sugg` : undefined} aria-activedescendant={suggest.length ? `${uid}-sugg-${sIdx}` : undefined} onkeydown={(e) => suggestKey(e) || store.spaceSubmits(e, text, () => (text = ''))} oninput={(e) => store.myTurn && store.setTyped(text, e.isComposing)} oncompositionend={() => store.myTurn && store.setTyped(text)} class="h-11 min-w-0 flex-1 kk-input px-3" />
			<button class="kk-btn kk-btn-primary grid size-11 shrink-0 place-items-center p-0" aria-label={t('play.chat.send')} disabled={!text.trim()}><Icon name="send" size={20} fill /></button>
		</form>
		</div>
		{/if}
		{/if}
		</div>
	</section>
{:else}
	<!-- Secondary look on purpose: the chat is never the main action. -->
	<button type="button" aria-label={unread ? `${t('play.chat.toggle')} (${t('play.chat.unread', { count: unread })})` : t('play.chat.toggle')} aria-expanded="false" onclick={show} class="kk-btn fixed bottom-4 z-20 grid size-14 place-items-center p-0 {prefs.hand === 'left' ? 'left-4' : 'right-4'}">
		<Icon name="chat" size={26} />
		{#if unread}<span class="absolute -top-1.5 grid {prefs.hand === 'left' ? '-left-1.5' : '-right-1.5'} h-5 min-w-5 place-items-center rounded-full bg-(--kk-danger) px-1 text-[11px] font-extrabold text-white">{unread > 99 ? '99+' : unread}</span>{/if}
	</button>
{/if}
