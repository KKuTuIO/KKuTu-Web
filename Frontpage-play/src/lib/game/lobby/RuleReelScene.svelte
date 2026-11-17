<script>
	import { onMount } from 'svelte';
	import { t } from '../i18n.svelte.js';
	import { cdnImg } from '../../cdn.js';
	import { reducedMotion } from '../motion.js';
	import { spinStart, lockSound, soundLand, soundPrank } from '../leagueSound.js';
	import { RULE_LABEL_NAME, REEL_TIMES, REEL_ROUNDS, shuffled } from '../leagueModel.js';
	import Icon from './Icon.svelte';
	import RemiBand from './RemiBand.svelte';

	/**
	 * 규칙 슬롯머신: 독립된 한 장면이다(매칭 장면과 무관). 서버가 이미 정한 규칙(`rules`)을 2×2 슬롯으로 돌려서 한 칸씩 멈춰 보여 준다:
	 * 게임 유형, 특수 규칙, 라운드 시간, 라운드 수. 값은 모르는 채 연출만 하고, 끝나면 `onend`를 부른다.
	 * 소리: 돌아가는 동안 회전음을 되풀이하다가 마지막 슬롯이 멈추면 끄고, 그 '뒤에' 확정음을 낸다. 15%로 걸리는 "파란 머리 모레미의 장난"은
	 * 슬롯 앞에 해커레미 같은 띠로 먼저 나온다.
	 * 모양은 로비의 말(.kk-arcade 배경, .kk-fire-text 제목, 로비 카드의 색 쌍과 아이콘)을 따르고, 멈춘 슬롯은 테마 색이 진해지며 글자가 튀어나오고 빛이 훑는다.
	 * @type {{store:any, rules:{mode:string, picked:string[], pool:string[], fixed:string[], roundTime:number, rounds:number, prank?:boolean}, seed:string, onend?:()=>void}}
	 */
	let { store, rules, seed, onend } = $props();
	const labels = $derived(store.net.meta.labels);
	const calm = reducedMotion();

	const STOP_FIRST = 2000; // ms, 첫 슬롯이 멈추는 때(장난 띠가 끝난 뒤부터 센다)
	const STOP_STEP = 700; // ms, 슬롯 사이 간격
	const LOCK_GAP = 320; // ms, 마지막 슬롯이 멈춘 뒤 확정음까지
	const LOCK_LEN = 1100; // ms, 확정음 길이(lock.mp3)
	const END_GAP = 700; // ms, 확정음이 끝난 뒤 장면을 닫기까지
	const PRANK_MS = 2600; // ms, 장난 띠가 떠 있는 시간
	const PRANK_OUT_MS = 420; // ms, 그중 끝에서 띠가 닦여 나가는 시간(해커레미의 OUT_MS)
	const CELL_MS = 130; // ms, 슬롯이 칸 하나를 지나는 시간(칸 수를 이걸로 정해 속도가 같아 보이게 한다)

	/** 로비 카드의 색 쌍: [진한 색, 밝은 색, 글자 테두리의 짙은 색]. 슬롯마다 한 쌍이라 색만 봐도 무엇인지 안다. */
	const KINDS = [
		{ kind: 'mode', icon: 'sports_esports', pair: ['#64c7c3', '#a2f4f4', '#005f5c'] },
		{ kind: 'rule', icon: 'auto_awesome', pair: ['#f6a65c', '#fee273', '#73420c'] },
		{ kind: 'time', icon: 'timer', pair: ['#7bbde8', '#b6eaff', '#09567c'] },
		{ kind: 'rounds', icon: 'format_list_numbered', pair: ['#fa96c3', '#ffbccb', '#753855'] }
	];

	const modeName = (code) => labels[`game.mode.${code}.name`] ?? code;
	const ruleName = (code) => {
		const n = RULE_LABEL_NAME[code];
		return (n && labels[`game.option.${n}.name`]) || t(`play.league.rule.${code}`);
	};
	const ruleExpl = (code) => {
		const n = RULE_LABEL_NAME[code];
		return (n && labels[`game.option.${n}.expl`]) || t(`play.league.rule.${code}.expl`);
	};

	/** 슬롯 네 개: { title, icon, pair, cells: [{ lines }] (마지막이 결과), stop } */
	const slots = $derived.by(() => {
		const spin = (pool, target, tag, stop) => {
			const filler = shuffled(pool.filter((x) => !target.includes(x)), seed + tag);
			const base = filler.length ? filler : [''];
			const n = Math.max(8, Math.round(stop / CELL_MS));
			const cells = Array.from({ length: n }, (_, k) => ({ lines: [base[k % base.length]] }));
			return [...cells, { lines: target, final: true }];
		};
		const picked = rules.picked ?? [];
		const targets = [
			[modeName(rules.mode)],
			picked.length ? picked.map(ruleName) : [t('play.league.reel.none')],
			[t('play.league.reel.sec', { n: rules.roundTime })],
			[t('play.league.reel.round', { n: rules.rounds })]
		];
		const pools = [
			['KSH', 'ESH', 'KKT', 'KWS', 'EWS'].map(modeName),
			(rules.pool ?? []).map(ruleName),
			REEL_TIMES.map((x) => t('play.league.reel.sec', { n: x })),
			REEL_ROUNDS.map((x) => t('play.league.reel.round', { n: x }))
		];
		const titles = [t('play.league.reel.mode'), t('play.league.reel.rule'), t('play.league.reel.time'), t('play.league.reel.rounds')];
		return KINDS.map((k, i) => {
			const stop = STOP_FIRST + i * STOP_STEP;
			return { ...k, title: titles[i], stop, cells: spin(pools[i], targets[i], k.kind, stop) };
		});
	});
	const fixed = $derived((rules.fixed ?? []).map((c) => (c === 'nob' ? t('play.league.rule.nob') : ruleName(c))));
	const explains = $derived((rules.picked ?? []).map((c) => ({ name: ruleName(c), text: ruleExpl(c) })));

	// ---- 진행: (장난 띠) -> 슬롯 -> 확정음 -> 끝 ----
	let stage = $state('start'); // start | prank | reel
	let landed = $state(0); // 멈춘 슬롯 수
	let prankOut = $state(false); // 장난 띠가 오른쪽으로 닦여 나가는 중
	let timers = [];
	let spin = null; // 회전음의 약속
	const later = (fn, ms) => timers.push(setTimeout(fn, ms));
	const stopSpin = async (fade) => (await spin)?.stop(fade);

	function runReel() {
		stage = 'reel';
		if (calm) {
			landed = slots.length;
			later(lockSound, 150);
			later(() => onend?.(), 150 + LOCK_LEN + END_GAP);
			return;
		}
		spin = spinStart();
		slots.forEach((s, i) => later(() => (soundLand(i), (landed = i + 1)), s.stop));
		const last = slots[slots.length - 1].stop;
		later(() => stopSpin(0.15), last + 30);
		// 확정음은 마지막 슬롯이 멈추고 회전음이 꺼진 '뒤에' 낸다
		later(lockSound, last + LOCK_GAP);
		later(() => onend?.(), last + LOCK_GAP + LOCK_LEN + END_GAP);
	}
	onMount(() => {
		if (rules.prank && !calm) {
			stage = 'prank';
			soundPrank();
			later(() => (prankOut = true), PRANK_MS - PRANK_OUT_MS);
			later(runReel, PRANK_MS);
		} else later(runReel, 250);
		return () => {
			timers.forEach(clearTimeout);
			timers = [];
			stopSpin(0.05);
		};
	});
	const WAVE = $derived([...t('play.league.reel.title')]);
</script>

<!-- 고정 위치는 바깥 래퍼가 맡는다(.kk-arcade가 position: relative라서 같은 요소에 fixed를 주면 덮어써진다) -->
<div class="reel fixed inset-0 z-[3000]" role="dialog" aria-modal="true" aria-label={t('play.league.reel.title')}>
	<div class="kk-arcade h-full w-full overflow-hidden text-white" style="--ac: #3553a0">
		<div class="kk-checker-clip"><span class="kk-checker"></span></div>

		{#if stage === 'prank'}
			<!-- 파란 머리 모레미의 장난: 해커레미와 같은 띠(RemiBand)에 파란 머리 모레미를 얹었다 -->
			<div class="pointer-events-none absolute inset-0 bg-black/35" aria-hidden="true"></div>
			<div class="pointer-events-none absolute inset-x-0 top-1/2 h-44 -translate-y-1/2 max-md:h-40" aria-live="assertive">
				<RemiBand out={prankOut} gradient="linear-gradient(90deg,#1d4ed8,#3b82f6 55%,#38bdf8)" remi={cdnImg('play/league/blue-remi.png')} glyphs="ㄱㄴㄷㅏㅓㅗㅜ규칙장난섞기XP0123456789ABCDEF<>/#$">
					{#snippet remiExtras()}<i class="prank-glint absolute not-italic" aria-hidden="true">✦</i>{/snippet}
					{#snippet title()}
						<p class="remi-text kk-pop-font relative text-center text-5xl tracking-tight text-balance break-keep drop-shadow-[0_3px_0_rgb(0_0_0/0.25)] max-md:text-3xl max-md:leading-tight">
							{t('play.league.prank.title')}
							<i class="remi-spark absolute -top-3 -right-8 text-3xl not-italic max-md:-right-3">✦</i>
							<i class="remi-spark absolute top-8 -right-14 text-xl not-italic max-md:hidden" style="animation-delay: 0.25s">✦</i>
							<i class="remi-spark absolute -bottom-1 -left-8 text-2xl not-italic max-md:-left-3" style="animation-delay: 0.5s">✦</i>
						</p>
					{/snippet}
				</RemiBand>
			</div>
		{:else}
			<div class="stage @container relative mx-auto flex h-full max-w-xl flex-col items-center overflow-y-auto px-4 py-4" style="--c: #7c3aed">
				<div class="my-auto flex w-full flex-col items-center gap-3">
					<!-- 제목: 글자마다 한 박자씩 어긋나게 출렁인다 -->
					<h2 class="title kk-fire-text text-[2.4rem] leading-tight max-md:text-[1.9rem]" aria-label={t('play.league.reel.title')}>
						{#each WAVE as ch, i (i)}<span class="wave" style="animation-delay: {i * 0.07}s" aria-hidden="true">{ch === ' ' ? ' ' : ch}</span>{/each}
					</h2>
					{#if rules.prank}<p class="rounded-full bg-[#2563eb] px-3 py-0.5 text-xs font-black shadow-[0_2px_0_#1e3a8a]">{t('play.league.prank.badge')}</p>{/if}

					<ul class="grid w-full grid-cols-2 gap-x-3 gap-y-2.5">
						{#each slots as s, i (s.kind)}
							<li style="--c1: {s.pair[0]}; --c2: {s.pair[1]}; --ink: {s.pair[2]}">
								<p class="mb-0.5 px-1 text-xs font-black text-white drop-shadow-[0_2px_0_rgb(40_20_90/0.7)]">{s.title}</p>
								<div class="slot {landed > i ? 'hit' : ''}">
									<div class="strip {calm ? 'still' : ''}" style:--n={s.cells.length - 1} style:--dur="{s.stop}ms">
										{#each s.cells as c, k (k)}
											<span class="cell {c.final ? 'final' : ''}">
												<span class="kk-checker-clip"><span class="kk-checker"></span></span>
												{#if c.final}<span class="veil" aria-hidden="true"></span><span class="shine" aria-hidden="true"></span>{/if}
												<Icon name={s.icon} size={34} fill />
												<b class="cell-text kk-pop-font {c.lines.length > 1 ? 'multi' : ''}">{#each c.lines as line (line)}<span>{line}</span>{/each}</b>
											</span>
										{/each}
									</div>
								</div>
							</li>
						{/each}
					</ul>

					{#if fixed.length}
						<p class="flex flex-wrap items-center justify-center gap-1.5 text-sm font-extrabold">
							<span class="text-white/85 drop-shadow-[0_1px_0_rgb(40_20_90/0.7)]">{t('play.league.reel.fixed')}</span>
							{#each fixed as f (f)}<span class="chip">{f}</span>{/each}
						</p>
					{/if}
					<div class="expl-box">
						{#if landed > 1}{#each explains as e (e.name)}<p class="expl"><b>{e.name}</b> {e.text}</p>{/each}{/if}
					</div>
				</div>
			</div>
		{/if}
	</div>
</div>

<style>
	.reel {
		animation: kk-fade-in 0.25s ease-out;
	}
	.title {
		animation: kk-title-in 0.55s cubic-bezier(0.22, 1, 0.36, 1) both;
	}
	.wave {
		display: inline-block;
		animation: reel-wave 1.1s ease-in-out infinite;
	}
	@keyframes reel-wave {
		0%, 100% { transform: translateY(0); }
		50% { transform: translateY(-0.28em); }
	}
	/* 한 슬롯: 칸 하나가 곧 컬러 카드다(아이콘 위, 글자 아래). 위로 흘러가다 마지막 칸(결과)에서 느려지며 멈춘다. */
	.slot {
		--h: 7.6rem;
		height: var(--h);
		overflow: hidden;
		border-radius: 20px;
		box-shadow: 0 5px 0 rgb(40 20 90 / 0.42), 0 10px 18px rgb(30 20 80 / 0.28);
	}
	.slot.hit {
		animation: reel-hit 0.42s cubic-bezier(0.3, 1.5, 0.5, 1);
	}
	.strip {
		display: flex;
		flex-direction: column;
		animation: reel-spin var(--dur) cubic-bezier(0.1, 0.72, 0.14, 1) both;
	}
	.strip.still {
		animation: none;
		transform: translateY(calc(var(--n) * var(--h) * -1));
	}
	@keyframes reel-spin {
		from { transform: translateY(0); }
		to { transform: translateY(calc(var(--n) * var(--h) * -1)); }
	}
	@keyframes reel-hit {
		0% { transform: scale(1, 1); }
		35% { transform: scale(1.05, 0.93); }
		65% { transform: scale(0.99, 1.04); }
		100% { transform: scale(1, 1); }
	}
	.cell {
		position: relative;
		isolation: isolate;
		flex: none;
		display: flex;
		flex-direction: column;
		align-items: center;
		justify-content: center;
		gap: 0.15rem;
		height: var(--h);
		/* 도는 동안의 칸은 옅은 색: 멈춘 칸만 진해지므로 '확정'이 눈에 보인다 */
		background: linear-gradient(to bottom, color-mix(in srgb, var(--c1) 38%, #fff), color-mix(in srgb, var(--c2) 55%, #fff));
		overflow: hidden;
	}
	.cell .kk-checker-clip {
		position: absolute;
		inset: 0;
		z-index: -1;
		overflow: hidden;
	}
	/* 결과 칸: 진한 테마 색 위에 옅은 막을 덮어 두었다가, 멈추면 막이 걷히며 색이 진해진다 */
	.cell.final {
		background: linear-gradient(to bottom, var(--c1), var(--c2));
	}
	.veil {
		position: absolute;
		inset: 0;
		z-index: -1;
		background: linear-gradient(to bottom, color-mix(in srgb, var(--c1) 38%, #fff), color-mix(in srgb, var(--c2) 55%, #fff));
		transition: opacity 0.5s ease-out;
	}
	.slot.hit .veil {
		opacity: 0;
	}
	/* 빛이 한 번 훑고 지나간다 */
	.shine {
		position: absolute;
		inset: -20% auto -20% 0;
		width: 45%;
		z-index: 3;
		background: linear-gradient(100deg, transparent, rgb(255 255 255 / 0.85), transparent);
		transform: translateX(-130%) skewX(-18deg);
		opacity: 0;
		pointer-events: none;
	}
	.slot.hit .shine {
		animation: reel-shine 0.85s ease-out 0.05s both;
	}
	@keyframes reel-shine {
		0% { transform: translateX(-130%) skewX(-18deg); opacity: 0.95; }
		100% { transform: translateX(330%) skewX(-18deg); opacity: 0.95; }
	}
	/* 로비 카드의 글자와 아이콘: 흰 글자에 그 색의 짙은 테두리와 단단한 그림자 */
	.cell :global(.material-symbols-outlined) {
		color: #fff;
		-webkit-text-stroke: 5px var(--ink);
		paint-order: stroke fill;
		filter: drop-shadow(0 2px 0 var(--ink));
	}
	.cell-text {
		display: flex;
		flex-direction: column;
		align-items: center;
		font-weight: 400;
		font-size: clamp(1.05rem, 4.7vw, 1.55rem);
		line-height: 1.15;
		text-align: center;
		letter-spacing: 0.01em;
		color: #fff;
		-webkit-text-stroke: 5px var(--ink);
		paint-order: stroke fill;
		text-shadow: 0 3px 0 var(--ink);
	}
	.cell-text.multi {
		font-size: clamp(0.9rem, 3.6vw, 1.2rem);
		-webkit-text-stroke-width: 4px;
	}
	/* 멈추면 글자가 튀어나온다 */
	.slot.hit .final .cell-text,
	.slot.hit .final :global(.material-symbols-outlined) {
		animation: reel-pop 0.5s cubic-bezier(0.2, 1.6, 0.4, 1) both;
	}
	@keyframes reel-pop {
		0% { transform: scale(0.55); }
		60% { transform: scale(1.22); }
		100% { transform: scale(1); }
	}
	.expl-box {
		min-height: 3.2rem;
		display: flex;
		flex-direction: column;
		gap: 0.15rem;
	}
	.expl {
		margin: 0;
		padding: 0 0.4rem;
		text-align: center;
		font-size: 0.82rem;
		font-weight: 800;
		color: #fff;
		text-shadow: 0 1px 0 rgb(40 20 90 / 0.8), 0 0 8px rgb(40 20 90 / 0.5);
		animation: kk-fade-in 0.3s ease-out;
	}
	.expl b {
		font-weight: 900;
		color: #fde68a;
	}
	.chip {
		border-radius: 999px;
		background: #fff;
		color: #4b3a8f;
		padding: 0.1rem 0.75rem;
		box-shadow: 0 2px 0 rgb(40 20 90 / 0.4);
	}
	/* ---- 파란 머리 모레미의 장난: 띠는 RemiBand, 여기에는 파란 머리 모레미의 머리 모서리에서 반짝이는 별만 있다 ---- */
	.prank-glint {
		left: 74%;
		top: 14%;
		font-size: 1.7rem;
		line-height: 1;
		color: #fff;
		text-shadow: 0 0 8px #fff, 0 0 16px rgb(190 220 255 / 0.95);
		transform: translate(-50%, -50%) scale(0);
		animation: prank-glint 1.3s ease-in-out infinite;
		animation-delay: 0.9s;
	}
	@keyframes prank-glint {
		0%, 25% { opacity: 0; transform: translate(-50%, -50%) scale(0); }
		45% { opacity: 1; transform: translate(-50%, -50%) scale(1.15) rotate(25deg); }
		65%, 100% { opacity: 0; transform: translate(-50%, -50%) scale(0.2) rotate(70deg); }
	}
	@media (prefers-reduced-motion: reduce) {
		.strip { animation: none; transform: translateY(calc(var(--n) * var(--h) * -1)); }
		.slot.hit, .slot.hit .final .cell-text, .slot.hit .final :global(.material-symbols-outlined), .shine, .wave, .title, .prank-glint { animation: none; }
	}
</style>
