<script>
	import { onMount } from 'svelte';
	import { t } from '../i18n.svelte.js';
	import { reducedMotion } from '../motion.js';
	import Icon from './Icon.svelte';

	/**
	 * The home page's banner carousel. Slides come from the home page's data (art 2400 x 600, see img/shop/home/home.json);
	 * clicking a slide goes to its link (there is no button on it); the tiles under it move it. The picture is cropped to the width it is shown at, so what matters stays in the middle. Changes by itself every 6 s
	 * unless the pointer or the focus is on it, or motion is reduced.
	 */
	let { slides = [], base = '', index = $bindable(0), onaction } = $props();
	let paused = $state(false);
	const count = $derived(slides.length);
	const at = $derived(Math.min(index, Math.max(0, count - 1)));
	const go = (i) => (index = (i + count) % count);

	onMount(() => {
		const timer = setInterval(() => !paused && count > 1 && !reducedMotion() && go(at + 1), 6000);
		return () => clearInterval(timer);
	});
</script>

{#if count}
	<div
		class="relative h-52 overflow-hidden rounded-2xl bg-(--kk-surface-2) shadow-[0_3px_0_var(--kk-line)] md:h-72"
		role="region"
		aria-roledescription="carousel"
		aria-label={t('play.nav.shop')}
		onpointerenter={() => (paused = true)}
		onpointerleave={() => (paused = false)}
		onfocusin={() => (paused = true)}
		onfocusout={() => (paused = false)}
	>
		{#each slides as s, i (s.id)}
			<button
				type="button"
				tabindex={i === at ? 0 : -1}
				aria-hidden={i !== at}
				aria-label={s.alt}
				onclick={() => onaction?.(s.target)}
				class="absolute inset-0 {i === at ? '' : 'hidden'}"
			>
				<img src={base + s.image} alt="" draggable="false" class="size-full object-cover" />
			</button>
		{/each}
		{#if count > 1}
			<button type="button" aria-label={t('play.shop.hero.prev')} onclick={() => go(at - 1)} class="absolute top-1/2 left-2 grid size-9 -translate-y-1/2 place-items-center rounded-full bg-black/40 text-white hover:bg-black/60"><Icon name="chevron_left" size={24} /></button>
			<button type="button" aria-label={t('play.shop.hero.next')} onclick={() => go(at + 1)} class="absolute top-1/2 right-2 grid size-9 -translate-y-1/2 place-items-center rounded-full bg-black/40 text-white hover:bg-black/60"><Icon name="chevron_right" size={24} /></button>
			<div class="absolute bottom-3 left-1/2 flex -translate-x-1/2 gap-1.5">
				{#each slides as s, i (s.id)}
					<button type="button" aria-label={t('play.shop.hero.go', { n: i + 1 })} aria-current={i === at} onclick={() => go(i)} class="h-2 rounded-full bg-white transition-all {i === at ? 'w-6 opacity-100' : 'w-2 opacity-50'}"></button>
				{/each}
			</div>
		{/if}
	</div>
{/if}
