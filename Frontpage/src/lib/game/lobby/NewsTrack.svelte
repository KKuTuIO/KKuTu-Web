<script>
	import Icon from './Icon.svelte';
	import RewardBundle from './RewardBundle.svelte';
	import { t } from '../i18n.svelte.js';
	import { trackCount, trackStep } from '../news.js';

	/**
	 * Reward steps in a row. When the block names where the player's progress comes from (`source`), the steps already reached are checked
	 * and the one to reach next is ringed; the line between two steps fills as they are passed. Without one the steps are only shown.
	 * @type {{store:any, event:any, block:any, shop?:any, details?:any}}
	 */
	let { store, event, block, shop = {}, details = null } = $props();
	const count = $derived(trackCount(block.source, event, store.net.eventProgress));
</script>

<section class="flex flex-col gap-2">
	{#if block.title || count >= 0}
		<div class="flex items-baseline justify-between gap-2">
			{#if block.title}<h3 class="text-base font-extrabold">{block.title}</h3>{:else}<span></span>{/if}
			{#if count >= 0}<span class="text-sm font-extrabold tabular-nums" aria-label={t('play.news.step.progress', { n: Math.min(count, block.steps.length), total: block.steps.length })}>{Math.min(count, block.steps.length)}/{block.steps.length}{t('play.news.step.unit')}</span>{/if}
		</div>
	{/if}
	<ol class="flex flex-wrap items-stretch gap-x-1 gap-y-2">
		{#each block.steps as step, i (i)}
			{@const state = trackStep(count, i)}
			<li class="flex items-center gap-1" aria-current={state === 'current' ? 'step' : undefined}>
				<span class="relative flex min-w-24 flex-col items-center gap-1.5 rounded-xl border-[1.5px] px-3 py-2 {state === 'done' ? 'border-(color:--kk-line) bg-(--kk-accent-soft)' : 'border-(color:--kk-line) bg-(--kk-surface-2)'} {state === 'current' ? 'border-(--ac,var(--kk-accent)) shadow-[0_2px_0_var(--ac,var(--kk-accent))]' : ''}">
					{#if step.label}<b class="text-xs font-extrabold text-(color:--kk-muted)">{step.label}</b>{/if}
					<RewardBundle rewards={step.rewards} {shop} {details} size={64} />
					{#if state === 'done'}<Icon name="check_circle" size={18} fill class="absolute -top-2 -right-2 rounded-full bg-(--kk-surface) text-(color:--kk-accent-strong)" /><span class="sr-only">{t('play.news.step.done')}</span>{/if}
					{#if state === 'current'}<span class="sr-only">{t('play.news.step.current')}</span>{/if}
				</span>
				{#if i < block.steps.length - 1}<Icon name="chevron_right" size={22} class={state === 'done' ? 'text-(color:--kk-accent-strong)' : 'text-(color:--kk-line-strong)'} />{/if}
			</li>
		{/each}
	</ol>
</section>
