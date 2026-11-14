<script>
	import GameIcon from './GameIcon.svelte';

	/**
	 * The price tag inside a buy button, in the one look the gacha, the nickname change, the 캐릭터 합성 and the 강화 all share: the 핑 icon and the amount in a
	 * darkened pill. It sits inside a `kk-btn kk-btn-primary` (`[ 구매 (P) 100 ]`); `lacking` reddens the amount when the player cannot pay it.
	 * @type {{price:number|string, size?:number, lacking?:boolean}}
	 */
	let { price, size = 22, lacking = false } = $props();
</script>

<span class="inline-flex items-center gap-1 rounded-full bg-black/20 py-0.5 pr-3 pl-1.5 text-base font-extrabold tabular-nums {lacking ? 'text-red-200' : ''}"><GameIcon name="ping" {size} />{Number(price).toLocaleString()}</span>
