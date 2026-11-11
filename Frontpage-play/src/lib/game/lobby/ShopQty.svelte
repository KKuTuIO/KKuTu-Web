<script>
	import { t } from '../i18n.svelte.js';
	import Icon from './Icon.svelte';
	/** − 0 + : how many of an item to buy. */
	let { value = 0, max = 99, name = '', onchange } = $props();
</script>

<div role="group" aria-label={t('play.shop.qty.label', { name })} class="inline-flex h-10 items-center overflow-hidden rounded-xl border-[1.5px] border-(color:--kk-line-strong) bg-(--kk-surface) shadow-[0_2px_0_var(--kk-line-strong)]">
	<button type="button" aria-label={t('play.shop.qty.dec')} disabled={value <= 0} onclick={() => onchange?.(value - 1)} class="grid size-10 place-items-center hover:bg-(--kk-surface-2) disabled:opacity-35"><Icon name="remove" size={20} /></button>
	<span class="w-10 text-center text-lg font-extrabold tabular-nums {value ? '' : 'text-(color:--kk-muted)'}" aria-live="polite">{value}</span>
	<button type="button" aria-label={t('play.shop.qty.inc')} disabled={value >= max} onclick={() => onchange?.(value + 1)} class="grid size-10 place-items-center hover:bg-(--kk-surface-2) disabled:opacity-35"><Icon name="add" size={20} /></button>
</div>
