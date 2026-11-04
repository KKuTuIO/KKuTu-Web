<script>
	import { t, tList } from '../i18n.svelte.js';
	import { tick } from 'svelte';
	import { MAX_BIO, saveBio, applyFormat } from '../bio.js';
	import { confirm } from '../ui/confirm.svelte.js';
	import { toast } from '../ui/toast.svelte.js';
	import Icon from './Icon.svelte';
	import Markdown from './Markdown.svelte';

	/**
	 * 소개: the bio as a speech-bubble card on the 프로필 tab, drawn as Markdown. On my own, hovering (or focusing) it shows a small
	 * pill with edit and delete; editing is in place with a live preview. A link asks first before leaving the game.
	 * @type {{bio:string, isMe:boolean, mask:(s:string)=>string, onsaved?:(text:string)=>void}}
	 */
	let { bio = '', isMe = false, mask = (s) => s, onsaved } = $props();
	let editing = $state(false);
	let draft = $state('');
	let saving = $state(false);
	let area = $state(); // the textarea
	// an empty bio of mine offers a random opening line to continue: pressing it opens the editor with that line already in
	const idea = (() => {
		const all = tList('play.bio.idea');
		return all[Math.floor(Math.random() * all.length)] ?? '';
	})();
	async function startWith(line) {
		draft = `${line} `;
		editing = true;
		await tick();
		area?.focus();
		area?.setSelectionRange(draft.length, draft.length);
	}
	const TOOLS = [
		['bold', 'format_bold', 'B'],
		['italic', 'format_italic', 'I'],
		['strike', 'strikethrough_s', ''],
		['code', 'code', ''],
		['link', 'link', 'K'],
		['list', 'format_list_bulleted', ''],
		['quote', 'format_quote', '']
	];
	/** A formatting button (or its shortcut): the textarea keeps the focus and the selection moves with the text. */
	async function format(kind) {
		if (!area) return;
		const r = applyFormat(draft, area.selectionStart, area.selectionEnd, kind);
		if (r.text.length > MAX_BIO) return;
		draft = r.text;
		await tick();
		area.focus();
		area.setSelectionRange(r.start, r.end);
	}

	function edit() {
		draft = bio;
		editing = true;
	}
	async function persist(text) {
		saving = true;
		const r = await saveBio(text);
		saving = false;
		if ('error' in r) return void toast(r.error === 'request' ? t('play.error.request', { code: 'bio' }) : r.error, { kind: 'error' });
		onsaved?.(r.text);
		editing = false;
		toast(t(r.text ? 'play.bio.saved' : 'play.bio.removed'), { kind: 'success' });
	}
	const save = () => !saving && persist(draft.trim());
	async function remove() {
		if (await confirm({ message: t('play.bio.confirmRemove') })) persist('');
	}
	async function openLink(href) {
		let same = false;
		try {
			same = new URL(href).origin === location.origin;
		} catch {
			/* not an address */
		}
		if (!same && !(await confirm({ message: `${t('play.link.confirm')}\n${href}`, title: t('play.confirm.ok'), ok: t('play.link.yes'), cancel: t('play.link.no') }))) return;
		window.open(href, '_blank', 'noopener,noreferrer');
	}
	function keydown(e) {
		if (e.key === 'Escape') {
			e.stopPropagation();
			editing = false;
		} else if (e.key === 'Enter' && (e.metaKey || e.ctrlKey)) {
			e.preventDefault();
			save();
		} else if ((e.metaKey || e.ctrlKey) && !e.shiftKey && !e.altKey) {
			const kind = { b: 'bold', i: 'italic', k: 'link' }[e.key.toLowerCase()];
			if (kind) {
				e.preventDefault();
				format(kind);
			}
		}
	}
	const pillBtn = 'grid size-8 place-items-center rounded-full text-white/90 hover:bg-white/15 focus-visible:bg-white/15';
</script>

{#if editing}
	<section class="flex flex-col gap-2 rounded-2xl bg-(--kk-surface-2) p-3" aria-label={t('play.bio.title')}>
		<div class="flex items-center gap-0.5" role="toolbar" aria-label={t('play.bio.format')}>
			{#each TOOLS as [kind, icon, key] (kind)}
				<button type="button" onmousedown={(e) => e.preventDefault()} onclick={() => format(kind)} aria-label={t(`play.bio.fmt.${kind}`)} title="{t(`play.bio.fmt.${kind}`)}{key ? ` (Ctrl+${key})` : ''}" class="grid size-8 place-items-center rounded-lg text-(color:--kk-muted) hover:bg-(--kk-surface) hover:text-(color:--kk-text)"><Icon name={icon} size={19} /></button>
			{/each}
		</div>
		<!-- svelte-ignore a11y_autofocus -->
		<textarea bind:this={area} bind:value={draft} autofocus rows="5" maxlength={MAX_BIO} placeholder={t('play.bio.placeholder')} onkeydown={keydown} aria-label={t('play.bio.title')} class="kk-input min-h-28 resize-y p-3 text-sm leading-relaxed"></textarea>
		<div class="flex justify-end text-xs text-(color:--kk-muted)">
			<span class="font-bold tabular-nums {draft.length >= MAX_BIO ? 'text-red-500' : ''}">{draft.length}/{MAX_BIO}</span>
		</div>
		{#if draft.trim()}
			<div class="rounded-xl bg-(--kk-surface) px-3 py-2 text-sm">
				<p class="mb-1 text-[11px] font-extrabold text-(color:--kk-muted)">{t('play.bio.preview')}</p>
				<Markdown source={draft} {mask} />
			</div>
		{/if}
		<div class="flex justify-end gap-2">
			<button type="button" class="kk-btn min-h-10 px-4 text-sm" onclick={() => (editing = false)}>{t('play.confirm.cancel')}</button>
			<button type="button" class="kk-btn kk-btn-primary min-h-10 px-5 text-sm" disabled={saving} onclick={save}>{t('play.bio.save')}</button>
		</div>
	</section>
{:else}
	<!-- svelte-ignore a11y_no_noninteractive_tabindex -->
	<section class="group relative rounded-2xl bg-(--kk-surface-2) px-4 py-3 text-sm leading-relaxed outline-none" tabindex={isMe ? 0 : undefined} aria-label={t('play.bio.title')}>
		{#if bio}
			<Markdown source={bio} {mask} onlink={openLink} />
		{:else if isMe && idea}
			<button type="button" onclick={() => startWith(idea)} class="group/idea flex w-full items-center gap-2.5 text-left italic text-(color:--kk-muted) hover:text-(color:--kk-text)"><Icon name="add_circle" size={24} fill class="shrink-0 opacity-70 group-hover/idea:opacity-100" />{idea}</button>
		{:else}
			<p class="text-(color:--kk-muted)">{t(isMe ? 'play.bio.emptyMine' : 'play.profile.noExordial')}</p>
		{/if}
		{#if isMe && bio}
			<div class="absolute -top-4 right-4 flex items-center gap-0.5 rounded-full bg-neutral-800/95 p-1 text-white opacity-0 shadow-lg transition-opacity duration-150 group-focus-within:opacity-100 group-hover:opacity-100 [@media(hover:none)]:opacity-100">
				<button type="button" class={pillBtn} aria-label={t('play.bio.edit')} title={t('play.bio.edit')} onclick={edit}><Icon name="edit" size={18} /></button>
				{#if bio}<button type="button" class={pillBtn} aria-label={t('play.bio.remove')} title={t('play.bio.remove')} onclick={remove}><Icon name="delete" size={18} /></button>{/if}
			</div>
		{/if}
	</section>
{/if}
