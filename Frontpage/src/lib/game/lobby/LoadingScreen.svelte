<script>
	import { onMount } from 'svelte';
	import { fade } from 'svelte/transition';
	import { i18n, t, tList } from '../i18n.svelte.js';

	let { store } = $props();
	const net = $derived(store.net);

	// Stay up until the connection is live AND assets are in, but never flash by in under a moment.
	let minDone = $state(false);
	const show = $derived(net.status === 'connecting' || (net.status === 'ready' && !(net.assetsReady && minDone)));
	const progress = $derived(net.status === 'connecting' ? (net.meta.modes.length ? 35 : 12) : net.assetsReady ? 100 : 78);

	// Tips are locale messages (`play.tip.1`, `play.tip.2`, ...), so they can be edited per language.
	const pick = (except) => {
		const all = tList('play.tip');
		const pool = all.filter((x) => x !== except);
		const from = pool.length ? pool : all;
		return from[Math.floor(Math.random() * from.length)] ?? '';
	};
	let tip = $state('');
	$effect(() => {
		if (i18n.ready && !tip) tip = pick();
	});

	onMount(() => {
		const min = setTimeout(() => (minDone = true), 1200);
		const rotate = setInterval(() => (tip = pick(tip)), 3500);
		return () => {
			clearTimeout(min);
			clearInterval(rotate);
		};
	});
</script>

{#if show}
	<div out:fade={{ duration: 350 }} class="loading fixed inset-0 z-[60] grid place-items-center bg-(--kk-bg) p-6" role="status" aria-live="polite" aria-label={t('play.loading.label')}>
		<div class="flex w-full max-w-xl flex-col items-center gap-8">
			<span class="inline-block">
				<img class="h-16 dark:hidden" src="https://cdn.kkutu.io/img/bi/bi_main.svg" alt={t('play.brand')} />
				<img class="hidden h-16 dark:block" src="https://cdn.kkutu.io/img/bi/bi_white.svg" alt="" />
			</span>

			<div class="flex w-full flex-col gap-4">
				<div class="flex min-h-16 overflow-hidden rounded-xl border-[3px] border-(color:--kk-text) bg-(--kk-surface)">
					<span class="grid w-9 shrink-0 place-items-center bg-(--kk-fill) text-xs leading-none font-black tracking-widest text-white [writing-mode:vertical-rl] [text-orientation:upright]">TIP</span>
					{#key tip}
						<p in:fade={{ duration: 250 }} class="flex flex-1 items-center px-4 py-3 font-bold break-keep">{tip}</p>
					{/key}
				</div>

				<div class="h-6 overflow-hidden rounded-lg border-[3px] border-(color:--kk-text) bg-(--kk-surface)" role="progressbar" aria-valuemin="0" aria-valuemax="100" aria-valuenow={progress}>
					<span class="block h-full bg-(--kk-accent) transition-[width] duration-500 ease-out" style:width="{progress}%"></span>
				</div>
			</div>
		</div>
	</div>
{/if}

<style>
	/* Soft checkerboard, in the spirit of a classic game loading screen. */
	.loading {
		background-image: conic-gradient(color-mix(in srgb, var(--kk-accent) 7%, transparent) 25%, transparent 0 50%, color-mix(in srgb, var(--kk-accent) 7%, transparent) 0 75%, transparent 0);
		background-size: 72px 72px;
	}
</style>
