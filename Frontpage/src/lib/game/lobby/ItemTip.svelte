<script>
	import { t } from '../i18n.svelte.js';
	import { plainText } from '../protocol.js';
	import { optionRows, itemName } from '../shop.js';
	import Rich from '../ui/Rich.svelte';

	/**
	 * The tooltip of an item (legacy `explainGoods`): name, group, description, term, options; in the shop its cost, in my box (`own`)
	 * whether it is worn and when a time-limited one ends.
	 * @type {{item:any, details:Record<string,any>, own?:{expire?:number, equipped?:boolean}|null}}
	 */
	let { item, details, own = null } = $props();
	const rows = $derived(optionRows(item.options, (k) => t(`kkutu.js.opts.${k}`)));
</script>

<div class="flex max-w-64 flex-col gap-1 text-left">
	<b>{itemName(details, item._id)}{#if own?.equipped}<span class="ml-1 font-extrabold text-(--kk-accent)">{plainText(t('kkutu.js.equipped')).replace(/[()]/g, '').trim()}</span>{/if}</b>
	<span class="text-xs opacity-80">{t(`item.group.${item.group}`)}</span>
	<span class="text-xs"><Rich html={details?.[item._id]?.detail ?? t('kkutu.js.invalidItem.expl')} /></span>
	{#if item.term}<span class="text-xs">{Math.floor(item.term / 86400)}{t('kkutu.js.day')} {t('kkutu.js.itemTerm')}</span>{/if}
	{#if rows.length}
		<div class="flex flex-col text-xs">
			{#each rows as r}
				{#if r.text !== undefined}<span style:color={r.color}>{r.text}</span>
				{:else}<span class="flex justify-between gap-3"><span class="opacity-80">{r.label}</span><span>{r.value}</span></span>{/if}
			{/each}
		</div>
	{/if}
	{#if own?.expire}<span class="text-xs">{new Date(own.expire * 1000).toLocaleString()}{t('kkutu.js.itemTermed')}</span>{/if}
	{#if !own}
		<div class="flex flex-col text-xs">
			<span>{t('kkutu.js.exts.shop')}</span>
			<span>{t('kkutu.js.exts.cost')}</span>
			<span class="flex justify-between gap-3 pl-2"><span class="opacity-80">{t('kkutu.js.ping')}</span><span>{item.cost}</span></span>
		</div>
	{/if}
</div>
