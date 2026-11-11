<script>
	import { t } from '../i18n.svelte.js';
	import { plainText } from '../protocol.js';
	import { toast } from '../ui/toast.svelte.js';
	import { audio, setVolume } from '../audio.svelte.js';
	import { DEFAULTS, CHOICES, prefs, setPref } from '../prefs.svelte.js';
	import { replaceBlocks } from '../blocks.svelte.js';
	import { replaceNotes } from '../notes.svelte.js';
	import { replacePresets } from '../themePresets.svelte.js';
	import { replaceOutfits } from '../wardrobe.svelte.js';
	import { applySettingsFile, partsIn, readSettingsFile } from '../settingsFile.js';
	import Dialog from './Dialog.svelte';
	import Icon from './Icon.svelte';
	import SettingsFileList from './SettingsFileList.svelte';

	/**
	 * 설정 가져오기: a settings file (.kkis) of this or the legacy client, and which parts of it to take; or, for members, the 8-character
	 * 불러오기 code of a guest session's progress (이어하기).
	 */
	let { store, open = $bindable(false) } = $props();
	let data = $state(null);
	let name = $state('');
	let selected = $state({});
	let code = $state('');
	let error = $state('');
	let dragging = $state(false);
	const parts = $derived(data ? partsIn(data) : []);
	$effect(() => {
		if (open) {
			data = null;
			name = '';
			selected = {};
			code = '';
			error = '';
		}
	});

	async function read(file) {
		error = '';
		data = null;
		if (!file) return;
		if (!/\.kkis$/i.test(file.name)) return (error = plainText(t('kkutu.js.settingsFile.invalid')));
		try {
			const d = readSettingsFile(await file.arrayBuffer());
			if (!partsIn(d).length) throw new Error('empty');
			data = d;
			name = file.name;
			selected = Object.fromEntries(partsIn(d).map((p) => [p, true]));
		} catch {
			error = plainText(t('kkutu.js.settingsFile.invalid'));
		}
	}
	function drop(e) {
		e.preventDefault();
		dragging = false;
		read(e.dataTransfer?.files?.[0]);
	}

	/** The pref values of a file, through the prefs' own rules (a bad value is not taken). */
	const clean = (name, v) => (typeof DEFAULTS[name] === 'boolean' ? (typeof v === 'boolean' ? v : undefined) : CHOICES[name]?.includes(v) ? v : undefined);

	function apply() {
		if (!data) return;
		if (!parts.some((p) => selected[p])) return (error = plainText(t('kkutu.js.settingsFile.noSelection')));
		const done = applySettingsFile(
			data,
			selected,
			{
				kks: ({ prefs: p, audio: a }) => {
					for (const [k, v] of Object.entries(p)) {
						if (k === 'hideRecords') {
							if (!store.net.guest && v !== prefs.hideRecords) store.setRecordPrivacy(v); // (the server keeps this one)
						}
						setPref(k, v);
					}
					for (const [k, v] of Object.entries(a)) setVolume(k, v);
				},
				block: (o) => replaceBlocks('mute', o),
				shut: (o) => replaceBlocks('shut', o),
				notes: replaceNotes,
				themePresets: replacePresets,
				wardrobePresets: replaceOutfits
			},
			{ clean }
		);
		if (!done.length) return (error = plainText(t('kkutu.js.settingsFile.invalid')));
		toast(t('play.settings.file.imported'), { kind: 'success' });
		open = false;
	}

	function importCode(e) {
		e.preventDefault();
		const c = code.trim();
		if (!c) return;
		if (store.net.guest) return toast(plainText(store.net.meta.labels['kkutu.js.error.711'] ?? ''), { kind: 'error' });
		if (!/^[A-Za-z0-9]{8}$/.test(c)) return toast(plainText(store.net.meta.labels['kkutu.js.error.712'] ?? ''), { kind: 'error' });
		store.importProgress(c);
	}
</script>

<Dialog bind:open title={t('kkutu.dialog.setting.load')}>
	<div class="flex flex-col gap-5">
		<form onsubmit={importCode} class="flex flex-col gap-2">
			<h3 class="text-sm font-extrabold">{t('kkutu.dialog.accountOptions.import.code.title')}</h3>
			<div class="flex gap-2">
				<input bind:value={code} maxlength="8" autocomplete="off" aria-label={t('kkutu.dialog.accountOptions.import.code.title')} placeholder={t('kkutu.dialog.accountOptions.import.code.placeholder')} class="kk-input h-11 min-w-0 flex-1 px-3 font-mono tracking-widest" />
				<button class="kk-btn kk-btn-primary min-h-11 shrink-0 px-5" disabled={!code.trim()}>{t('kkutu.dialog.accountOptions.import.code.ok')}</button>
			</div>
		</form>

		<div class="flex flex-col gap-2">
			<h3 class="text-sm font-extrabold">{t('kkutu.dialog.accountOptions.import.file.title')}</h3>
			<label
				ondragover={(e) => (e.preventDefault(), (dragging = true))}
				ondragleave={() => (dragging = false)}
				ondrop={drop}
				class="flex cursor-pointer flex-col items-center gap-1.5 rounded-xl border-2 border-dashed px-4 py-6 text-center transition-colors {dragging ? 'border-(--kk-accent) bg-(--kk-accent-soft)' : 'border-(color:--kk-line-strong) hover:bg-(--kk-surface-2)'}"
			>
				<Icon name="upload_file" size={32} class="text-(color:--kk-muted)" />
				<b class="text-sm">{name || t('kkutu.dialog.accountOptions.import.file.button')}</b>
				{#if !name}<span class="text-xs text-(color:--kk-muted)">.kkis · {t('kkutu.dialog.accountOptions.import.file.none')}</span>{/if}
				<input type="file" accept=".kkis" class="sr-only" onchange={(e) => (read(e.currentTarget.files?.[0]), (e.currentTarget.value = ''))} />
			</label>
			{#if error}<p role="alert" class="rounded-xl bg-(--kk-danger)/10 px-3 py-2 text-sm font-bold text-(color:--kk-danger)">{error}</p>{/if}
			{#if data}
				<SettingsFileList {parts} bind:selected />
				<button type="button" onclick={apply} class="kk-btn kk-btn-primary min-h-12 text-base">{t('kkutu.dialog.accountOptions.import.file.ok')}</button>
			{/if}
		</div>
	</div>
</Dialog>
