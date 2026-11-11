<script>
	import { t } from '../i18n.svelte.js';
	import { TILE, TILE_SQUARES } from '../gameTile.js';
	import Icon from './Icon.svelte';
	import Moremi from './Moremi.svelte';

	/**
	 * 아이템전: what plays over the stage when someone uses an item. The same title band as the 해커레미 dialog's hacking (a strip wiping in
	 * from the left over a drifting board-tile pattern, the character popping up from below, a skewing title), but inside the
	 * stage, with the user's own moremi and the item's colour. Decoration only; the parent removes it after DURATION.
	 * @type {{user?:any, item:{icon:string,color:string}, name:string, shop:any, parts:any[]}}
	 */
	let { user, item, name, shop, parts } = $props();
	const uid = $props.id();
	const DURATION = 1900;
	const rnd = (a, b) => a + Math.random() * (b - a);
	// the item's icon bursts out of the character
	const BURST = Array.from({ length: 10 }, () => {
		const a = rnd(-Math.PI * 0.95, -Math.PI * 0.05);
		const v = rnd(90, 240);
		return { dx: Math.cos(a) * v, dy: Math.sin(a) * v * 0.55, rot: rnd(-200, 200), size: Math.round(rnd(18, 30)), delay: rnd(0.25, 0.45) };
	});
</script>

<div class="fx" style:--c={item.color} style:--dur="{DURATION}ms" aria-hidden="true">
	<p class="fire kk-fire-text">{t('play.item.fire')}</p>
	<div class="band">
		<span class="grad absolute inset-0"></span>
		<svg class="tile absolute top-0 left-0 h-full" style="width: calc(100% + {TILE * 2}px)" xmlns="http://www.w3.org/2000/svg">
			<defs>
				<pattern id="fx-tile-{uid}" width={TILE} height={TILE} patternUnits="userSpaceOnUse" patternTransform="scale(2)">
					{#each [-TILE, 0, TILE] as dx}{#each [-TILE, 0, TILE] as dy}{#each TILE_SQUARES as sq}<polygon points={sq.map((q) => `${q[0] + dx},${q[1] + dy}`).join(' ')} fill="#fff" fill-opacity="0.16" />{/each}{/each}{/each}
				</pattern>
			</defs>
			<rect width="100%" height="100%" fill="url(#fx-tile-{uid})" />
		</svg>
		<span class="sheen absolute inset-y-0"></span>
		<div class="stage relative mx-auto h-full max-w-3xl">
			<div class="who absolute bottom-[-3.2rem] left-8 h-40 w-40">
				{#if user}<span class="pop block size-full"><Moremi equip={user.equip} {shop} {parts} size={160} label={user.name} frame={false} /></span>{/if}
				<span class="absolute top-1/3 left-1/2 block size-0">
					{#each BURST as q}<i class="particle absolute block w-max" style="--dx: {q.dx}px; --dy: {q.dy}px; --rot: {q.rot}deg; animation-delay: {q.delay}s"><Icon name={item.icon} size={q.size} fill weight={700} /></i>{/each}
				</span>
			</div>
			<div class="absolute inset-y-0 right-0 left-48 flex items-center justify-center">
				<p class="title kk-pop-font relative flex items-center gap-3 text-4xl tracking-tight">
					<span class="orb"><Icon name={item.icon} size={30} weight={700} /></span>
					<span>{name}</span>
					<i class="spark absolute -top-3 -right-7 text-2xl not-italic">✦</i>
					<i class="spark absolute -bottom-2 -left-5 text-lg not-italic" style="animation-delay: 0.3s">✦</i>
				</p>
			</div>
		</div>
	</div>
</div>

<style>
	/* inside the stage, not over the whole screen: the band is centred in the container it is placed in */
	.fx {
		position: absolute;
		inset-inline: 0;
		top: 50%;
		z-index: 25;
		height: 8rem;
		transform: translateY(-50%);
		pointer-events: none;
	}
	/* 아이템 발동!: above the band, in the shared stage-title lettering (`kk-fire-text`), skewing in and out with the band's own title */
	.fire {
		position: absolute;
		left: 0;
		right: 0;
		bottom: calc(100% + 0.4rem);
		margin: 0;
		text-align: center;
		font-size: 2.1rem;
		animation: fx-title var(--dur) cubic-bezier(0.22, 1, 0.36, 1) 0.05s both;
	}
	.band {
		position: relative;
		height: 100%;
		overflow: hidden;
		color: #fff;
		box-shadow: 0 -2px 0 rgb(0 0 0 / 0.55), 0 2px 0 rgb(0 0 0 / 0.55);
		animation: fx-band var(--dur) cubic-bezier(0.22, 1, 0.36, 1) both;
	}
	.grad {
		background: linear-gradient(90deg, color-mix(in srgb, var(--c) 62%, #000), var(--c) 55%, color-mix(in srgb, var(--c) 72%, #fff));
	}
	.tile {
		animation: fx-drift 4s linear infinite;
		will-change: transform;
	}
	.sheen {
		left: 0;
		width: 22%;
		background: linear-gradient(90deg, transparent, rgb(255 255 255 / 0.4), transparent);
		transform: skewX(-22deg) translateX(-160%);
		animation: fx-sheen 0.9s ease-in-out 0.3s both;
	}
	.who {
		transform-origin: 50% 100%;
	}
	.pop {
		animation: fx-remi 0.5s cubic-bezier(0.34, 1.6, 0.64, 1) 0.2s both;
	}
	.particle {
		color: #fff;
		filter: drop-shadow(0 0 6px rgb(255 255 255 / 0.9));
		animation: fx-burst 1.05s cubic-bezier(0.2, 0.7, 0.3, 1) both;
	}
	.title {
		text-shadow: 0 3px 0 rgb(0 0 0 / 0.28);
		animation: fx-title var(--dur) cubic-bezier(0.22, 1, 0.36, 1) both;
	}
	.orb {
		display: grid;
		place-items: center;
		width: 2.9rem;
		height: 2.9rem;
		border-radius: 50%;
		background: rgb(255 255 255 / 0.22);
		box-shadow: inset 0 0 0 2px rgb(255 255 255 / 0.55), 0 3px 0 rgb(0 0 0 / 0.2);
	}
	.spark {
		color: #fff;
		text-shadow: 0 0 10px rgb(255 255 255 / 0.9);
		animation: fx-twinkle 0.9s ease-in-out infinite;
	}
	@keyframes fx-band {
		0% { clip-path: inset(0 100% 0 0); opacity: 0; }
		20%, 78% { clip-path: inset(0 0 0 0); opacity: 1; }
		100% { clip-path: inset(0 0 0 100%); opacity: 0; }
	}
	@keyframes fx-title {
		0%, 8% { opacity: 0; transform: translateX(-56px) skewX(-24deg); }
		24%, 76% { opacity: 1; transform: none; }
		100% { opacity: 0; transform: translateX(72px) skewX(-24deg); }
	}
	@keyframes fx-drift { to { transform: translate3d(-400px, 0, 0); } }
	@keyframes fx-sheen { from { transform: skewX(-22deg) translateX(-160%); } to { transform: skewX(-22deg) translateX(560%); } }
	@keyframes fx-remi { from { transform: translateY(110%) scale(0.8); opacity: 0; } to { transform: none; opacity: 1; } }
	@keyframes fx-burst {
		0% { opacity: 0; transform: translate(-50%, -50%) scale(0.3) rotate(0); }
		12%, 70% { opacity: 1; }
		100% { opacity: 0; transform: translate(calc(-50% + var(--dx)), calc(-50% + var(--dy) + 36px)) scale(1) rotate(var(--rot)); }
	}
	@keyframes fx-twinkle { 0%, 100% { opacity: 0.2; transform: scale(0.6) rotate(0); } 50% { opacity: 1; transform: scale(1.15) rotate(20deg); } }
	@media (prefers-reduced-motion: reduce) {
		.band, .tile, .sheen, .pop, .particle, .title, .fire, .spark { animation: none; }
		.sheen, .particle { display: none; }
	}
</style>
