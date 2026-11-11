<script>
	import { t } from '../i18n.svelte.js';
	import { plainText } from '../protocol.js';
	import { toast } from '../ui/toast.svelte.js';
	import { audio } from '../audio.svelte.js';
	import { prefs } from '../prefs.svelte.js';
	import { custom } from '../themePresets.svelte.js';
	import { wardrobe } from '../wardrobe.svelte.js';
	import { buildKks, buildSettingsFile, SETTING_PARTS } from '../settingsFile.js';
	import Dialog from './Dialog.svelte';
	import SettingsFileList from './SettingsFileList.svelte';

	/** 설정 내보내기: choose what goes into the file (.kkis), and it is downloaded. */
	let { open = $bindable(false) } = $props();
	let selected = $state(Object.fromEntries(SETTING_PARTS.map((p) => [p, true])));
	$effect(() => {
		if (open) selected = Object.fromEntries(SETTING_PARTS.map((p) => [p, true]));
	});
	const stored = (key) => {
		try {
			return localStorage.getItem(key) || '{}';
		} catch {
			return '{}';
		}
	};

	function save() {
		if (!SETTING_PARTS.some((p) => selected[p])) return toast(plainText(t('kkutu.js.settingsFile.noSelection')), { kind: 'error' });
		const bytes = buildSettingsFile(selected, {
			kks: () => buildKks({ prefs, audio }),
			block: () => stored('block'),
			shut: () => stored('shut'),
			notes: () => stored('notes'),
			themePresets: () => $state.snapshot(custom.list),
			wardrobePresets: () => $state.snapshot(wardrobe.list)
		});
		const d = new Date();
		const url = URL.createObjectURL(new Blob([bytes], { type: 'application/octet-stream' }));
		const a = document.createElement('a');
		a.href = url;
		a.download = `KKuTuIO-settings-${d.getFullYear()}-${d.getMonth() + 1}-${d.getDate()}-${d.getHours()}-${d.getMinutes()}-${d.getSeconds()}.kkis`;
		document.body.append(a);
		a.click();
		a.remove();
		setTimeout(() => URL.revokeObjectURL(url), 1000);
		toast(plainText(t('kkutu.dialog.accountOptions.copySettings.info')), { kind: 'success' });
		open = false;
	}
</script>

<Dialog bind:open title={t('kkutu.dialog.accountOptions.export.title')}>
	<div class="flex flex-col gap-4">
		<p class="text-sm leading-relaxed text-(color:--kk-muted)">{plainText(t('kkutu.dialog.accountOptions.export.expl'))}</p>
		<SettingsFileList parts={SETTING_PARTS} bind:selected />
		<button type="button" onclick={save} class="kk-btn kk-btn-primary min-h-12 text-base">{t('kkutu.dialog.accountOptions.export.ok')}</button>
	</div>
</Dialog>
