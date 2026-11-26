<script>
	import { cdnImg } from '../../cdn.js';

	/**
	 * How a game went, as a mark: a gold, silver or bronze medal with the placement on its face for 1st to 3rd (a win counts as 1st),
	 * and the cracked medal (with its placement too) for 4th and below. A game without a placement shows the bare value.
	 * Art: the CDN's img/play/medal-*.svg.
	 * @type {{placement:number, won?:boolean, size?:number}}
	 */
	let { placement, won = false, size = 40 } = $props();
	const rank = $derived(won ? 1 : Number(placement));
	const kind = $derived(rank === 1 ? 'gold' : rank === 2 ? 'silver' : rank === 3 ? 'bronze' : rank >= 4 ? 'broken' : '');
	// The numeral is filled with a gradient of its own metal (light on the gold, dark steel on the silver, dark on the bronze) and has a
	// thin edge of the opposite tone, so it reads on the medal's face.
	const INK = {
		gold: { fill: 'linear-gradient(to bottom, #ffffff 15%, #fff1a8 55%, #ffd84a 100%)', edge: 'rgb(140 92 0 / 0.75)' },
		silver: { fill: 'linear-gradient(to bottom, #6f7c90 10%, #4a5568 55%, #2f3846 100%)', edge: 'rgb(255 255 255 / 0.8)' },
		bronze: { fill: 'linear-gradient(to bottom, #8a5320 10%, #5f3a12 55%, #3e2208 100%)', edge: 'rgb(255 214 160 / 0.7)' },
		// the cracked medal is dark grey: a light numeral
		broken: { fill: 'linear-gradient(to bottom, #ffffff 10%, #dfe3e8 55%, #b4bcc6 100%)', edge: 'rgb(30 34 40 / 0.85)' }
	};
</script>

<span class="relative inline-grid shrink-0 place-items-center" style:width="{size * 0.88}px" style:height="{size}px" aria-label={String(rank)}>
	{#if kind}
		<img src={cdnImg(`play/medal-${kind}.svg`)} alt="" draggable="false" class="absolute inset-0 size-full" />
		<b class="relative mt-[22%] bg-clip-text leading-none font-black text-transparent tabular-nums" style:font-size="{size * 0.4}px" style:background-image={INK[kind].fill} style:-webkit-background-clip="text" style:filter="drop-shadow(0 1px 0 {INK[kind].edge})">{rank}</b>
	{:else}
		<b class="grid size-[78%] place-items-center rounded-full bg-(--kk-surface-2) text-sm font-black tabular-nums text-(color:--kk-muted)">{placement}</b>
	{/if}
</span>
