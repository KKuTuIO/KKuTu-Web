<script>
	import Icon from './Icon.svelte';
	import RewardBundle from './RewardBundle.svelte';
	import { t } from '../i18n.svelte.js';
	import { gaugeView } from '../tile.js';

	/**
	 * A gauge with checkpoints, as wide as the place it is in: one bar from 0 to the last checkpoint, and every checkpoint a mark on it at its own share of
	 * the way (a checkpoint at 150 of 600 sits a quarter in), with its reward above it and its points below; a reached one is checked. It is shared by the
	 * campaign's hot-time gauge and the tile roulette's mileage. The track scrolls sideways rather than squeeze checkpoints that lie close together.
	 * @type {{points:number, tiers:{points:number, rewards:any[]}[], shop?:any, details?:any, label:string, size?:number, minWidth?:string}}
	 */
	let { points, tiers, shop = {}, details = null, label, size = 72, minWidth = '34rem' } = $props();
	const bar = $derived(gaugeView({ points, steps: tiers }));
	const num = (n) => Number(n).toLocaleString();
	// the rows of the track, from the top: the rewards and a tick (size + 20px), the bar, the points
	const barTop = $derived(size + 22);
</script>

{#if bar}
	<div class="overflow-x-auto">
		<div class="relative mx-12" style:height="{size + 66}px" style:min-width={minWidth}>
			<ol>
				{#each bar.steps as m, i (m.points)}
					<li class="absolute top-0 flex -translate-x-1/2 flex-col items-center gap-1" style:left="{m.at * 100}%">
						<span class="sr-only">{t('play.news.campaign.tierN', { n: i + 1 })}</span>
						<span class="relative">
							<RewardBundle rewards={m.rewards} {shop} {details} {size} dim={m.reached} />
							{#if m.reached}<span class="pointer-events-none absolute inset-0 grid place-items-center"><Icon name="check" size={Math.round(size * 0.6)} weight={700} class="text-(color:--kk-accent) drop-shadow-[0_2px_0_rgb(0_0_0/0.6)]" /><span class="sr-only">{t('play.news.campaign.reached')}</span></span>{/if}
						</span>
						<span class="h-4 w-0.5 {m.reached ? 'bg-[#facc15]' : 'bg-white/70'}" aria-hidden="true"></span>
					</li>
				{/each}
			</ol>
			<div class="absolute right-0 left-0 h-3 rounded-full bg-black/60 ring-2 ring-white/40" style:top="{barTop}px" role="progressbar" aria-label={label} aria-valuemin="0" aria-valuemax={bar.last} aria-valuenow={Math.min(bar.points, bar.last)}>
				<span class="block h-full rounded-full bg-gradient-to-r from-[#fde047] to-[#f59e0b] transition-[width] duration-500" style:width="{bar.ratio * 100}%"></span>
				{#each bar.steps as m (m.points)}<span class="absolute top-1/2 size-4 -translate-x-1/2 -translate-y-1/2 rounded-full border-2 {m.reached ? 'border-[#facc15] bg-white' : 'border-white/70 bg-neutral-800'}" style:left="{m.at * 100}%" aria-hidden="true"></span>{/each}
			</div>
			<ul class="absolute right-0 left-0" style:top="{barTop + 18}px" aria-hidden="true">
				{#each bar.steps as m (m.points)}<li class="absolute -translate-x-1/2 text-sm font-extrabold tabular-nums text-white [text-shadow:0_1px_2px_rgb(0_0_0/0.7)]" style:left="{m.at * 100}%">{num(m.points)}</li>{/each}
			</ul>
		</div>
	</div>
{/if}
