<script>
	import { t } from '../i18n.svelte.js';
	import { untrack } from 'svelte';
	import { reducedMotion } from '../motion.js';
	import { playSound } from '../audio.svelte.js';
	import Icon from '../lobby/Icon.svelte';
	import HoloCard from './reactbits/HoloCard.svelte';
	import { CARD, paintLicense } from './licenseArt.js';
	import { card, licenseInfo, me } from './store.svelte.js';

	/**
	 * The license being handed over, on a dimmed screen. A new one comes down hanging from a lanyard (React Bits "Lanyard", loaded only then:
	 * it brings three.js), and 카드 받기 brings it close, with a holographic foil that follows the pointer (React Bits "HoloCard"; a click turns
	 * it over). Looking at my license again opens the close view at once. With 움직임 감소 it is a still picture: neither effect runs.
	 */
	let { store } = $props();
	let el = $state();
	let hang = $state();
	let width = $state(1280);
	let height = $state(800);
	let phase = $state('card'); // 'lanyard' | 'card'
	let still = $state(false);
	let art = $state.raw(null); // {front, back}
	let label = $state('');
	const scale = $derived(Math.max(0.3, Math.min(1.15, (width - 28) / CARD.width, (height - 200) / CARD.height)));

	let round = 0; // a picture that finishes after the screen was closed and opened again is dropped
	$effect(() => {
		if (!el) return;
		if (card.open && !el.open) {
			untrack(show);
		} else if (!card.open && el.open) el.close();
	});
	function show() {
		const mine = ++round;
		still = reducedMotion();
		phase = card.fresh && !still ? 'lanyard' : 'card';
		art = null;
		const name = store?.me?.name ?? t('play.malmoi.card.selfName');
		label = t('play.malmoi.card.alt', { name, level: me.level });
		el.showModal();
		playSound('success', { ui: true });
		paintLicense({ ...licenseInfo(), name, level: me.level, equip: store?.me?.equip, shop: store?.shop, parts: store?.net?.meta?.moremiParts })
			.then((made) => mine === round && (art = made))
			.catch(() => mine === round && (card.open = false));
	}

	$effect(() => {
		if (!card.open || phase !== 'lanyard' || !art || !hang) return;
		const { front, back } = art.hung;
		let alive = true;
		let hung = null;
		import('./reactbits/lanyard.js')
			.then(({ createLanyard }) => {
				if (!alive) return;
				hung = createLanyard(hang, {
					frontImage: front, backImage: back, orientation: 'landscape', cardColor: '#3553a0', cornerRadius: 0.2, finish: 'glossy',
					size: 0.36, strapLength: 0.45, strapColor: '#3553a0', strapImage: art.strap, strapWidth: 0.8, metal: 'silver', breeze: 0.25
				});
				if (!hung) phase = 'card'; // no WebGL
			})
			.catch(() => alive && (phase = 'card'));
		return () => {
			alive = false;
			hung?.destroy();
		};
	});
	const title = $derived(card.fresh ? t('play.malmoi.card.issuedTitle') : t('play.malmoi.card.myLicense'));
</script>

<svelte:window bind:innerWidth={width} bind:innerHeight={height} />

<dialog bind:this={el} aria-label={t('play.malmoi.card.title')} onclose={() => (card.open = false)} class="license m-0 size-full max-h-none max-w-none overflow-hidden p-0 outline-none">
	{#if card.open}
		{#if phase === 'lanyard'}
			<div class="relative size-full">
				<div bind:this={hang} class="hang absolute inset-0" role="img" aria-label={label}></div>
				<div class="pointer-events-none absolute inset-x-0 bottom-0 flex flex-col items-center gap-4 px-3 pb-[max(2rem,env(safe-area-inset-bottom))]">
					<h2 class="kk-fire-text m-0 text-[clamp(2rem,7vw,3rem)] leading-tight">{title}</h2>
					<!-- svelte-ignore a11y_autofocus -->
					<button type="button" autofocus disabled={!art} class="kk-btn kk-btn-primary pointer-events-auto min-h-12 px-12 text-base" onclick={() => (phase = 'card')}>{t('play.malmoi.card.take')}</button>
				</div>
			</div>
		{:else}
			<div class="flex size-full flex-col items-center justify-center gap-6 px-3">
				<h2 class="kk-fire-text m-0 text-[clamp(2rem,7vw,3rem)] leading-tight">{title}</h2>
				<!-- the box the card takes, kept while the picture is being made -->
				<div class="grid shrink-0 place-items-center" style:width="{CARD.width * scale}px" style:height="{CARD.height * scale}px">
					{#if !art}
						<span class="animate-spin text-white" role="status" aria-label={t('play.malmoi.card.making')}><Icon name="progress_activity" size={40} /></span>
					{:else if still}
						<img src={art.front} alt={label} draggable="false" class="size-full shadow-[0_18px_44px_rgb(0_0_0/0.45)]" style:border-radius="{20 * scale}px" />
					{:else}
						<HoloCard image={art.front} backImage={art.back} alt={label} width={CARD.width * scale} radius={20 * scale} preset="bursts" intensity={0.4} edgeSparkle={0.35} frame={3} glare={0.2} tiltMax={12} hoverScale={1.03} />
					{/if}
				</div>
				<!-- svelte-ignore a11y_autofocus -->
				<button type="button" autofocus class="kk-btn kk-btn-primary min-h-12 px-12 text-base" onclick={() => (card.open = false)}>{t('play.malmoi.common.ok')}</button>
			</div>
		{/if}
	{/if}
</dialog>

<style>
	/* beats the theme's `.kk dialog` surface colour: the dialog is only the veil */
	.license {
		background: transparent;
	}
	.license::backdrop {
		background: rgb(0 0 0 / 0.8);
		animation: kk-fade-in 0.2s ease-out both;
	}
	/* what React Bits' Lanyard.css gave its container and canvas */
	.hang {
		overflow: hidden;
	}
	.hang :global(canvas) {
		display: block;
		width: 100%;
		height: 100%;
	}
</style>
