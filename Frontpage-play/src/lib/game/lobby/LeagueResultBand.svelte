<script>
	import { untrack } from 'svelte';
	import { t } from '../i18n.svelte.js';
	import { league } from '../leagueStore.svelte.js';
	import { tierIdOf, tierAccent } from '../leagueModel.js';
	import { reducedMotion } from '../motion.js';
	import { playSound } from '../audio.svelte.js';
	import { TILE, TILE_SQUARES } from '../gameTile.js';
	import Icon from './Icon.svelte';
	import TierBadge from './TierBadge.svelte';
	import PlacementMedal from './PlacementMedal.svelte';

	/**
	 * 판이 끝난 뒤 트로피 결과를 풀 width 띠로 보여 준다. 우리 모레미 띠(RemiBand)와 같은 말을 쓴다: 한 겹의 띠가 왼쪽에서 닦아 들어오고, 위에 체커 무늬가 흐르며,
	 * 글자는 `.kk-fire-text`다. 띠 색은 올라간(또는 머문) 티어 계열 색이라 어느 티어인지 색으로 알 수 있다.
	 * 왼쪽에 티어 뱃지, 가운데에 제목(승급!/강등/결과)과 그 아래 받은(잃은) 트로피 칩과 지금 트로피, 오른쪽에 결과 화면과 같은 등수 메달.
	 * 티어가 바뀌었으면 뱃지가 이전 티어에서 새 티어로 크게 튀어 바뀌고, 숫자는 판 전 값에서 판 후 값으로 올라간다(줄어든다).
	 * 결과 화면이 열린 뒤(판이 끝나고 약 2초 뒤) 그 위로 들어오고, 잠시 머문 뒤 오른쪽으로 닦여 나간다(누르면 바로 나간다).
	 * 값은 서버의 `leagueResult`가 정했고 여기서는 보여 주기만 한다(`league.result`).
	 * @type {{store:any}}
	 */
	let { store } = $props();
	let winW = $state(typeof innerWidth === 'number' ? innerWidth : 1280);
	const phone = $derived(winW < 768);
	const r = $derived(league.result);
	const calm = reducedMotion();

	const OPEN_AFTER = 2800; // ms, 결과 패킷이 온 뒤 결과 화면(판이 끝나고 약 2초 뒤에 열린다)이 열리길 기다리는 시간. 띠가 그 위로 와야 해서 늦게 띄운다.
	const COUNT_AT = 900; // ms, 띠가 들어온 뒤 숫자가 올라가기 시작하는 때
	const COUNT_MS = 1000;
	const SWAP_AT = 1700; // ms, 티어 뱃지가 바뀌는 때
	const HOLD_MS = 4800; // ms, 띠가 들어온 뒤 나가기 시작하기까지
	const OUT_MS = 420;

	let stage = $state('off'); // off | in | out
	let host = $state();
	let shownDelta = $state(0);
	let shownTotal = $state(0);
	let badgeIndex = $state(0);
	let timers = [];
	let raf = 0;
	const later = (fn, ms) => timers.push(setTimeout(fn, ms));
	const stop = () => (timers.forEach(clearTimeout), (timers = []), cancelAnimationFrame(raf));

	const moved = $derived(r ? (r.tier > r.tierBefore ? 'up' : r.tier < r.tierBefore ? 'down' : 'same') : 'same');
	const gain = $derived((r?.delta ?? 0) >= 0);
	const accent = $derived(tierAccent(tierIdOf(r?.tier ?? 0)));

	function count() {
		if (!r) return;
		const from = r.before, to = r.trophies;
		if (calm) return void ((shownTotal = to), (shownDelta = r.delta));
		const t0 = performance.now();
		const tick = (now) => {
			const k = Math.min(1, (now - t0) / COUNT_MS);
			const e = 1 - Math.pow(1 - k, 3);
			shownTotal = Math.round(from + (to - from) * e);
			shownDelta = Math.round(r.delta * e);
			if (k < 1) raf = requestAnimationFrame(tick);
		};
		raf = requestAnimationFrame(tick);
	}
	function dismiss() {
		if (stage !== 'in') return;
		stage = 'out';
		later(() => {
			stage = 'off';
			league.result = null;
		}, calm ? 0 : OUT_MS);
	}
	// 결과 화면은 브라우저의 최상위 레이어(dialog)라서 일반 z-index로는 그 위에 올 수 없다. 띠는 popover(최상위 레이어)이고, 나중에 열렸으므로 결과 화면 위에 놓인다.
	$effect(() => {
		if (stage !== 'off' && host && !host.matches(':popover-open')) host.showPopover();
	});
	$effect(() => {
		const res = r;
		return untrack(() => {
			stop();
			if (!res) return void (stage = 'off');
			shownDelta = calm ? res.delta : 0;
			shownTotal = calm ? res.trophies : res.before;
			badgeIndex = res.tierBefore;
			later(() => {
				stage = 'in';
				playSound(moved === 'up' ? 'lvup' : gain && res.delta > 0 ? 'success' : 'fail', { ui: true });
				later(count, calm ? 0 : COUNT_AT);
				later(() => (badgeIndex = res.tier), calm || moved === 'same' ? 0 : SWAP_AT);
				later(dismiss, HOLD_MS);
			}, calm ? 200 : OPEN_AFTER);
			return stop;
		});
	});
	// 숫자가 길어질수록(최고 티어는 네다섯 자리) 글자를 줄여서 띠 폭 안에 넣는다. 자릿수는 끝값 기준이라 올라가는 도중에 크기가 흔들리지 않는다.
	const digits = $derived(String(Math.max(Math.abs(r?.trophies ?? 0), Math.abs(r?.before ?? 0))).length);
	const deltaDigits = $derived(String(Math.abs(r?.delta ?? 0)).length);
	const fit = (n) => (n <= 3 ? 1 : n === 4 ? 0.86 : n === 5 ? 0.72 : 0.6);
	const totalSize = $derived((phone ? 2.25 : 3.6) * fit(digits));
	const chipSize = $derived((phone ? 1 : 1.5) * fit(deltaDigits + 1));
	const title = $derived(moved === 'up' ? t('play.league.result.promote') : moved === 'down' ? t('play.league.result.demote') : t('play.league.result.title'));
</script>

<svelte:window bind:innerWidth={winW} />

{#if r && stage !== 'off'}
	<!-- 한 번 누르면 바로 나간다. 띠 밖의 클릭(결과 화면의 버튼 등)은 막지 않는다. -->
	<div bind:this={host} popover="manual" class="lrb pointer-events-none fixed inset-x-0 top-1/2 bottom-auto m-0 h-44 w-screen max-w-none -translate-y-1/2 overflow-visible border-0 bg-transparent p-0 max-md:h-40" role="status" aria-live="polite" style="--a1: {accent[0]}; --a2: {accent[1]}; --a3: {accent[2]}; --c: {accent[0]}">
		<button type="button" class="lrb-band pointer-events-auto relative block size-full cursor-pointer overflow-hidden text-white {stage === 'out' ? 'is-out' : ''}" onclick={dismiss} aria-label={title}>
			<span class="absolute inset-0 bg-[linear-gradient(90deg,var(--a1),var(--a2)_55%,var(--a3))]"></span>
			<svg class="lrb-tile absolute top-0 left-0 h-full" style="width: calc(100% + {TILE * 2}px)" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
				<defs>
					<pattern id="lrb-tile" width={TILE} height={TILE} patternUnits="userSpaceOnUse" patternTransform="scale(2)">
						{#each [-TILE, 0, TILE] as dx}{#each [-TILE, 0, TILE] as dy}{#each TILE_SQUARES as sq}<polygon points={sq.map((q) => `${q[0] + dx},${q[1] + dy}`).join(' ')} fill="#fff" fill-opacity="0.16" />{/each}{/each}{/each}
					</pattern>
				</defs>
				<rect width="100%" height="100%" fill="url(#lrb-tile)" />
			</svg>
			<span class="relative mx-auto flex h-full max-w-4xl min-w-0 items-center justify-center gap-10 px-6 max-md:gap-3 max-md:px-3">
				{#key badgeIndex}<span class="lrb-badge shrink-0 {moved !== 'same' && badgeIndex === r.tier ? 'swap' : ''}"><TierBadge tier={tierIdOf(badgeIndex)} size={phone ? 64 : 104} /></span>{/key}
				<span class="flex min-w-0 shrink flex-col items-start gap-1.5">
					<span class="lrb-title kk-fire-text text-[2.2rem] leading-none whitespace-nowrap max-md:text-2xl">{title}{#if moved !== 'same'}<i class="lrb-spark absolute -top-2 -right-9 text-3xl not-italic max-md:-right-6 max-md:text-xl" aria-hidden="true">✦</i>{/if}</span>
					<span class="flex items-center gap-4 max-md:gap-2.5">
						<span class="lrb-chip inline-flex items-center gap-1 rounded-full bg-white px-3.5 py-1 font-black tabular-nums whitespace-nowrap max-md:px-2.5 {gain ? 'gain' : 'loss'}" style:font-size="{chipSize}rem"><Icon name="emoji_events" size={22} fill class="max-md:!text-[17px]" />{shownDelta > 0 ? '+' : ''}{shownDelta}</span>
						<b class="lrb-total kk-fire-text leading-none tabular-nums whitespace-nowrap" style:font-size="{totalSize}rem">{shownTotal.toLocaleString()}</b>
					</span>
				</span>
				<span class="lrb-medal flex shrink-0 flex-col items-center gap-1">
					<PlacementMedal placement={r.placement + 1} size={phone ? 54 : 84} />
					{#if r.size}<span class="rounded-full bg-white/90 px-2.5 py-0.5 text-xs font-extrabold text-(color:--c)">{t('play.league.result.of', { size: r.size })}</span>{/if}
				</span>
			</span>
		</button>
	</div>
{/if}

<style>
	/* 띠는 왼쪽에서 닦아 들어오고, 끝날 때 오른쪽으로 닦여 나간다(모레미 띠와 같은 움직임) */
	.lrb-band {
		box-shadow: 0 -2px 0 rgb(0 0 0 / 0.55), 0 2px 0 rgb(0 0 0 / 0.55);
		animation: lrb-wipe 0.7s cubic-bezier(0.22, 1, 0.36, 1) both;
	}
	.lrb-band.is-out {
		animation: lrb-wipe-out 0.42s cubic-bezier(0.64, 0, 0.78, 0) both;
	}
	.lrb-tile {
		will-change: transform;
		animation: lrb-drift 4s linear infinite;
	}
	.lrb-title {
		position: relative;
	}
	.lrb-spark {
		color: #fff;
		text-shadow: 0 0 10px rgb(255 255 255 / 0.9);
		animation: lrb-twinkle 0.9s ease-in-out infinite;
	}
	/* 받은 트로피 칩: 우리 칩처럼 흰 바탕에 단단한 그림자 */
	.lrb-chip {
		box-shadow: 0 3px 0 rgb(0 0 0 / 0.28);
		animation: lrb-pop 0.5s cubic-bezier(0.2, 1.6, 0.4, 1) 0.7s both;
	}
	.lrb-chip.gain {
		color: #0b8a5e;
	}
	.lrb-chip.loss {
		color: #cf3f4d;
	}
	.lrb-total {
		animation: lrb-pop 0.5s cubic-bezier(0.2, 1.6, 0.4, 1) 0.85s both;
	}
	.lrb-medal {
		animation: lrb-pop 0.55s cubic-bezier(0.2, 1.6, 0.4, 1) 1s both;
	}
	.lrb-badge {
		display: inline-block;
		filter: drop-shadow(0 4px 0 rgb(0 0 0 / 0.3));
		animation: lrb-pop 0.55s cubic-bezier(0.2, 1.6, 0.4, 1) 0.45s both;
	}
	/* 티어가 바뀌면 새 뱃지가 크게 튀며 빛난다 */
	.lrb-badge.swap {
		animation: lrb-swap 0.8s cubic-bezier(0.2, 1.6, 0.4, 1) both;
	}
	@keyframes lrb-wipe {
		from { clip-path: inset(0 100% 0 0); opacity: 0; }
		to { clip-path: inset(0 0 0 0); opacity: 1; }
	}
	@keyframes lrb-wipe-out {
		from { clip-path: inset(0 0 0 0); opacity: 1; }
		to { clip-path: inset(0 0 0 100%); opacity: 0; }
	}
	@keyframes lrb-drift { to { transform: translate3d(-400px, 0, 0); } }
	@keyframes lrb-pop {
		0% { transform: scale(0.5); opacity: 0; }
		60% { transform: scale(1.18); opacity: 1; }
		100% { transform: scale(1); opacity: 1; }
	}
	@keyframes lrb-swap {
		0% { transform: scale(0.3) rotate(-12deg); opacity: 0; filter: drop-shadow(0 0 0 rgb(255 255 255 / 0)); }
		55% { transform: scale(1.35) rotate(4deg); opacity: 1; filter: drop-shadow(0 0 26px rgb(255 255 255 / 0.95)); }
		100% { transform: scale(1) rotate(0); opacity: 1; filter: drop-shadow(0 4px 0 rgb(0 0 0 / 0.3)); }
	}
	@keyframes lrb-twinkle { 0%, 100% { opacity: 0.3; transform: scale(0.6) rotate(0); } 50% { opacity: 1; transform: scale(1.15) rotate(20deg); } }
	@media (prefers-reduced-motion: reduce) {
		.lrb-band, .lrb-tile, .lrb-spark, .lrb-chip, .lrb-total, .lrb-medal, .lrb-badge, .lrb-badge.swap {
			animation: none;
		}
	}
</style>
