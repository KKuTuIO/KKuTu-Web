<script>
	import { t } from '../i18n.svelte.js';
	import Tooltip from '../ui/Tooltip.svelte';
	import { PALETTE, SIZES, TOOLS, colorKeys, pad } from '../drawing.svelte.js';
	import Icon from './Icon.svelte';

	/** The painter's side panels of the sketchbook: `side="palette"` (left) and `side="tools"` (right). */
	let { side, onclear, onpalette } = $props();
	const icons = { pencil: 'edit', spray: 'brush', circle: 'bubble_chart', eraser: 'ink_eraser' };
	const eraser = $derived(pad.tool === 'eraser');
	const colorName = (i) => colorKeys(i).map((k) => t(k)).join(' ');
	const currentName = $derived(colorName(Math.max(0, PALETTE.indexOf(pad.color))));
</script>

{#if side === 'phone'}
	<!-- a phone: one slim toolbar under the sheet (the tools, the colour that opens the palette, the clear button) and the brush sizes -->
	<div class="kkp-tools" data-tools>
		<div class="kkp-toolrow" role="group" aria-label={t('play.drawing.tools')}>
			{#each TOOLS as tool (tool)}
				<button type="button" class="kkp-tool {pad.tool === tool ? 'on' : ''}" aria-pressed={pad.tool === tool} aria-label={t(`play.drawing.tool.${tool}`)} onclick={() => (pad.tool = tool)}><Icon name={icons[tool]} size={24} /></button>
			{/each}
			<button type="button" class="kkp-tool kkp-color" aria-label={`${t('kkutu.js.drawingColor')}: ${currentName}`} aria-haspopup="dialog" onclick={onpalette}><span style:background-color={pad.color}></span></button>
			<button type="button" class="kkp-tool kkp-clear" aria-label={t('kkutu.js.drawingClear')} onclick={onclear}><Icon name="delete" size={24} /></button>
		</div>
		<div class="kkp-sizerow" role="group" aria-label={t('play.drawing.size')}>
			{#each SIZES as n (n)}
				<button type="button" class="kkp-size {pad.width === n ? 'on' : ''}" aria-pressed={pad.width === n} aria-label={`${t('play.drawing.size')} ${n}`} onclick={() => (pad.width = n)}><i style:width="{Math.max(4, n * 0.6)}px" style:height="{Math.max(4, n * 0.6)}px" class:er={eraser}></i></button>
			{/each}
		</div>
	</div>
{:else if side === 'palette'}
	<div class="sk-panel sk-palette" role="group" aria-label={t('kkutu.js.drawingColor')}>
		{#each PALETTE as c, i (c)}
			<Tooltip text={colorName(i)}><button type="button" class="sk-swatch {pad.color === c ? 'on' : ''}" style:background-color={c} aria-pressed={pad.color === c} aria-label={colorName(i)} onclick={() => ((pad.color = c), pad.tool === 'eraser' && (pad.tool = 'pencil'))}></button></Tooltip>
		{/each}
		<Tooltip text={currentName}><div class="sk-current" style:background-color={pad.color} role="img" aria-label={currentName}></div></Tooltip>
	</div>
{:else}
	<div class="sk-panel sk-tools" data-tools>
		<div class="sk-toolgrid" role="group" aria-label={t('play.drawing.tools')}>
			{#each TOOLS as tool (tool)}
				<Tooltip text={t(`play.drawing.tool.${tool}`)}><button type="button" class="sk-btn {pad.tool === tool ? 'on' : ''}" aria-pressed={pad.tool === tool} aria-label={t(`play.drawing.tool.${tool}`)} onclick={() => (pad.tool = tool)}><Icon name={icons[tool]} size={24} /></button></Tooltip>
			{/each}
			<Tooltip text={t('kkutu.js.drawingClear')}><button type="button" class="sk-btn sk-clear" aria-label={t('kkutu.js.drawingClear')} onclick={onclear}><Icon name="delete" size={24} /></button></Tooltip>
		</div>
		<div class="sk-sizes" role="group" aria-label={t('play.drawing.size')}>
			{#each SIZES as n (n)}
				<Tooltip text={`${t('play.drawing.size')} ${n}`}><button type="button" class="sk-size {pad.width === n ? 'on' : ''}" aria-pressed={pad.width === n} aria-label={`${t('play.drawing.size')} ${n}`} onclick={() => (pad.width = n)}><i class={eraser ? 'er' : ''} style="width:{Math.max(3, n * 0.55)}px;height:{Math.max(3, n * 0.55)}px;{eraser ? '' : `background:${pad.color}`}"></i></button></Tooltip>
			{/each}
		</div>
		<input class="sk-range" type="range" min="1" max="40" bind:value={pad.width} aria-label={t('play.drawing.size')} style="--p:{((pad.width - 1) / 39) * 100}%" />
	</div>
{/if}
