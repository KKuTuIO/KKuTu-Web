<script>
	import { t } from '../i18n.svelte.js';
	import { DOCKS } from '../items.js';

	/**
	 * The places the item bar can be dropped, shown over the stage while the grip is dragged; the one under the pointer lights up.
	 * @type {{hover:string|null, answer:{x:number,y:number,w:number,h:number}|null, edgeW:number, edgeH:number}}
	 */
	let { hover = null, answer = null, edgeW = 84, edgeH = 56 } = $props();
	const box = (d) =>
		({
			top: `left:6px;right:6px;top:6px;height:${edgeH}px`,
			bottom: `left:6px;right:6px;bottom:6px;height:${edgeH}px`,
			left: `left:6px;top:${edgeH + 12}px;bottom:${edgeH + 12}px;width:${edgeW}px`,
			right: `right:6px;top:${edgeH + 12}px;bottom:${edgeH + 12}px;width:${edgeW}px`,
			answer: answer ? `left:${answer.x}px;top:${answer.y}px;width:${answer.w}px;height:${answer.h}px` : 'display:none'
		})[d];
</script>

<div class="pointer-events-none absolute inset-0 z-30" aria-hidden="true">
	{#each DOCKS as d (d)}
		<div class="zone {hover === d ? 'on' : ''}" style={box(d)}><span>{t(`play.item.dock.${d}`)}</span></div>
	{/each}
</div>

<style>
	.zone {
		position: absolute;
		display: grid;
		place-items: center;
		border: 2px dashed color-mix(in srgb, var(--kk-accent) 55%, transparent);
		border-radius: 14px;
		background: color-mix(in srgb, var(--kk-surface) 55%, transparent);
		color: var(--kk-accent);
		font-size: 12px;
		font-weight: 800;
		transition: background 0.12s, transform 0.12s, border-color 0.12s;
	}
	.zone.on {
		border-style: solid;
		border-color: var(--kk-accent);
		background: color-mix(in srgb, var(--kk-accent) 22%, var(--kk-surface));
		transform: scale(1.015);
	}
	@media (prefers-reduced-motion: reduce) {
		.zone {
			transition: none;
		}
	}
</style>
