<script>
	import { onMount } from 'svelte';
	import { reducedMotion } from '../motion.js';
	import { t } from '../i18n.svelte.js';
	import { itemName } from '../shop.js';
	import Icon from './Icon.svelte';
	import Rich from '../ui/Rich.svelte';
	import ShopThumb from './ShopThumb.svelte';

	/**
	 * "You got these": a band across the whole width on our own surface (the veil dims what is behind it), with the items in the
	 * same cells as the gacha's pool (picture, name, ×count), then the note, the page controls (like the ranking's; also A / D and
	 * the arrow keys) and the confirm button (Enter). Used for a gacha result; meant for coupons and mail rewards as well.
	 *
	 * The reveal is made of our pop-up keyframes (`kk-pop-in` / `kk-pop-out`, the dialogs' own): the cells pop up one after another,
	 * each ×count chip pops after its cell, and the band leaves with `kk-pop-out`. `rare` keys (a set) come after a short beat with an accent ring.
	 * @type {{open:boolean, title:string, message?:string, items:{key:string,q:number}[], rare?:Set<string>, shop:Record<string,any>, details:Record<string,any>}}
	 */
	let { open = $bindable(false), title, message = '', items = [], rare = new Set(), shop, details } = $props();
	let closing = $state(false);
	/** Closes with the pop-out (at once when motion is reduced). */
	function dismiss() {
		if (closing) return;
		if (reducedMotion()) return void (open = false);
		closing = true;
		setTimeout(() => ((open = false), (closing = false)), 200);
	}
	let el = $state();
	let page = $state(0);
	let width = $state(1280);
	let height = $state(800);
	// As many cells as fit in a row (a cell and its gap are 10rem), in two rows when the window is tall enough: a ten-draw fits one page.
	const perPage = $derived(Math.max(2, Math.min(8, Math.floor((width - 96) / 160))) * (height >= 700 ? 2 : 1));
	const pages = $derived(Math.max(1, Math.ceil(items.length / perPage)));
	const shown = $derived(items.slice(page * perPage, (page + 1) * perPage));
	const go = (d) => (page = Math.min(pages - 1, Math.max(0, page + d)));

	$effect(() => {
		if (!el) return;
		if (open && !el.open) {
			page = 0;
			el.showModal();
		} else if (!open && el.open) el.close();
	});
	$effect(() => {
		items; // a new set of items starts on its first page
		page = 0;
	});

	function key(e) {
		if (!open) return;
		if (e.key === 'a' || e.key === 'A' || e.key === 'ArrowLeft') go(-1);
		else if (e.key === 'd' || e.key === 'D' || e.key === 'ArrowRight') go(1);
		else if (e.key === 'Enter') (e.preventDefault(), dismiss());
	}

	onMount(() => () => el?.open && el.close());
</script>

<svelte:window bind:innerWidth={width} bind:innerHeight={height} onkeydown={key} />

<dialog bind:this={el} aria-label={title} oncancel={(e) => (e.preventDefault(), dismiss())} onclose={() => (open = false)} onclick={(e) => e.target === el && dismiss()} class="obtain m-0 size-full max-h-none max-w-none bg-transparent p-0 outline-none">
	{#if open}
		<div class="grid size-full place-items-center" onclick={(e) => e.target === e.currentTarget && dismiss()} role="presentation">
			<section class="band w-full {closing ? 'closing' : ''} border-y-[1.5px] border-(color:--kk-line) bg-(--kk-surface) text-(color:--kk-text) shadow-[0_3px_0_var(--kk-line)]">
				<div class="mx-auto flex max-w-6xl flex-col gap-5 px-6 py-6">
					<div class="flex items-center">
						<h2 class="text-2xl font-extrabold">{title}</h2>
						<button type="button" aria-label={t('play.dialog.close')} onclick={dismiss} class="kk-close ml-auto"><span aria-hidden="true"><Icon name="close" size={11} /></span></button>
					</div>

					<ul class="flex min-h-44 flex-wrap content-start justify-center gap-3">
						{#each shown as it, i (page + ':' + it.key)}
							<li style="--d:{(i * 140 + 160 + (rare.has(it.key) ? 320 : 0))}ms" class="cell relative flex w-36 flex-col items-center justify-center gap-2 rounded-xl bg-(--kk-surface-2) p-3 text-center shadow-[0_2px_0_var(--kk-line)] {rare.has(it.key) ? 'rare' : ''}">
								<ShopThumb id={it.key} {shop} class="size-20" />
								<span class="flex min-h-9 items-center justify-center text-sm leading-[1.15rem] font-bold break-keep [overflow-wrap:anywhere]"><span class="line-clamp-2">{itemName(details, it.key)}</span></span>
								{#if it.q > 1}<b class="chip absolute top-2 right-2 rounded-md bg-(--kk-fill) px-1.5 text-xs leading-5 text-white tabular-nums">×{it.q}</b>{/if}
							</li>
						{/each}
					</ul>

					<div class="flex flex-wrap items-center gap-x-6 gap-y-3">
						{#if message}<p class="min-w-60 flex-1 text-xs leading-relaxed text-(color:--kk-muted)"><Rich html={message} /></p>{:else}<span class="flex-1"></span>{/if}
						{#if pages > 1}
							<div class="flex items-center gap-3">
								<button type="button" aria-label={t('play.rank.prev')} class="kk-btn grid size-11 place-items-center p-0" disabled={page === 0} onclick={() => go(-1)}><Icon name="chevron_left" size={24} /></button>
								<span class="min-w-16 text-center text-sm font-bold tabular-nums" aria-live="polite">{page + 1} / {pages}</span>
								<button type="button" aria-label={t('play.rank.next')} class="kk-btn grid size-11 place-items-center p-0" disabled={page >= pages - 1} onclick={() => go(1)}><Icon name="chevron_right" size={24} /></button>
							</div>
						{/if}
						<button type="button" class="kk-btn kk-btn-primary px-10" onclick={dismiss}>{t('kkutu.dialog.obtain.ok')}</button>
					</div>
				</div>
			</section>
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
	.band.closing {
		animation: kk-pop-out 0.2s ease-in both;
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
		.band,
		.cell,
		.chip,
		.rare,
		.obtain::backdrop {
			animation: none;
		}
	}
</style>
