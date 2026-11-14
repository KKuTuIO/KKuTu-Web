<script>
	import GameIcon from './GameIcon.svelte';

	/**
	 * The price tag inside a buy button, in the one look the gacha, the nickname change, the 캐릭터 합성, the 강화 and the tile roulette share: the currency's
	 * icon and the amount in a darkened pill. It sits inside a `kk-btn kk-btn-primary` (`[ 구매 (P) 100 ]`). A number is written with its thousands
	 * separators, a text as it is. `icon` is a currency icon of GameIcon (핑 by default); `glyph` draws another one; `lacking` reddens the amount when the
	 * player cannot pay it; `title` is the tooltip.
	 * @type {{price:number|string, icon?:string, size?:number, lacking?:boolean, title?:string, glyph?:import('svelte').Snippet}}
	 */
	let { price, icon = 'ping', size = 22, lacking = false, title, glyph } = $props();
</script>

<span {title} class="inline-flex items-center gap-1 rounded-full bg-black/20 py-0.5 pr-3 pl-1.5 text-base font-extrabold tabular-nums {lacking ? 'text-red-200' : ''}">{#if glyph}{@render glyph()}{:else}<GameIcon name={icon} {size} />{/if}{typeof price === 'number' ? price.toLocaleString() : price}</span>
