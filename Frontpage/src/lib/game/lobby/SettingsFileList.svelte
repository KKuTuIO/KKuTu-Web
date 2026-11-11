<script>
	import { t } from '../i18n.svelte.js';
	import Checkbox from '../ui/Checkbox.svelte';
	import Icon from './Icon.svelte';

	/**
	 * The parts of a settings file, one tick box each, with 모두 선택 / 모두 해제 (the legacy lists).
	 * @type {{parts:string[], selected:Record<string,boolean>}}
	 */
	let { parts, selected = $bindable({}) } = $props();
	const all = $derived(parts.length > 0 && parts.every((p) => selected[p]));
	const some = $derived(parts.some((p) => selected[p]));
	const toggleAll = () => (selected = Object.fromEntries(parts.map((p) => [p, !all])));
</script>

<div class="flex flex-col gap-1">
	<button type="button" aria-pressed={all} onclick={toggleAll} class="kk-btn inline-flex min-h-10 items-center gap-1.5 self-start px-3 text-sm"><Icon name={all ? 'check_box' : some ? 'indeterminate_check_box' : 'check_box_outline_blank'} size={20} fill={some} />{t(all ? 'kkutu.dialog.accountOptions.export.deselectAll' : 'kkutu.dialog.accountOptions.export.selectAll')}</button>
	<div class="flex flex-col rounded-xl border-[1.5px] border-(color:--kk-line) px-3 py-1">
		{#each parts as p (p)}
			<Checkbox checked={!!selected[p]} onchange={(v) => (selected = { ...selected, [p]: v })}>{t(`kkutu.dialog.accountOptions.export.${p === 'kks' ? 'kks' : p}`)}</Checkbox>
		{/each}
	</div>
</div>
