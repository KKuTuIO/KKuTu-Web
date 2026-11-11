<script>
	import { t } from '../i18n.svelte.js';
	import { PALETTE, colorKeys, pad } from '../drawing.svelte.js';

	/** The colours, as a grid of big swatches (for the bottom sheet a phone opens from the toolbar). */
	let { onpick } = $props();
	const colorName = (i) => colorKeys(i).map((k) => t(k)).join(' ');
	function pick(c) {
		pad.color = c;
		if (pad.tool === 'eraser') pad.tool = 'pencil';
		onpick?.(c);
	}
</script>

<div class="grid grid-cols-6 gap-2.5" role="group" aria-label={t('kkutu.js.drawingColor')}>
	{#each PALETTE as c, i (c)}
		<button
			type="button"
			aria-pressed={pad.color === c}
			aria-label={colorName(i)}
			onclick={() => pick(c)}
			style:background-color={c}
			class="aspect-square rounded-xl border-2 shadow-[0_2px_0_var(--kk-line-strong)] {pad.color === c ? 'border-(--kk-accent) outline-2 outline-offset-2 outline-(--kk-accent)' : 'border-(color:--kk-line-strong)'}"
		></button>
	{/each}
</div>
