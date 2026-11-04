<script>
	import { t } from '../i18n.svelte.js';
	import { cdnImg } from '../../cdn.js';
	import { prefs } from '../prefs.svelte.js';
	import { maskBad } from '../badwords.js';
	import { backOut, backIn } from 'svelte/easing';
	import { ms } from '../motion.js';
	import Icon from './Icon.svelte';
	import Moremi from './Moremi.svelte';

	/**
	 * The preview stage: the profile card's banner (`BAN`) fills the whole stage, the avatar wearing what is in the cart stands on it with
	 * its nickname in the name skin (`NIK`, the legacy `x-<id>` class), and a chat of its own to try the look out in a balloon (e.g.
	 * for a screenshot). What is typed here stays here: it is never sent to the server.
	 */
	let { equip, parts, shop, user, canReset = false, canBare = true, onreset, onbare } = $props();
	const skin = $derived(equip.NIK ? `x-${equip.NIK}` : '');
	let bannerFailed = $state('');
	const banner = $derived(equip.BAN && equip.BAN !== bannerFailed ? equip.BAN : 'def_banner');
	let draft = $state('');
	let balloon = $state(null);
	let seq = 0;
	let timer;

	function say(e) {
		e.preventDefault();
		const text = draft.trim();
		if (!text) return;
		draft = '';
		const shown = prefs.filter === 'off' ? text : maskBad(text);
		balloon = { id: ++seq, text: shown };
		clearTimeout(timer);
		timer = setTimeout(() => (balloon = null), 4000);
	}
	$effect(() => () => clearTimeout(timer));

	// The same pop as the speech balloons over the seats.
	function pop(node, { duration = 260 } = {}) {
		return {
			duration: ms(duration),
			css: (t, u) => `transform-origin:50% 100%;transform:translateY(${u * 10}px) scale(${0.35 + 0.65 * backOut(t)});opacity:${Math.min(1, t * 6)}`
		};
	}
	function drop(node, { duration = 120 } = {}) {
		return { duration: ms(duration), css: (t, u) => `transform-origin:50% 100%;transform:translateY(${u * 6}px) scale(${1 - 0.25 * backIn(u)});opacity:${t}` };
	}
</script>

<div class="flex flex-col gap-2">
	<div class="relative flex h-72 flex-col items-center justify-end gap-1 overflow-hidden rounded-2xl border border-(color:--kk-line) bg-(--kk-surface-2) pb-4 shadow-[0_2px_0_var(--kk-line)]">
		<img src={cdnImg(`kkutu/moremi/banner/${banner}_p.png`)} alt="" draggable="false" onerror={() => (bannerFailed = equip.BAN)} class="absolute inset-0 size-full object-cover" />
		{#each balloon ? [balloon] : [] as b (b.id)}
			<p in:pop out:drop class="pointer-events-none absolute top-3 left-1/2 z-10 w-max max-w-[80%] -translate-x-1/2 rounded-xl border border-(color:--kk-line) bg-(--kk-surface) px-3 py-1.5 text-center text-sm font-bold break-words shadow-[0_2px_0_var(--kk-line)]">
				{b.text}
				<span class="absolute top-full left-1/2 size-2.5 -translate-x-1/2 -translate-y-1.5 rotate-45 border-r border-b border-(color:--kk-line) bg-(--kk-surface)"></span>
			</p>
		{/each}
		<Moremi {equip} {shop} {parts} size={170} frame={false} label={t('kkutu.dialog.purchase.moremiAfter')} class="relative" />
		<p class="relative rounded-full bg-(--kk-surface)/85 px-3 py-0.5 text-lg leading-tight font-extrabold backdrop-blur-sm"><span class={skin}>{user?.name}</span>{#if user?.tag}<small class="font-normal text-(color:--kk-muted)">#{user.tag}</small>{/if}</p>
	</div>
	<div class="flex gap-2">
		<button type="button" class="kk-btn inline-flex h-10 min-h-0 items-center gap-1.5 px-3 text-sm" disabled={!canReset} onclick={onreset}><Icon name="restart_alt" size={20} />{t('play.shop.reset')}</button>
		<button type="button" class="kk-btn inline-flex h-10 min-h-0 items-center gap-1.5 px-3 text-sm" disabled={!canBare} onclick={onbare}><Icon name="checkroom" size={20} />{t('play.shop.bare')}</button>
	</div>
	<form onsubmit={say} class="flex items-center gap-2">
		<label class="kk-input flex h-10 min-w-0 flex-1 items-center px-3"><input type="text" bind:value={draft} maxlength={100} placeholder={t('play.chat.placeholder')} aria-label={t('play.chat.input')} class="min-w-0 flex-1 bg-transparent outline-none" /></label>
		<button type="submit" aria-label={t('play.chat.send')} disabled={!draft.trim()} class="kk-btn kk-btn-primary grid size-10 min-h-0 shrink-0 place-items-center p-0"><Icon name="send" size={20} fill /></button>
	</form>
</div>
