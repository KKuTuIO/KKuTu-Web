<script>
	import { tick } from 'svelte';
	import { t } from '../i18n.svelte.js';
	import { isHidden } from '../chatFilter.js';
	import Icon from './Icon.svelte';

	/**
	 * A phone's chat line at the very bottom of the board, always there. As in the legacy client, on my turn what is typed here is my
	 * answer (store.sendChat hands it to submitWord), so it is guarded and traced like the chat box of the chat panel. The last line of
	 * the chat is shown above it; pressing that, or the chat button, opens the whole chat.
	 */
	let { store } = $props();
	let text = $state('');
	let input = $state();
	const mine = $derived(store.myTurn);
	const last = $derived(store.net.chat.filter((m) => !isHidden(m, store.net)).at(-1));
	const unread = $derived(store.dmUnread);
	const watch = (node) => store.watchField(node, 'chat');
	const openChat = () => store.ui && store.ui.chatNonce++;

	function send(e) {
		e.preventDefault();
		store.sendChat(text);
		text = '';
		input?.focus(); // keeps the keyboard up between lines
	}
	// my turn: the line starts empty and the keyboard is ready
	$effect(() => {
		if (!mine) return;
		text = '';
		tick().then(() => input?.focus());
	});
</script>

{#if last}
	<button type="button" onclick={openChat} class="kkp-lastchat" aria-label={t('play.chat.toggle')}><b>{last.who ?? ''}</b> {last.msg}</button>
{/if}
<form onsubmit={send} class="kkp-chatbar {mine ? 'mine' : ''}">
	<button type="button" onclick={openChat} aria-label={t('play.chat.toggle')} class="kkp-chatbtn"><Icon name="chat" size={22} />{#if unread}<i class="dot" aria-hidden="true"></i>{/if}</button>
	<input
		use:watch
		bind:this={input}
		bind:value={text}
		aria-label={t('play.chat.input')}
		placeholder={mine ? t('kkutu.product.game.inputHere') : t('play.chat.placeholder')}
		maxlength="200"
		autocomplete="off"
		autocorrect="off"
		autocapitalize="off"
		spellcheck="false"
		enterkeyhint="send"
		onkeydown={(e) => store.spaceSubmits(e, text, () => (text = ''))}
		oninput={(e) => store.myTurn && store.setTyped(text, e.isComposing)}
		oncompositionend={() => store.myTurn && store.setTyped(text)}
	/>
	<button type="submit" aria-label={t('play.chat.send')} disabled={!text.trim()} class="kkp-send"><Icon name="send" size={20} fill /></button>
</form>
