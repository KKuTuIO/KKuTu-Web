<script>
	import { BADGE_RATIO } from '../leagueModel.js';

	/**
	 * 명성 등급 뱃지(SVG): 트로피 리그 티어 뱃지와 헷갈리지 않게 색 조합과 가운데 심볼(새싹, 펜촉, 두루마리, 책, 트로피 등)을 따로 정했다.
	 * `id`는 서버의 등급 id(`g1`~`g18`)이고, 없으면(명성이 첫 등급 아래) 등급 없음 뱃지. `size`는 높이(px).
	 * 그림은 클라이언트 번들에 들어 있다(assets/grade: 18개 + 등급 없음). 아이콘 라이브러리를 런타임에 쓰지 않고 완성된 SVG만 쓴다.
	 * 심볼은 Font Awesome Free(CC BY 4.0, https://fontawesome.com/license/free)의 윤곽선을 생성기가 읽어서 그려 넣은 것이다.
	 * @type {{id?: string|null, size?: number, label?: string}}
	 */
	let { id = null, size = 40, label = '' } = $props();
	const urls = import.meta.glob('../assets/grade/*.svg', { eager: true, query: '?url', import: 'default' });
	const src = $derived(urls[`../assets/grade/${id && /^g\d+$/.test(id) ? id : 'none'}.svg`]);
</script>

{#if src}
	<img {src} alt={label} title={label || undefined} height={size} width={Math.round(size * BADGE_RATIO)} draggable="false" class="inline-block shrink-0 select-none" />
{/if}
