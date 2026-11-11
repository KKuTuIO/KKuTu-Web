<script>
	import { cdnImg } from '../../cdn.js';
	/** The tile roulette's ticket, art in KKuTu-Static like the bonus cards (`img/kkutu/shop/tileToken.png`, source `img/shop/bonus/tileToken.svg`). Square, the card sits in its middle. */
	let { size = 28 } = $props();
</script>

<img src={cdnImg('kkutu/shop/tileToken.png')} alt="" aria-hidden="true" draggable="false" width={size} height={size} style:width="{size}px" style:height="{size}px" class="inline-block shrink-0 object-contain" />
