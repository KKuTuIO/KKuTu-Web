<script>
	import { t } from '../i18n.svelte.js';
	import { ITEMS, DOCK_KEYS, PER_ROUND, itemReady, itemLeft } from '../items.js';
	import Tooltip from '../ui/Tooltip.svelte';
	import Icon from './Icon.svelte';

	/**
	 * 아이템전: the six items, always in view. Each is a small pill (icon, name, uses left, F-key); it is greyed out when the server would
	 * refuse it. The grip at its start moves the whole bar: drag it onto one of the places (or press an arrow key on it) and it snaps there.
	 * layout: row (top / bottom edge), grid (under the answer box, 3x2), rail (left / right edge: icons only, the names are in the tips).
	 * @type {{store:any, layout:'row'|'grid'|'rail', onuse:(id:number)=>void, ongrip:(e:PointerEvent)=>void, onmove:(dock:string)=>void, dragging?:boolean}}
	 */
	let { store, layout = 'row', onuse, ongrip, onmove, dragging = false } = $props();
	const g = $derived(store.game);
	const mine = $derived(store.myTurn);
	const rail = $derived(layout === 'rail');

	function gripKey(e) {
		const dock = DOCK_KEYS[e.key];
		if (!dock) return;
		e.preventDefault();
		onmove(dock);
	}
</script>

<div class="kk-items {layout} {dragging ? 'is-dragging' : ''}" role="toolbar" aria-label={t('kkutu.js.item')} data-mine={mine}>
	<Tooltip text={t('play.item.move')} placement={rail ? 'right' : 'top'}>
		<button type="button" class="grip" aria-label={t('play.item.move')} onpointerdown={ongrip} onkeydown={gripKey}><Icon name="drag_indicator" size={18} /></button>
	</Tooltip>
	{#each ITEMS as it (it.id)}
		{@const left = itemLeft(g, it.id)}
		{@const ready = itemReady(g, it.id, mine)}
		{#snippet tip()}
			<b class="block">{t(`game.item.${it.id}.name`)}</b>
			<span class="block max-w-56 text-[11px] leading-snug opacity-90">{t(`game.item.${it.id}.expl`)}</span>
		{/snippet}
		<Tooltip content={tip} placement={layout === 'row' ? 'top' : rail ? 'right' : 'bottom'}>
			<button type="button" style:--c={it.color} class="pill {ready ? '' : 'off'} {left <= 0 ? 'none' : ''}" aria-disabled={!ready} aria-label="{t(`game.item.${it.id}.name`)} x{left}" aria-keyshortcuts="Shift+{it.id + 1}" onclick={() => ready && onuse(it.id)}>
				<span class="orb"><Icon name={it.icon} size={rail ? 20 : 18} weight={600} /></span>
				{#if rail}
					<span class="n">x{left}</span>
				{:else}
					<span class="txt"><span class="nm">{t(`game.item.${it.id}.name`)}</span><b class="cnt">x{left}</b></span>
				{/if}
				<span class="key">{rail ? '⇧' : 'Shift+'}{it.id + 1}</span>
			</button>
		</Tooltip>
	{/each}
	<Tooltip text={t('kkutu.js.itemLeft.expl', { rem: g?.itemsLeft ?? 0 })} placement={rail ? 'right' : 'top'}>
		<span class="sum">
			<span class="pips" aria-hidden="true">{#each Array(PER_ROUND) as _, i (i)}<i class={i < (g?.itemsLeft ?? 0) ? 'on' : ''}></i>{/each}</span>
			<span>{t('play.item.left', { n: g?.itemsLeft ?? 0 })}</span>
		</span>
	</Tooltip>
</div>

<style>
	.kk-items {
		display: flex;
		align-items: center;
		gap: 6px;
		pointer-events: auto;
		transition: opacity 0.15s;
	}
	/* the row at the top / bottom adapts to the room the stage card gives it: first the key hints go, then the names, then it wraps */
	.kk-items:not(.grid):not(.rail) {
		flex-wrap: wrap;
		justify-content: center;
		max-width: 100%;
	}
	@container (max-width: 1000px) {
		.kk-items:not(.grid):not(.rail) .key {
			display: none;
		}
	}
	@container (max-width: 700px) {
		.kk-items:not(.grid):not(.rail) .nm {
			display: none;
		}
		.kk-items:not(.grid):not(.rail) .pill {
			padding-right: 10px;
		}
	}
	@container (max-width: 520px) {
		.kk-items:not(.grid):not(.rail) .sum > span:last-child {
			display: none;
		}
	}
	.kk-items.grid {
		display: grid;
		grid-template-columns: auto repeat(3, 1fr);
		gap: 6px;
		width: 100%;
	}
	.kk-items.grid .grip {
		grid-row: span 2;
	}
	.kk-items.grid .sum {
		grid-column: 2 / -1;
		justify-self: end;
	}
	.kk-items.rail {
		flex-direction: column;
		width: 60px;
	}
	.kk-items.is-dragging {
		opacity: 0.5;
	}
	.grip {
		display: grid;
		place-items: center;
		align-self: stretch;
		min-height: 36px;
		width: 22px;
		border-radius: 10px;
		color: var(--kk-muted);
		background: color-mix(in srgb, var(--kk-surface) 85%, transparent);
		cursor: grab;
		touch-action: none;
	}
	.rail .grip {
		align-self: auto;
		width: 100%;
		min-height: 22px;
	}
	.grip:hover,
	.grip:focus-visible {
		color: var(--kk-accent-strong);
		outline: none;
		box-shadow: 0 0 0 2px color-mix(in srgb, var(--kk-accent) 55%, transparent);
	}
	.grip:active {
		cursor: grabbing;
	}
	.pill {
		position: relative;
		display: flex;
		align-items: center;
		gap: 6px;
		min-width: 0;
		padding: 4px 8px 4px 4px;
		border-radius: 999px;
		color: var(--kk-text);
		background: color-mix(in srgb, var(--kk-surface) 90%, var(--c));
		box-shadow: 0 2px 0 var(--kk-line-strong);
		font: inherit;
		cursor: pointer;
		transition: transform 0.1s, box-shadow 0.1s, opacity 0.15s, filter 0.15s;
	}
	.pill:hover:not(.off) {
		transform: translateY(-1px);
		box-shadow: 0 3px 0 color-mix(in srgb, var(--c) 70%, #000);
	}
	.pill:active:not(.off) {
		transform: translateY(1px);
		box-shadow: 0 1px 0 color-mix(in srgb, var(--c) 70%, #000);
	}
	.pill:focus-visible {
		outline: 2px solid var(--c);
		outline-offset: 2px;
	}
	.pill.off {
		cursor: not-allowed;
		opacity: 0.55;
		filter: grayscale(0.85);
	}
	.pill.none {
		opacity: 0.35;
	}
	.orb {
		display: grid;
		place-items: center;
		flex: none;
		width: 28px;
		height: 28px;
		border-radius: 50%;
		color: #fff;
		background: var(--c);
		box-shadow: inset 0 -2px 0 rgb(0 0 0 / 0.2);
	}
	.txt {
		display: flex;
		flex-direction: column;
		min-width: 0;
		line-height: 1.1;
		text-align: left;
	}
	.nm {
		font-size: 11px;
		font-weight: 700;
		white-space: nowrap;
	}
	.cnt {
		font-size: 12px;
		font-weight: 800;
		color: color-mix(in srgb, var(--c) 72%, var(--kk-text));
	}
	.key {
		margin-left: auto;
		padding-left: 4px;
		font-size: 10px;
		font-weight: 800;
		color: var(--kk-muted);
		opacity: 0.8;
	}
	.rail .pill {
		flex-direction: column;
		gap: 1px;
		width: 100%;
		padding: 5px 4px 3px;
		border-radius: 16px;
	}
	.rail .key {
		margin: 0;
		padding: 0;
		font-size: 9px;
	}
	.n {
		position: absolute;
		top: -5px;
		right: -3px;
		min-width: 16px;
		padding: 0 4px;
		border-radius: 999px;
		font-size: 10px;
		font-weight: 800;
		line-height: 16px;
		text-align: center;
		color: #fff;
		background: var(--c);
		box-shadow: 0 1px 0 color-mix(in srgb, var(--c) 70%, #000);
	}
	/* the round's total: our accent, whatever colours the items have */
	.sum {
		display: inline-flex;
		align-items: center;
		gap: 6px;
		flex: none;
		padding: 3px 10px 3px 8px;
		border-radius: 999px;
		font-size: 11px;
		font-weight: 800;
		white-space: nowrap;
		color: var(--kk-accent-strong);
		background: var(--kk-accent-soft);
		box-shadow: 0 2px 0 color-mix(in srgb, var(--kk-fill-edge) 35%, transparent);
	}
	.pips {
		display: inline-flex;
		gap: 3px;
	}
	.pips i {
		width: 6px;
		height: 6px;
		border-radius: 50%;
		background: color-mix(in srgb, var(--kk-accent-strong) 25%, transparent);
	}
	.pips i.on {
		background: var(--kk-fill);
		box-shadow: 0 1px 0 var(--kk-fill-edge);
	}
	.rail .sum {
		flex-direction: column;
		gap: 3px;
		padding: 4px 3px;
		font-size: 10px;
		text-align: center;
		width: 100%;
	}
	.rail .pips {
		gap: 2px;
	}
	@media (prefers-reduced-motion: reduce) {
		.pill,
		.kk-items {
			transition: none;
		}
	}
</style>
