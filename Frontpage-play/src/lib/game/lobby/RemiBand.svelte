<script>
	import { TILE, TILE_SQUARES } from '../gameTile.js';

	/**
	 * 모레미 띠: 해커레미(HackerDialog)와 파란 머리 모레미의 장난(RuleReelScene)이 함께 쓰는 풀폭 띠 연출이다. 화면을 가로지르는 띠가 왼쪽에서 닦아
	 * 들어오고(띠와 글자가 함께 잘려서 글자는 늘어나는 너비에 가려진다), 모레미는 띠 아래 가장자리에서 튕기듯 솟아오르며, 제목은 기울어진 채 왼쪽에서
	 * 들어온다. `out`이면 띠가 오른쪽으로 닦여 나가고 제목은 기울어진 채 오른쪽으로 빠진다. 자리는 고정이다: 모레미와 제목은 어느 때든 같은 곳에 있다.
	 * 폰에서는(`max-md`) 모레미를 줄이고 제목 자리를 좁혀 같은 구조를 그대로 작게 쓴다.
	 *
	 * 높이는 바깥이 정한다(이 띠는 `h-full`). 호출하는 쪽이 정하는 것:
	 *  - `gradient`: 띠의 바탕(CSS background 값), `remi`: 모레미 그림 주소, `glyphs`: 글자 비에 떨어지는 글자들, `rain`: 글자 비를 쓰는지
	 *  - `remiClass`: 모레미 칸에 붙일 상태(`jump` 해냈을 때 뛰기, `slump` 풀 죽기)
	 *  - 조각들: `underlay`(바탕 위·무늬 아래에 깔 층, 예: 성공의 금빛), `remiExtras`(모레미 칸 안에 얹을 것, 예: 렌즈의 빛이나 땀), `title`(제목)
	 *  - 제목 요소에는 `remi-text`(들어올 때 0.35초 뒤에 시작) 또는 `remi-text now`(바로 시작) 클래스를 달고, 반짝이는 별에는 `remi-spark`를 단다.
	 * @type {{gradient?:string, out?:boolean, remi:string, glyphs?:string, rain?:boolean, remiClass?:string, underlay?:import('svelte').Snippet, remiExtras?:import('svelte').Snippet, title?:import('svelte').Snippet}}
	 */
	let { gradient = 'linear-gradient(90deg,#4338ca,#7c3aed 55%,#a855f7)', out = false, remi, glyphs = 'ㄱㄴㄷㅏㅓㅗㅜXP0123456789ABCDEF<>/#$', rain = true, remiClass = '', underlay, remiExtras, title } = $props();

	// 모레미 둘레의 글자 비: 열마다 속도가 다르게 떨어진다(한 번 만들면 되고, 무작위처럼만 보이면 된다)
	const rnd = (a, b) => a + Math.random() * (b - a);
	const RAIN = $derived.by(() => {
		const g = [...glyphs];
		return Array.from({ length: 16 }, (_, i) => ({
			x: 2 + i * 6.2 + rnd(-1.5, 1.5),
			text: Array.from({ length: 11 }, () => g[Math.floor(Math.random() * g.length)]).join('\n'),
			dur: rnd(1.1, 2.1),
			delay: -rnd(0, 2),
			size: rnd(0.8, 1.15)
		}));
	});
</script>

<div class="remi-band relative h-full overflow-hidden text-white {out ? 'is-out' : ''}">
	<span class="absolute inset-0" style:background={gradient}></span>
	{@render underlay?.()}
	<svg class="remi-tile absolute top-0 left-0 h-full" style="width: calc(100% + {TILE * 2}px)" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
		<defs>
			<pattern id="remi-tile" width={TILE} height={TILE} patternUnits="userSpaceOnUse" patternTransform="scale(2)">
				{#each [-TILE, 0, TILE] as dx}{#each [-TILE, 0, TILE] as dy}{#each TILE_SQUARES as sq}<polygon points={sq.map((q) => `${q[0] + dx},${q[1] + dy}`).join(' ')} fill="#fff" fill-opacity="0.16" />{/each}{/each}{/each}
			</pattern>
		</defs>
		<rect width="100%" height="100%" fill="url(#remi-tile)" />
	</svg>
	<div class="relative mx-auto h-full max-w-4xl">
		{#if rain}
			<div class="remi-rain absolute bottom-0 -left-8 h-full w-[30rem] max-md:-left-4 max-md:w-[20rem]" aria-hidden="true">
				{#each RAIN as c}
					<span class="remi-rain-col" style="left: {c.x}%; animation-duration: {c.dur}s; animation-delay: {c.delay}s; font-size: {c.size}rem">{c.text}</span>
				{/each}
			</div>
		{/if}
		<div class="remi-wrap absolute bottom-[-4rem] left-8 h-48 w-48 max-md:bottom-[-3.2rem] max-md:left-2 max-md:h-36 max-md:w-36 {remiClass}">
			<img src={remi} alt="" draggable="false" class="remi-img h-full w-full object-contain" />
			{@render remiExtras?.()}
		</div>
		<div class="absolute inset-y-0 right-0 left-60 flex items-center justify-center max-md:left-40 max-md:pr-3">
			{@render title?.()}
		</div>
	</div>
</div>

<style>
	/* 띠는 왼쪽에서 닦아 들어오고, 끝날 때 오른쪽으로 닦여 나간다 */
	.remi-band {
		box-shadow: 0 -2px 0 rgb(0 0 0 / 0.55), 0 2px 0 rgb(0 0 0 / 0.55);
		animation: remi-wipe 0.7s cubic-bezier(0.22, 1, 0.36, 1) both;
	}
	.remi-band.is-out {
		animation: remi-wipe-out 0.42s cubic-bezier(0.64, 0, 0.78, 0) both;
	}
	.remi-tile {
		will-change: transform;
		animation: remi-drift 4s linear infinite;
	}
	/* 글자 비: 열마다 속도가 다르고 머리는 밝고 꼬리는 흐려지며, 모레미 둘레의 부드러운 자리로만 보이게 가린다 */
	.remi-rain {
		-webkit-mask-image: radial-gradient(ellipse 65% 80% at 45% 55%, #000 35%, transparent 100%);
		mask-image: radial-gradient(ellipse 65% 80% at 45% 55%, #000 35%, transparent 100%);
	}
	.remi-rain-col {
		position: absolute;
		top: 0;
		white-space: pre;
		line-height: 1.05;
		font-family: ui-monospace, 'SF Mono', Menlo, monospace;
		font-weight: 800;
		text-align: center;
		color: transparent;
		background: linear-gradient(to bottom, transparent, rgb(255 255 255 / 0.75) 45%, #fff);
		-webkit-background-clip: text;
		background-clip: text;
		will-change: transform;
		animation: remi-rain 1.6s linear infinite;
	}
	.remi-wrap {
		will-change: transform;
		transform-origin: 50% 100%;
	}
	/* 모레미는 띠 아래 가장자리에서 튕기듯 솟아오른다(0.6초 뒤) */
	.remi-img {
		animation: remi-rise 0.55s cubic-bezier(0.34, 1.6, 0.64, 1) 0.6s both;
	}
	/* 해냈을 때: 한 번 크게 뛰고 착지에서 찌그러지며, 더 작게 한 번 더 */
	.remi-wrap.jump {
		animation: remi-jump 0.9s cubic-bezier(0.3, 0, 0.4, 1) both;
	}
	/* 못 해냈을 때: 풀이 죽어 가라앉는다 */
	.remi-wrap.slump {
		animation: remi-slump 0.8s cubic-bezier(0.3, 0, 0.4, 1) both;
	}
	.remi-wrap.slump .remi-img {
		filter: grayscale(0.55) brightness(0.92);
		transition: filter 0.4s;
	}
	/* 제목은 기울어진 채 왼쪽에서 들어오고(0.35초 뒤, `now`는 바로), 나갈 때는 기울어진 채 오른쪽으로 빠진다 */
	.remi-band :global(.remi-text) {
		animation: remi-text-in 0.5s cubic-bezier(0.22, 1, 0.36, 1) 0.35s both;
	}
	.remi-band :global(.remi-text.now) {
		animation: remi-text-in 0.5s cubic-bezier(0.22, 1, 0.36, 1) both;
	}
	.remi-band.is-out :global(.remi-text) {
		animation: remi-text-out 0.38s cubic-bezier(0.64, 0, 0.78, 0) both;
	}
	.remi-band :global(.remi-spark) {
		animation: remi-twinkle 0.9s ease-in-out infinite;
		color: #fff;
		text-shadow: 0 0 10px rgb(255 255 255 / 0.9);
	}
	@keyframes remi-wipe {
		from { clip-path: inset(0 100% 0 0); opacity: 0; }
		to { clip-path: inset(0 0 0 0); opacity: 1; }
	}
	@keyframes remi-wipe-out {
		from { clip-path: inset(0 0 0 0); opacity: 1; }
		to { clip-path: inset(0 0 0 100%); opacity: 0; }
	}
	@keyframes remi-drift { to { transform: translate3d(-400px, 0, 0); } }
	@keyframes remi-rain { from { transform: translate3d(0, -100%, 0); } to { transform: translate3d(0, 11rem, 0); } }
	@keyframes remi-rise {
		from { transform: translateY(110%) scale(0.8); opacity: 0; }
		to { transform: none; opacity: 1; }
	}
	@keyframes remi-jump {
		0% { transform: none; }
		28% { transform: translateY(-38px) scale(0.96, 1.07); }
		50% { transform: translateY(0) scale(1.08, 0.9); }
		68% { transform: translateY(-14px) scale(0.98, 1.03); }
		84% { transform: translateY(0) scale(1.03, 0.97); }
		100% { transform: none; }
	}
	@keyframes remi-slump {
		0% { transform: none; }
		35% { transform: translateY(10px) scale(1.04, 0.93) rotate(-2deg); }
		55% { transform: translateY(6px) scale(1.02, 0.96) rotate(1.5deg); }
		100% { transform: translateY(8px) scale(1.03, 0.94) rotate(-1deg); }
	}
	@keyframes remi-text-in {
		from { opacity: 0; transform: translateX(-56px) skewX(-24deg); }
		to { opacity: 1; transform: none; }
	}
	@keyframes remi-text-out {
		from { opacity: 1; transform: none; }
		to { opacity: 0; transform: translateX(72px) skewX(-24deg); }
	}
	@keyframes remi-twinkle { 0%, 100% { opacity: 0.2; transform: scale(0.6) rotate(0); } 50% { opacity: 1; transform: scale(1.15) rotate(20deg); } }
	@media (prefers-reduced-motion: reduce) {
		.remi-band, .remi-tile, .remi-rain-col, .remi-wrap, .remi-img, .remi-band :global(.remi-text), .remi-band :global(.remi-spark) {
			animation: none;
		}
	}
</style>
