<script>
	import { t } from '../i18n.svelte.js';
	import Icon from './Icon.svelte';
	/** − 0 + : how many of an item to buy. `min`, `step` and `unit` let it count anything else in steps (a typing goal: − 200타/분 +). */
	let { value = 0, max = 99, min = 0, step = 1, unit = '', name = '', onchange } = $props();
</script>

<div role="group" aria-label={t('play.shop.qty.label', { name })} class="inline-flex h-10 items-center overflow-hidden rounded-xl border-[1.5px] border-(color:--kk-line-strong) bg-(--kk-surface) shadow-[0_2px_0_var(--kk-line-strong)]">
	<button type="button" aria-label={t('play.shop.qty.dec')} disabled={value <= min} onclick={() => onchange?.(Math.max(min, value - step))} class="grid size-10 place-items-center hover:bg-(--kk-surface-2) disabled:opacity-35"><Icon name="remove" size={20} /></button>
	<span class="{unit ? 'min-w-24 px-1' : 'w-10'} text-center text-lg font-extrabold tabular-nums {value ? '' : 'text-(color:--kk-muted)'}" aria-live="polite">{value.toLocaleString()}{#if unit}<small class="ml-0.5 text-xs font-bold text-(color:--kk-muted)">{unit}</small>{/if}</span>
	<button type="button" aria-label={t('play.shop.qty.inc')} disabled={value >= max} onclick={() => onchange?.(Math.min(max, value + step))} class="grid size-10 place-items-center hover:bg-(--kk-surface-2) disabled:opacity-35"><Icon name="add" size={20} /></button>
</div>
