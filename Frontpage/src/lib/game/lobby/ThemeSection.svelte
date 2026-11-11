<script>
	import { t } from '../i18n.svelte.js';
	import Icon from './Icon.svelte';
	import Select from '../ui/Select.svelte';
	import ThemePicker from '../ui/ThemePicker.svelte';
	import Tooltip from '../ui/Tooltip.svelte';
	import PresetManager from './PresetManager.svelte';
	import { toast } from '../ui/toast.svelte.js';
	import { prompt } from '../ui/confirm.svelte.js';
	import { custom, addPreset, setPresetThemes, MAX_PRESETS, MAX_NAME } from '../themePresets.svelte.js';

	/**
	 * The 단어장 part of the room form (legacy "인정픽" dialog): choose the word themes a room plays, by hand, from an
	 * official set (sent by the server) or from the player's own sets. Own sets can be saved, edited in place, renamed,
	 * deleted and re-ordered in the manager window.
	 * `injpick` is the chosen theme codes; `lang` ("ko" | "en") decides which themes and sets are offered.
	 */
	let { net, lang, injpick = $bindable([]), title } = $props();
	const pickable = $derived(net.meta.pickThemes?.[lang] ?? []);
	const nameOf = (code) => net.meta.labels[`word.theme.${code}`] ?? code;
	const themeNames = $derived(Object.fromEntries(pickable.map((c) => [c, nameOf(c)])));
	const official = $derived((net.themePresets ?? []).filter((p) => p.lang === lang));
	const own = $derived(custom.list.filter((p) => p.lang === lang));
	const allPicked = $derived(pickable.length > 0 && injpick.length === pickable.length);

	let selected = $state(''); // '' | 'official:<id>' | 'custom:<id>'
	let editingId = $state(null); // an own set being edited: every change of the picks is saved into it
	let manage = $state(false);
	const find = (value) => {
		const [kind, ...rest] = String(value).split(':');
		return (kind === 'official' ? official : own).find((p) => p.id === rest.join(':'));
	};
	const editing = $derived(own.find((p) => p.id === editingId) ?? null);
	const canEdit = $derived(selected.startsWith('custom:') && !!find(selected));

	// Group headings are disabled entries: the dropdown has no groups of its own.
	const options = $derived([
		{ value: '', label: t('play.options.themePresetPick') },
		...(official.length ? [{ value: '#official', label: t('play.preset.official'), disabled: true }, ...official.map((p) => ({ value: `official:${p.id}`, label: p.name }))] : []),
		...(own.length ? [{ value: '#custom', label: t('play.preset.custom'), disabled: true }, ...own.map((p) => ({ value: `custom:${p.id}`, label: p.name }))] : [])
	]);

	// A set that disappeared (deleted in the manager, other language) can't stay selected or being edited.
	$effect(() => {
		if (selected && !find(selected)) selected = '';
		if (editingId && !editing) editingId = null;
	});

	function pick(value) {
		selected = value;
		const p = find(value);
		if (p) injpick = p.themes.filter((c) => pickable.includes(c));
	}
	// Changing the picks by hand: a set that is being edited follows along, otherwise the picks no longer match the set.
	function changed(next) {
		injpick = next;
		if (editingId) setPresetThemes(editingId, next);
		else selected = '';
	}
	function toggleEdit() {
		if (editingId) return (editingId = null);
		const p = find(selected);
		if (p) editingId = p.id;
	}
	async function save() {
		if (custom.list.length >= MAX_PRESETS) return toast(t('play.preset.full', { max: MAX_PRESETS }), { kind: 'error' });
		const name = await prompt({ message: t('play.preset.askName'), max: MAX_NAME });
		if (!name?.trim()) return;
		const p = addPreset(name, lang, injpick);
		if (p) selected = `custom:${p.id}`;
	}
	// From the manager: edit this set here.
	function edit(p) {
		manage = false;
		selected = `custom:${p.id}`;
		injpick = p.themes.filter((c) => pickable.includes(c));
		editingId = p.id;
	}
</script>

<div class="mt-3 flex flex-col gap-2">
	<span class="text-xs font-bold text-(color:--kk-muted)">{title}</span>
	<div class="flex items-center gap-2">
		<div class="min-w-0 flex-1"><Select label={t('play.options.themePreset')} value={selected} {options} onchange={pick} /></div>
		<Tooltip text={editing ? t('play.preset.editDone') : t('play.preset.edit')} placement="top">
			<button type="button" disabled={!editing && !canEdit} onclick={toggleEdit} class="kk-btn inline-flex min-h-11 shrink-0 items-center gap-1 px-3 text-sm {editing ? 'kk-btn-primary' : ''}"><Icon name={editing ? 'check' : 'edit'} size={18} />{editing ? t('play.preset.editDone') : t('play.preset.edit')}</button>
		</Tooltip>
	</div>
	{#if editing}<p class="text-xs font-bold text-(color:--kk-accent-strong)">{t('play.preset.editing', { name: editing.name })}</p>{/if}
	<div class="flex items-center gap-2">
		<div class="min-w-0 flex-1"><ThemePicker multiple themes={themeNames} value={injpick} onchange={changed} label={title} placeholder={t('play.options.themesNone')} /></div>
		<button type="button" class="kk-btn min-h-11 shrink-0 px-3 text-sm" onclick={() => changed(allPicked ? [] : [...pickable])}>{allPicked ? t('play.options.themesClear') : t('play.options.themesAll')}</button>
	</div>
	<div class="flex items-center gap-2">
		<button type="button" disabled={!injpick.length || !!editingId} onclick={save} class="kk-btn inline-flex min-h-10 items-center gap-1 px-3 text-sm"><Icon name="add" size={18} />{t('play.preset.save')}</button>
		<button type="button" onclick={() => (manage = true)} class="kk-btn inline-flex min-h-10 items-center gap-1 px-3 text-sm"><Icon name="tune" size={18} />{t('play.preset.manage')}<span class="text-xs tabular-nums text-(color:--kk-muted)">{custom.list.length}/{MAX_PRESETS}</span></button>
	</div>
</div>

<PresetManager bind:open={manage} {net} {lang} onedit={edit} />
