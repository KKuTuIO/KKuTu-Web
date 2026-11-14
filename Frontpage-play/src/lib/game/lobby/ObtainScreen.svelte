<script>
	import { onMount } from 'svelte';
	import { ms, reducedMotion } from '../motion.js';
	import { t } from '../i18n.svelte.js';
	import { itemName } from '../shop.js';
	import GameIcon from './GameIcon.svelte';
	import Icon from './Icon.svelte';
	import Rich from '../ui/Rich.svelte';
	import ShopThumb from './ShopThumb.svelte';
	import Sparkles from './Sparkles.svelte';

	/**
	 * "You got these", and the one screen for everything the game hands out: a gacha result, a level-up, meant for coupons and mail
	 * rewards as well. The title is in the stage lettering of 아이템 발동! (`kk-fire-text`) above a band across the whole width on our
	 * own surface (the veil dims what is behind it); in the band the items in the same cells as the gacha's pool (picture, name,
	 * ×count), then the note and the page controls (like the ranking's; also A / D and the arrow keys); the confirm button (Enter or
	 * Space; the button shows its Enter key) is under the band. Besides item keys a cell can be `ping` or `exp`, shown with the game's own icons.
	 *
	 * The reveal is made of our pop-up keyframes (`kk-pop-in` / `kk-pop-out`, the dialogs' own): the cells pop up one after another,
	 * each ×count chip pops after its cell, and everything leaves with `kk-pop-out`. `rare` keys (a set) come after a short beat with an accent ring.
	 *
	 * For a sequence (level-ups one after another) the owner gives `onconfirm` and keeps `open` itself: confirming then only calls it,
	 * with a short guard so a held key or a double press cannot run through the sequence; `onskip` (Esc, and a button when
	 * `skipLabel` is given) skips the rest; `rev` changes with each step so the cells pop up again; `kind` is only a label on the dialog; `ambient="shine"` spins blue sunburst rays behind the title and puts a few twinkling stars on its ribbon (a level-up); `ribbon` sets the title on a ribbon that straddles the band's top edge; `notice` is the description, centred right under the items (it may carry the safe rich text); `palette` picks the ribbon's colours: `violet` (level-up) or `teal`, the lobby's call-to-action green-to-teal (items).
	 * @type {{open:boolean, title:string, subtitle?:string, accent?:string, palette?:'violet'|'teal', items:{key:string,q:number}[], rare?:Set<string>, shop:Record<string,any>, details:Record<string,any>, lead?:import('svelte').Snippet, confirmLabel?:string, onconfirm?:()=>void, onskip?:()=>void, skipLabel?:string, counter?:string, rev?:number|string, kind?:string, ambient?:'shine'|'', ribbon?:boolean, notice?:string}}
	 */
	let { open = $bindable(false), title, subtitle = '', accent = '', palette = 'violet', items = [], rare = new Set(), shop, details, lead, confirmLabel = '', onconfirm, onskip, skipLabel = '', counter = '', rev = 0, kind = '', ambient = '', ribbon = false, notice = '' } = $props();
	let closing = $state(false);
	/** Closes (the pop-out runs from the effect below, at once when motion is reduced). */
	const dismiss = () => (open = false);
	const confirm = () => (onconfirm ? onconfirm() : dismiss());
	let el = $state();
	let page = $state(0);
	let width = $state(1280);
	let height = $state(800);
	// As many cells as fit in a row (a cell and its gap are 10rem), in two rows when the window is tall enough: a ten-draw fits one page.
	const perPage = $derived(Math.max(2, Math.min(8, Math.floor((width - 96) / 160))) * (height >= 700 ? 2 : 1));
	const pages = $derived(Math.max(1, Math.ceil(items.length / perPage)));
	const shown = $derived(items.slice(page * perPage, (page + 1) * perPage));
	const go = (d) => (page = Math.min(pages - 1, Math.max(0, page + d)));
	const nameOf = (key) => (key === 'ping' ? t('play.obtain.ping') : key === 'exp' ? t('play.obtain.exp') : itemName(details, key));

	$effect(() => {
		if (!el) return;
		if (open) {
			closing = false; // opened again while it was still leaving
			if (!el.open) {
				page = 0;
				el.showModal();
			}
		} else if (el.open) {
			if (reducedMotion()) return el.close();
			closing = true;
			setTimeout(() => {
				if (open) return; // opened again meanwhile
				closing = false;
				if (el?.open) el.close();
			}, 200);
		}
	});
	$effect(() => {
		items; // a new set of items starts on its first page
		rev;
		page = 0;
	});

	// a sequence's step takes a moment before it answers: a key held down, or pressed again at once, must not run through the rest
	let ready = $state(true);
	$effect(() => {
		rev;
		if (!onconfirm) return void (ready = true);
		ready = false;
		const timer = setTimeout(() => (ready = true), ms(350));
		return () => clearTimeout(timer);
	});

	// Sparkles of the ribbon (`ambient="shine"`): a few four-point stars and soft dots at its ends and above and below it. x and y are
	// percentages of the ribbon's box (a negative one lies outside it); fixed, so they never jump on a re-render.
	const SPARKLES = [
		{ star: 1, x: -5, y: 4, size: 34, tint: '#ffffff', t: 2.2, d: 0 }, { star: 1, x: 9, y: -34, size: 18, tint: 'color-mix(in srgb, var(--rb-ray) 30%, #fff)', t: 1.9, d: 0.7 },
		{ star: 1, x: 22, y: 118, size: 15, tint: 'color-mix(in srgb, var(--rb-glow) 45%, #fff)', t: 2.4, d: 1.2 }, { star: 1, x: 50, y: -52, size: 24, tint: '#ffffff', t: 2.6, d: 0.3 },
		{ star: 1, x: 76, y: 120, size: 17, tint: 'color-mix(in srgb, var(--rb-ray) 30%, #fff)', t: 2.1, d: 1.7 }, { star: 1, x: 91, y: -30, size: 20, tint: 'color-mix(in srgb, var(--rb-glow) 45%, #fff)', t: 2.3, d: 1 },
		{ star: 1, x: 105, y: 22, size: 36, tint: '#ffffff', t: 2.5, d: 0.5 }, { star: 0, x: 14, y: 8, size: 6, tint: '#ffffff', t: 1.8, d: 0.9 },
		{ star: 0, x: 84, y: 96, size: 7, tint: 'color-mix(in srgb, var(--rb-ray) 30%, #fff)', t: 2, d: 0.1 }, { star: 0, x: 62, y: -40, size: 5, tint: '#ffffff', t: 1.7, d: 1.4 }
	];
	// The ribbon, the rays and the stars share one palette, handed down as CSS variables. `teal` is built from the lobby call-to-action tokens
	// (the 매칭 시작 button), so it follows the theme.
	const PALETTES = {
		violet: { from: '#58a6ff', mid: '#7a85ff', to: '#a773ff', tail: '#4a4fc4', fold: '#2d2f88', edge: '#3b3fa8', ray: '#78a0ff', glow: '#7a85ff', c: '#6f5cf0' },
		teal: { from: 'var(--kk-cta-from)', mid: 'color-mix(in srgb, var(--kk-cta-from) 50%, var(--kk-cta-to))', to: 'var(--kk-cta-to)', tail: 'color-mix(in srgb, var(--kk-cta-to) 62%, #000)', fold: 'color-mix(in srgb, var(--kk-cta-edge) 60%, #000)', edge: 'var(--kk-cta-edge)', ray: 'var(--kk-cta-to)', glow: 'var(--kk-cta-to)', c: 'var(--kk-cta-to)' }
	};
	const P = $derived(PALETTES[palette] ?? PALETTES.violet);
	const paletteStyle = $derived(`--rb-from:${P.from};--rb-mid:${P.mid};--rb-to:${P.to};--rb-tail:${P.tail};--rb-fold:${P.fold};--rb-edge:${P.edge};--rb-ray:${P.ray};--rb-glow:${P.glow};--c:${accent || P.c}`);
	const uid = $props.id();
	const isGo = (e) => e.key === 'Enter' || e.key === ' ';
	function key(e) {
		if (!open) return;
		if (e.key === 'a' || e.key === 'A' || e.key === 'ArrowLeft') go(-1);
		else if (e.key === 'd' || e.key === 'D' || e.key === 'ArrowRight') go(1);
		else if (isGo(e) || (e.key === 'Escape' && onskip)) {
			e.preventDefault();
			e.stopPropagation();
			if (e.repeat || !ready) return;
			if (isGo(e)) confirm();
			else onskip();
		}
	}
	/** A button the key would also press clicks on keyup: that must not count a second time. */
	function keyup(e) {
		if (open && (isGo(e) || (e.key === 'Escape' && onskip))) {
			e.preventDefault();
			e.stopPropagation();
		}
	}

	onMount(() => () => el?.open && el.close());
</script>

<svelte:window bind:innerWidth={width} bind:innerHeight={height} onkeydowncapture={key} onkeyupcapture={keyup} />

<dialog bind:this={el} aria-label={title} data-rev={rev} data-kind={kind || undefined} oncancel={(e) => (e.preventDefault(), onskip ? ready && onskip() : dismiss())} onclose={() => !onconfirm && (open = false)} onclick={(e) => e.target === el && !onconfirm && dismiss()} class="obtain m-0 size-full max-h-none max-w-none bg-transparent p-0 outline-none">
	{#if open || closing}
		<div class="grid size-full place-items-center" onclick={(e) => e.target === e.currentTarget && !onconfirm && dismiss()} role="presentation" style={paletteStyle}>
			<div class="stack relative flex w-full flex-col items-center gap-5 {closing ? 'closing' : ''}">
				{#if ambient === 'shine'}<div class="rays-half pointer-events-none absolute" aria-hidden="true"><div class="rays"></div></div>{/if}
				<div class="relative flex flex-col items-center gap-1 px-4 text-center {ribbon ? 'z-10 -mb-14' : ''}">
					{#if ribbon}
						<!-- the ribbon, drawn by hand: an arched body, two swallowtail tails behind it and the folds that join them -->
						<div class="ribbon relative w-[min(22rem,92vw)]">
							<svg viewBox="0 0 360 96" class="block w-full" aria-hidden="true">
								<defs>
									<clipPath id="rb-{uid}"><path d="M52 22 Q180 -4 308 22 L308 78 Q180 52 52 78 Z" /></clipPath>
									<linearGradient id="rbg-{uid}" x1="0" y1="0" x2="1" y2="0">
										<stop offset="0" style="stop-color: var(--rb-from)" />
										<stop offset="0.5" style="stop-color: var(--rb-mid)" />
										<stop offset="1" style="stop-color: var(--rb-to)" />
									</linearGradient>
								</defs>
								<path class="rb-tail" d="M0 38 H80 V92 H0 L24 65 Z" />
								<path class="rb-tail" d="M360 38 H280 V92 H360 L336 65 Z" />
								<path class="rb-fold" d="M52 78 L80 92 L80 74 Z" />
								<path class="rb-fold" d="M308 78 L280 92 L280 74 Z" />
								<path d="M52 22 Q180 -4 308 22 L308 78 Q180 52 52 78 Z" fill="url(#rbg-{uid})" />
								<g clip-path="url(#rb-{uid})" fill="#fff">
									<path opacity="0.14" d="M70 0 L110 0 L84 90 L44 90 Z" />
									<path opacity="0.14" d="M150 0 L190 0 L164 90 L124 90 Z" />
									<path opacity="0.14" d="M230 0 L270 0 L244 90 L204 90 Z" />
									<path opacity="0.14" d="M310 0 L350 0 L324 90 L284 90 Z" />
									<path opacity="0.3" d="M52 22 Q180 -4 308 22 L308 32 Q180 6 52 32 Z" />
									<path class="rb-shine" d="M-70 -10 L-26 -10 L-56 110 L-100 110 Z" />
								</g>
								<path class="rb-edge" d="M52 22 Q180 -4 308 22 L308 78 Q180 52 52 78 Z" />
							</svg>
							<h2 class="ribbon-title kk-fire-text absolute inset-x-0 top-[39%] m-0 -translate-y-1/2 text-center text-[clamp(1.7rem,5vw,2.4rem)] leading-none">{title}</h2>
							{#if ambient === 'shine'}
								<Sparkles list={SPARKLES} />
							{/if}
						</div>
					{:else}
						<h2 class="title kk-fire-text m-0 text-[clamp(2rem,6vw,3.25rem)] leading-tight">{title}</h2>
					{/if}
					{#if subtitle}<p class="subtitle m-0 text-base font-bold text-white [text-shadow:0_2px_0_rgb(0_0_0/0.45)]">{subtitle}</p>{/if}
				</div>

				<div class="relative z-[1] w-full">
					<section class="band w-full border-y-[1.5px] border-(color:--kk-line) bg-(--kk-surface) text-(color:--kk-text) shadow-[0_3px_0_var(--kk-line)]">
						<div class="mx-auto flex max-w-6xl flex-col items-center gap-4 px-6 pb-6 {ribbon ? 'pt-14' : 'pt-6'}">
							{#if lead}<div class="flex justify-center">{@render lead()}</div>{/if}
							{#if shown.length}
								<ul class="flex min-h-36 w-full flex-wrap content-start justify-center gap-3">
									{#each shown as it, i (rev + ':' + page + ':' + it.key)}
										<li style="--d:{(i * 140 + 160 + (rare.has(it.key) ? 320 : 0))}ms" class="cell relative flex w-36 flex-col items-center justify-center gap-2 rounded-xl bg-(--kk-surface-2) p-3 text-center shadow-[0_2px_0_var(--kk-line)] {rare.has(it.key) ? 'rare' : ''}">
											{#if it.key === 'ping'}<span class="grid size-20 place-items-center"><GameIcon name="ping" size={72} /></span>
											{:else if it.key === 'exp'}<span class="grid size-20 place-items-center"><GameIcon name="xp" size={72} /></span>
											{:else}<ShopThumb id={it.key} {shop} class="size-20" />{/if}
											<span class="flex min-h-9 items-center justify-center text-sm leading-[1.15rem] font-bold break-keep [overflow-wrap:anywhere]"><span class="line-clamp-2">{nameOf(it.key)}</span></span>
											{#if it.q > 1}<b class="chip absolute top-2 right-2 rounded-md bg-(--kk-fill) px-1.5 text-xs leading-5 text-white tabular-nums">×{it.q}</b>{/if}
										</li>
									{/each}
								</ul>
							{/if}

							{#if notice}<p class="notice m-0 max-w-2xl rounded-2xl bg-(--kk-accent-soft) px-4 py-2 text-center text-sm leading-relaxed font-bold text-(color:--kk-accent-strong)"><Rich html={notice} /></p>{/if}
							{#if pages > 1}
								<div class="flex items-center gap-3">
									<button type="button" aria-label={t('play.rank.prev')} class="kk-btn grid size-11 place-items-center p-0" disabled={page === 0} onclick={() => go(-1)}><Icon name="chevron_left" size={24} /></button>
									<span class="min-w-16 text-center text-sm font-bold tabular-nums" aria-live="polite">{page + 1} / {pages}</span>
									<button type="button" aria-label={t('play.rank.next')} class="kk-btn grid size-11 place-items-center p-0" disabled={page >= pages - 1} onclick={() => go(1)}><Icon name="chevron_right" size={24} /></button>
								</div>
							{/if}
						</div>
					</section>
				</div>

				<div class="actions flex items-center gap-3">
					{#if counter}<span class="rounded-full bg-black/45 px-3 py-1 text-xs font-bold text-white tabular-nums">{counter}</span>{/if}
					{#if onskip && skipLabel}<button type="button" class="kk-btn px-6 outline-none focus-visible:outline-none" onclick={() => onskip()}>{skipLabel}</button>{/if}
					<button type="button" class="kk-btn kk-btn-primary inline-flex items-center justify-center gap-2.5 px-8 outline-none focus-visible:outline-none" onclick={confirm}>{confirmLabel || t('kkutu.dialog.obtain.ok')}<kbd class="kk-key" aria-hidden="true">Enter</kbd></button>
				</div>
			</div>
		</div>
	{/if}
</dialog>

<style>
	/* beats the theme's `.kk dialog` surface colours: the dialog is only the veil, the band on it is the surface */
	.obtain {
		background: transparent;
	}
	.obtain::backdrop {
		background: rgb(0 0 0 / 0.4);
		animation: kk-fade-in 0.2s ease-out both;
	}
	.obtain:has(.closing)::backdrop {
		animation: kk-fade-out 0.2s ease-in both;
	}
	/* the band comes up with a soft rise; it leaves with the dialogs' pop-out */
	.band {
		animation: ob-rise 0.3s cubic-bezier(0.22, 1, 0.36, 1) both;
	}
	/* Its surface is set here too, and with the dialog in the selector, not only by the utility class: a screen that shows this inside the lobby's panel
	   (the arcade, the room list) is under `.glass section { background: transparent }`, which is more specific than `.band` alone and took the band away */
	.obtain .band {
		background: var(--kk-surface);
	}
	.stack.closing {
		animation: kk-pop-out 0.2s ease-in both;
	}
	/* the title skews in from the left, like 아이템 발동! */
	.title {
		animation: kk-title-in 0.5s cubic-bezier(0.22, 1, 0.36, 1) 0.05s both;
	}
	/* the cells: our pop-up keyframes, one after another (--d is each cell's turn) */
	.cell {
		animation: kk-pop-in 0.42s cubic-bezier(0.34, 1.7, 0.64, 1) both;
		animation-delay: var(--d);
	}
	/* the count chip pops right after its cell */
	.chip {
		animation: kk-check-pop 0.3s ease-out both;
		animation-delay: calc(var(--d) + 320ms);
	}
	/* a rare prize: a ring that pulses outwards once it has landed */
	.rare {
		box-shadow: 0 2px 0 var(--kk-line), 0 0 0 2px var(--kk-accent);
		animation:
			kk-pop-in 0.42s cubic-bezier(0.34, 1.7, 0.64, 1) both,
			ob-glow 0.9s ease-out both;
		animation-delay: var(--d), calc(var(--d) + 380ms);
	}
	/* the sunburst behind the ribbon: only a low upper half shows. The still box clips at the ribbon's centre line (and fades out at its top);
	   inside it a disc of translucent blue rays turns slowly, its centre on that line (clipping the turning disc itself would turn the clip too) */
	.rays-half {
		left: 50%;
		top: calc(2.4rem - 210px);
		z-index: 0;
		width: 600px;
		height: 210px;
		margin-left: -300px;
		overflow: hidden;
		mask-image: linear-gradient(to top, #000 45%, transparent 100%);
	}
	.rays {
		width: 600px;
		height: 600px;
		margin-top: -90px; /* the disc's centre (300px down) lands on the box's bottom edge (210px) */
		border-radius: 50%;
		background: repeating-conic-gradient(from 0deg, color-mix(in srgb, var(--rb-ray) 30%, transparent) 0deg 13deg, transparent 13deg 36deg);
		mask-image: radial-gradient(circle, #000 0%, rgb(0 0 0 / 0.75) 26%, transparent 68%);
		animation: ob-spin 36s linear infinite;
	}
	@keyframes ob-spin {
		to {
			transform: rotate(360deg);
		}
	}
	/* a band of light sweeps across the ribbon now and then */
	.rb-shine {
		opacity: 0;
		animation: ob-shine 3.4s ease-in-out 1s infinite;
	}
	@keyframes ob-shine {
		0% {
			opacity: 0;
			transform: translateX(0);
		}
		8% {
			opacity: 0.6;
		}
		44% {
			opacity: 0.6;
			transform: translateX(490px);
		}
		52%,
		100% {
			opacity: 0;
			transform: translateX(490px);
		}
	}
	/* the ribbon unfolds from the middle, then the lettering pops onto it */
	.ribbon {
		animation: ob-unfurl 0.45s cubic-bezier(0.34, 1.5, 0.64, 1) 0.05s both;
		filter: drop-shadow(0 4px 0 rgb(0 0 0 / 0.28));
	}
	.ribbon-title {
		animation: kk-pop-in 0.4s cubic-bezier(0.34, 1.7, 0.64, 1) 0.2s both;
	}
	.rb-tail {
		fill: var(--rb-tail);
	}
	.rb-fold {
		fill: var(--rb-fold);
	}
	.rb-edge {
		fill: none;
		stroke: var(--rb-edge);
		stroke-width: 3;
		stroke-linejoin: round;
	}
	@keyframes ob-unfurl {
		from {
			opacity: 0;
			transform: scaleX(0.35);
		}
	}
	@keyframes ob-rise {
		from {
			opacity: 0;
			transform: translateY(24px);
		}
	}
	@keyframes ob-glow {
		from {
			box-shadow: 0 2px 0 var(--kk-line), 0 0 0 2px var(--kk-accent), 0 0 0 2px color-mix(in srgb, var(--kk-accent) 55%, transparent);
		}
		to {
			box-shadow: 0 2px 0 var(--kk-line), 0 0 0 2px var(--kk-accent), 0 0 0 18px transparent;
		}
	}
	@media (prefers-reduced-motion: reduce) {
		.rays,
		.rb-shine {
			animation: none;
		}
		.band,
		.title,
		.ribbon,
		.ribbon-title,
		.stack.closing,
		.cell,
		.chip,
		.rare,
		.obtain::backdrop {
			animation: none;
		}
	}
</style>
