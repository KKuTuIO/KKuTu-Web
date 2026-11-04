<script>
	import { tick } from 'svelte';
	import { t, tList } from '../i18n.svelte.js';
	import { backOut } from 'svelte/easing';
	import { ms } from '../motion.js';
	import Icon from './Icon.svelte';
	import { confirm } from '../ui/confirm.svelte.js';

	const MAX_STATUS_MESSAGE = 60;

	/**
	 * The status message: one pinned line in a speech balloon over the avatar (the shop preview's balloon). On my own profile an empty one
	 * offers a random opening line with a (+), the same ones the bio does; a set one shows the edit and delete pill when hovered or focused,
	 * like the bio. Editing is in the balloon itself: Enter keeps it, Esc leaves it.
	 * @type {{message:string, isMe:boolean, mask:(s:string)=>string, onsave:(text:string)=>void}}
	 */
	let { message = '', isMe = false, mask = (s) => s, onsave } = $props();
	let editing = $state(false);
	let draft = $state('');
	let input = $state();
	const idea = (() => {
		const all = tList('play.bio.idea');
		return all[Math.floor(Math.random() * all.length)] ?? '';
	})();

	async function edit(start = message) {
		draft = start;
		editing = true;
		await tick();
		input?.focus();
		input?.setSelectionRange(draft.length, draft.length);
	}
	function save() {
		const text = draft.replace(/\s+/g, ' ').trim().slice(0, MAX_STATUS_MESSAGE);
		editing = false;
		if (text !== message) onsave?.(text);
	}
	async function remove() {
		if (await confirm({ message: t('play.statusMessage.confirmRemove') })) onsave?.('');
	}
	function keydown(e) {
		if (e.key === 'Enter') {
			e.preventDefault();
			save();
		} else if (e.key === 'Escape') {
			e.stopPropagation();
			editing = false;
		}
	}
	// the same pop as the shop preview's balloon
	function pop(node, { duration = 240 } = {}) {
		return { duration: ms(duration), css: (t, u) => `transform-origin:50% 100%;transform:translateY(${u * 8}px) scale(${0.4 + 0.6 * backOut(t)});opacity:${Math.min(1, t * 6)}` };
	}
	// One line, cut with an ellipsis, so it never spreads over the avatar; hovering (or focusing) it grows the balloon smoothly to the whole
	// text. The height is animated between two measured pixel values, and the ellipsis is only put back once it has shrunk again.
	let box = $state();
	let clamp = $state(true);
	let height = $state(null); // px while it is moving or open, null when collapsed
	let open = $state(false);
	let collapsedH = 0;
	async function expand() {
		if (open || !box) return;
		collapsedH = box.getBoundingClientRect().height;
		clamp = false;
		await tick();
		const full = box.scrollHeight;
		if (full <= collapsedH + 1) return void (clamp = true); // it fits on the line: nothing to open
		open = true;
		height = collapsedH;
		requestAnimationFrame(() => open && (height = full));
	}
	function collapse() {
		if (!open || !box) return;
		open = false;
		height = box.getBoundingClientRect().height;
		requestAnimationFrame(() => (height = collapsedH));
	}
	function done(e) {
		if (e.target !== box || open) return;
		clamp = true; // the ellipsis returns once it is one line again
		height = null;
	}
	$effect(() => {
		message;
		open = false;
		clamp = true;
		height = null;
	});
	const tail = 'absolute top-full left-1/2 size-2.5 -translate-x-1/2 -translate-y-1.5 rotate-45 border-r border-b border-(color:--kk-line) bg-(--kk-surface)';
	const pillBtn = 'grid size-7 place-items-center rounded-full text-white/90 hover:bg-white/15 focus-visible:bg-white/15';
</script>

{#if editing}
	<div in:pop class="group absolute top-12 left-1/2 z-10 w-[88%] -translate-x-1/2 rounded-xl border border-(color:--kk-accent) bg-(--kk-surface) px-2.5 py-1.5 shadow-[0_2px_0_var(--kk-line)]">
		<div class="flex items-center gap-1">
			<input bind:this={input} bind:value={draft} maxlength={MAX_STATUS_MESSAGE} onkeydown={keydown} onblur={save} aria-label={t('play.statusMessage.title')} placeholder={t('play.statusMessage.placeholder')} class="min-w-0 flex-1 bg-transparent text-center text-sm font-bold outline-none" />
			<span class="shrink-0 text-[10px] font-bold text-(color:--kk-muted) tabular-nums">{draft.length}/{MAX_STATUS_MESSAGE}</span>
		</div>
		<span class={tail}></span>
	</div>
{:else if message}
	<!-- svelte-ignore a11y_no_noninteractive_tabindex, a11y_no_noninteractive_element_interactions, a11y_click_events_have_key_events -->
	<div
		in:pop
		role="group"
		class="group absolute top-12 left-1/2 z-10 w-max max-w-[88%] -translate-x-1/2 rounded-xl border border-(color:--kk-line) bg-(--kk-surface) px-3 py-1.5 text-center text-sm font-bold shadow-[0_2px_0_var(--kk-line)] outline-none"
		tabindex={isMe ? 0 : undefined}
		aria-label={t('play.statusMessage.title')}
		onpointerenter={(e) => e.pointerType !== 'touch' && expand()}
		onpointerleave={(e) => e.pointerType !== 'touch' && collapse()}
		onfocusin={expand}
		onfocusout={collapse}
		onclick={(e) => e.pointerType === 'touch' && (open ? collapse() : expand())}
		onkeydown={(e) => (e.key === 'Enter' || e.key === ' ') && e.target === e.currentTarget && (e.preventDefault(), open ? collapse() : expand())}
	>
		<div bind:this={box} ontransitionend={done} style:height={height != null ? `${height}px` : undefined} class="overflow-hidden break-words transition-[height] duration-200 ease-out motion-reduce:transition-none {clamp ? 'line-clamp-1' : ''}">{mask(message)}</div>
		<span class={tail}></span>
		{#if isMe}
			<div class="absolute -top-3.5 -right-2 flex items-center rounded-full bg-neutral-800/95 p-0.5 text-white opacity-0 shadow-lg transition-opacity duration-150 group-focus-within:opacity-100 group-hover:opacity-100 [@media(hover:none)]:opacity-100">
				<button type="button" class={pillBtn} aria-label={t('play.statusMessage.edit')} title={t('play.statusMessage.edit')} onclick={() => edit()}><Icon name="edit" size={16} /></button>
				<button type="button" class={pillBtn} aria-label={t('play.statusMessage.remove')} title={t('play.statusMessage.remove')} onclick={remove}><Icon name="delete" size={16} /></button>
			</div>
		{/if}
	</div>
{:else if isMe && idea}
	<button type="button" in:pop onclick={() => edit(`${idea} `)} class="group absolute top-12 left-1/2 z-10 flex w-max max-w-[88%] -translate-x-1/2 items-center gap-1.5 rounded-xl border border-(color:--kk-line) bg-(--kk-surface) px-2.5 py-1.5 text-left text-xs font-bold text-(color:--kk-muted) italic shadow-[0_2px_0_var(--kk-line)] hover:text-(color:--kk-text)">
		<Icon name="add_circle" size={18} fill class="shrink-0 opacity-70 group-hover:opacity-100" />{idea}
		<span class={tail}></span>
	</button>
{/if}
