<script>
	import Dialog from './Dialog.svelte';
	import GameIcon from './GameIcon.svelte';
	import Icon from './Icon.svelte';
	import RewardChip from './RewardChip.svelte';
	import { t } from '../i18n.svelte.js';
	import { rewardsOfMap } from '../news.js';
	import { rewardLabel } from '../newsFormat.js';

	/**
	 * The question before an exchange: what I give, what I get, and what is left of each thing I give afterwards, so a spend is never a surprise.
	 * `have(cost)` is how much of a cost I own now; `onconfirm` runs the exchange.
	 * @type {{open:boolean, exchange:any, title:string, shop?:any, details?:any, have:(cost:any)=>number, onconfirm:()=>void, dialogTitle?:string, doLabel?:string}}
	 */
	let { open = $bindable(false), exchange, title, shop = {}, details = null, have, onconfirm, dialogTitle = t('play.news.exchangeDialog.title'), doLabel = t('play.news.exchangeDo') } = $props();
	const cost = $derived(rewardsOfMap(exchange?.cost));
	const gives = $derived(rewardsOfMap(exchange?.gives));
	const confirm = () => {
		open = false;
		onconfirm();
	};
</script>

<Dialog bind:open title={dialogTitle}>
	{#if exchange}
		<div class="flex flex-col gap-4">
			<p class="text-center text-base font-extrabold">{title}</p>
			<div class="grid grid-cols-[1fr_auto_1fr] items-center gap-3">
				<section class="flex flex-col items-center gap-2" aria-label={t('play.news.exchangeDialog.give')}>
					<h4 class="text-xs font-extrabold text-(color:--kk-muted)">{t('play.news.exchangeDialog.give')}</h4>
					{#each cost as c, i (i)}<RewardChip reward={c} {shop} {details} size={44} />{:else}<span class="text-sm font-extrabold text-(color:--kk-accent-strong)">{t('play.news.exchangeFree')}</span>{/each}
				</section>
				<Icon name="arrow_forward" size={28} class="text-(color:--kk-muted)" aria-hidden="true" />
				<section class="flex flex-col items-center gap-2" aria-label={t('play.news.exchangeDialog.get')}>
					<h4 class="text-xs font-extrabold text-(color:--kk-muted)">{t('play.news.exchangeDialog.get')}</h4>
					{#each gives as g, i (i)}<RewardChip reward={g} {shop} {details} size={44} />{/each}
				</section>
			</div>
			{#if cost.length}
				<ul class="flex flex-col gap-1 rounded-xl bg-(--kk-surface-2) px-3 py-2 text-sm" aria-label={t('play.news.exchangeDialog.after')}>
					{#each cost as c, i (i)}
						<li class="flex items-center gap-2">
							{#if c.kind === 'ping'}<GameIcon name="ping" size={18} />{/if}
							<span class="min-w-0 flex-1 truncate font-bold">{rewardLabel(c, details)}</span>
							<span class="tabular-nums text-(color:--kk-muted)">{have(c).toLocaleString()}</span>
							<Icon name="arrow_forward" size={14} class="text-(color:--kk-muted)" aria-hidden="true" />
							<b class="tabular-nums">{Math.max(0, have(c) - c.q).toLocaleString()}</b>
						</li>
					{/each}
				</ul>
			{/if}
			<div class="flex gap-2">
				<button type="button" onclick={() => (open = false)} class="kk-btn min-h-12 flex-1 px-4 text-base">{t('play.link.no')}</button>
				<button type="button" onclick={confirm} class="kk-btn kk-btn-primary min-h-12 flex-1 px-4 text-base">{doLabel}</button>
			</div>
		</div>
	{/if}
</Dialog>
