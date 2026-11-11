<script>
	import { t } from '../i18n.svelte.js';
	import Dialog from './Dialog.svelte';
	import Icon from './Icon.svelte';
	import Tooltip from '../ui/Tooltip.svelte';
	import { confirm, prompt } from '../ui/confirm.svelte.js';
	import { custom, MAX_PRESETS, MAX_NAME, renamePreset, removePreset, movePreset } from '../themePresets.svelte.js';

	/**
	 * The window of the player's own theme sets (legacy "프리셋 설정"): rename, edit themes, delete and re-order (drag the
	 * grip, or use the arrows). `lang` is the language of the mode being set up: sets of the other language are listed
	 * but can only be edited from a mode of their language.
	 */
	let { open = $bindable(false), net, lang, onedit } = $props();
	const nameOf = (code) => net.meta.labels[`word.theme.${code}`] ?? code;
	const none = $derived(t('play.preset.none'));
	// The themes a set switches on, and the ones of its language it leaves off (for the tooltip).
	function details(p) {
		const on = new Set(p.themes);
		const all = net.meta.pickThemes?.[p.lang] ?? [];
		return { active: p.themes.map(nameOf).join(', ') || none, inactive: all.filter((c) => !on.has(c)).map(nameOf).join(', ') || none };
	}

	async function rename(p) {
		const name = await prompt({ message: t('play.preset.askNewName'), value: p.name, max: MAX_NAME });
		if (name?.trim()) renamePreset(p.id, name);
	}
	async function remove(p) {
		if (await confirm({ message: `「${p.name}」 ${t('play.preset.confirmDelete')}` })) removePreset(p.id);
	}

	let dragging = $state(null);
	let over = $state(null);
	function drop(e, target) {
		e.preventDefault();
		const to = custom.list.findIndex((x) => x.id === target.id);
		if (dragging && to >= 0) movePreset(dragging, to);
		dragging = over = null;
	}
</script>

<Dialog bind:open title={t('play.preset.title', { count: custom.list.length, max: MAX_PRESETS })}>
	{#if !custom.list.length}
		<p class="rounded-xl bg-(--kk-surface-2) px-3 py-8 text-center text-sm text-(color:--kk-muted)">{t('play.preset.empty')}</p>
	{:else}
		<ul class="flex max-h-[min(24rem,60dvh)] flex-col gap-2 overflow-y-auto pr-1">
			{#each custom.list as p, i (p.id)}
				{@const d = details(p)}
				{@const mine = p.lang === lang}
				{#snippet tip()}
					<span class="block"><b>{t('play.preset.active')}:</b> {d.active}</span>
					<span class="mt-1 block"><b>{t('play.preset.inactive')}:</b> {d.inactive}</span>
				{/snippet}
				<!-- svelte-ignore a11y_no_noninteractive_element_interactions -->
				<li
					draggable="true"
					ondragstart={(e) => {
						dragging = p.id;
						e.dataTransfer.effectAllowed = 'move';
						e.dataTransfer.setData('text/plain', p.id);
					}}
					ondragend={() => (dragging = over = null)}
					ondragover={(e) => {
						e.preventDefault();
						over = p.id;
					}}
					ondrop={(e) => drop(e, p)}
					class="flex items-center gap-2 rounded-xl border-[1.5px] px-2 py-2 {over === p.id && dragging !== p.id ? 'border-(color:--kk-accent)' : 'border-(color:--kk-line)'} {dragging === p.id ? 'opacity-50' : ''}"
				>
					<span class="grid size-8 shrink-0 cursor-grab place-items-center text-(color:--kk-muted)" aria-hidden="true"><Icon name="drag_indicator" size={20} /></span>
					<Tooltip content={tip} placement="top">
						<div class="min-w-0 flex-1">
							<p class="truncate text-sm font-extrabold">#{i + 1} {p.name}{#if p.lang === 'en'}<span class="kk-chip ml-1.5">EN</span>{/if}</p>
							<p class="truncate text-xs text-(color:--kk-muted)">{t('play.preset.total', { n: p.themes.length })} · {d.active}</p>
						</div>
					</Tooltip>
					<div class="flex shrink-0 items-center">
						<Tooltip text={t('play.preset.moveUp')}><button type="button" aria-label={t('play.preset.moveUp')} disabled={i === 0} onclick={() => movePreset(p.id, i - 1)} class="grid size-8 place-items-center rounded-lg text-(color:--kk-muted) hover:bg-(--kk-surface-2) disabled:opacity-30"><Icon name="arrow_upward" size={18} /></button></Tooltip>
						<Tooltip text={t('play.preset.moveDown')}><button type="button" aria-label={t('play.preset.moveDown')} disabled={i === custom.list.length - 1} onclick={() => movePreset(p.id, i + 1)} class="grid size-8 place-items-center rounded-lg text-(color:--kk-muted) hover:bg-(--kk-surface-2) disabled:opacity-30"><Icon name="arrow_downward" size={18} /></button></Tooltip>
						<Tooltip text={t('play.preset.rename')}><button type="button" aria-label={t('play.preset.rename')} onclick={() => rename(p)} class="grid size-8 place-items-center rounded-lg text-(color:--kk-muted) hover:bg-(--kk-surface-2)"><Icon name="match_case" size={18} /></button></Tooltip>
						<Tooltip text={mine ? t('play.preset.editThemes') : t('play.preset.editOtherLang')}><button type="button" aria-label={t('play.preset.editThemes')} disabled={!mine} onclick={() => onedit?.(p)} class="grid size-8 place-items-center rounded-lg text-(color:--kk-muted) hover:bg-(--kk-surface-2) disabled:opacity-30"><Icon name="edit" size={18} /></button></Tooltip>
						<Tooltip text={t('play.preset.remove')}><button type="button" aria-label={t('play.preset.remove')} onclick={() => remove(p)} class="grid size-8 place-items-center rounded-lg text-(color:--kk-muted) hover:bg-(--kk-surface-2)"><Icon name="close" size={18} /></button></Tooltip>
					</div>
				</li>
			{/each}
		</ul>
	{/if}
</Dialog>
