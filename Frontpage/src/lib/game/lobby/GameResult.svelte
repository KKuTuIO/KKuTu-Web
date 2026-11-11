<script>
	import { t } from '../i18n.svelte.js';
	import Dialog from './Dialog.svelte';
	import { EXP, getLevel } from '../level.js';
	import { ms } from '../motion.js';
	import { playResultBgm, playSound } from '../audio.svelte.js';
	import { crossedLevels } from '../levelUp.js';
	import { levelUp } from '../levelUp.svelte.js';
	import { buildResultView } from '../resultModel.js';
	import DataTable from '../ui/DataTable.svelte';
	import ChatPanel from './ChatPanel.svelte';
	import GameIcon from './GameIcon.svelte';
	import Icon from './Icon.svelte';
	import LevelIcon from './LevelIcon.svelte';
	import Moremi from './Moremi.svelte';
	import NickName from './NickName.svelte';
	import PlacementMedal from './PlacementMedal.svelte';
	import Sparkles from './Sparkles.svelte';
	import { loadGoodDetails } from '../shop.js';

	/**
	 * The result screen (`roundEnd`). My own score is the screen: it is drawn in the digit cells of the game's scoreboard and lit in my
	 * team's colour. A game with teams has a bar across the top, cut into the teams' shares of all the points in rank order; two players
	 * face each other with the gap drawn between them; more than two, with no teams, get the list. My best word and the words I played
	 * stand in for a stats list; the best move is the dead-end word with the most points. The screen is split: on the left the game and my
	 * character with my score, on the right one panel with the ranking, the rewards and the buttons. It opens two seconds after the game
	 * ends, over the game, which stays visible through a blur and a fade to black; closing it takes me back to the waiting room. It is
	 * made of the client's own pieces (the card, the buttons, the XP and ping labels, the tokens), not its own look.
	 */
	let { store } = $props();
	// `store.resultSay` (the tutorial's practice game): {playerId: locale key}, a line a player says over their place on the podium
	const net = $derived(store.net);
	const game = $derived(store.game);
	const result = $derived(game?.result);
	const parts = $derived(net.meta.moremiParts);
	const view = $derived(result ? buildResultView({ rows: result.rows, users: net.users, played: game?.played ?? [], meId: net.id, quiz: { solved: game?.solved, kpm: game?.kpm } }) : null);
	const mine = $derived(view?.mine ?? null);
	const commify = (n) => Number(n).toLocaleString('en-US');

	let el = $state();
	let open = $state(false);
	let wasOpen = false;
	let skipped = $state(0);
	/** The flow: the full ranking, then (다음) my own result. */
	let step = $state('full');
	let leaving = $state(false); // the podium and the table slide out before my own result slides in
	let grown = $state(false); // the team bar and the gap bar grow from nothing once the screen is up
	$effect(() => {
		if (!result) {
			open = false;
			wasOpen = false;
			return;
		}
		const timer = setTimeout(() => (open = true), ms(2000));
		return () => clearTimeout(timer);
	});
	$effect(() => {
		if (!el) return;
		if (open && !el.open) {
			playSound('dialog', { ui: true });
			playResultBgm();
			skipped = 0;
			grown = false;
			step = 'full';
			leaving = false;
			sayIdx = Math.floor(Math.random() * SAYINGS);
			el.showModal();
			el.focus();
			requestAnimationFrame(() => requestAnimationFrame(() => (grown = true)));
		} else if (!open && el.open) el.close();
	});
	$effect(() => {
		if (open) wasOpen = true;
		else if (wasOpen) {
			wasOpen = false;
			store.closeResult();
		}
	});
	/** A tap, a click or Space on the screen itself shows the end of the counting at once. */
	function skip() {
		skipped++;
		grown = true;
	}
	function next() {
		if (leaving) return;
		leaving = true;
		setTimeout(() => {
			step = 'mine';
			leaving = false;
		}, ms(480));
	}
	function key(e) {
		if (!open || levelUp.busy || (e.key !== ' ' && e.key !== 'Enter') || e.target instanceof HTMLButtonElement || e.target instanceof HTMLInputElement || e.target instanceof HTMLTextAreaElement) return;
		if (e.key === ' ') {
			e.preventDefault();
			skip();
		} else if (!e.repeat) {
			e.preventDefault();
			if (step === 'full') next();
			else open = false;
		}
	}

	const gaugeOn = $derived(step === 'mine');
	// ---- the gauge: legacy roundEndAnimation, step for step ----
	let bars = $state({ before: 0, current: 0, bonus: 0 });
	let shownLevel = $state(1);
	let scoreText = $state('');

	// ---- level-ups: the gauge stops at each new level while LevelUpHost shows its popup (what each level gave: the server's) ----
	const giftOf = (level) => mine?.reward.levelGifts?.find((g) => g.level === level) ?? null;

	$effect(() => {
		if (!open || !mine || !gaugeOn) return;
		const r = mine.reward;
		const s = { exp: mine.before, level: getLevel(mine.before), left: r.exp, _exp: mine.before, _boing: r.baseExp, coef: 0, done: false };
		const sess = levelUp.session(crossedLevels(mine.before, mine.after), giftOf);
		let alive = true;
		let raf;
		let settle;
		const draw = (val, before, goal) => `${((val - before) / (goal - before)) * 100}%`;
		const step = () => {
			const goal = EXP[s.level - 1];
			const before = EXP[s.level - 2] || 0;
			let paused = false;
			if (s.left > 0) {
				let v = s.left * s.coef;
				if (v < 0.05 && s.coef) v = s.left;
				if (v > 0) {
					s.left -= v;
					s.exp += v;
					const nl = getLevel(s.exp);
					if (s.level !== nl) {
						s._boing -= goal - s._exp;
						s._exp = goal;
						for (let l = s.level + 1; l <= nl; l++) sess.reach(l);
						paused = levelUp.busy;
					}
					s.level = nl;
				}
				if (paused) levelUp.whenIdle(() => alive && (raf = requestAnimationFrame(step)));
				else raf = requestAnimationFrame(step);
			} else if (!s.done) {
				s.done = true;
				sess.end(); // whatever had no popup of its own comes as one summary once the gauge is full
			}
			const going = s.exp - s._exp;
			bars = { before: draw(s._exp, before, goal), current: draw(Math.min(going, s._boing), 0, goal - before), bonus: draw(Math.max(0, going - s._boing), 0, goal - before) };
			shownLevel = s.level;
			scoreText = `${commify(Math.round(s.exp))} / ${commify(goal)}`;
		};
		step();
		// the screen is up for half a second before the gauge starts to move
		settle = setTimeout(() => (s.coef = 0.05), ms(500));
		return () => {
			alive = false;
			cancelAnimationFrame(raf);
			clearTimeout(settle);
		};
	});

	// ---- what gave the experience and the ping: the base, then each bonus with its source (an item, the 오끄감 buff) ----
	let details = $state({}); // item names by id, once they are fetched
	let pinned = $state(null); // the (i) whose breakdown is open
	$effect(() => {
		if (open) loadGoodDetails().then((d) => (details = d ?? {}));
		else pinned = null;
	});
	const isOpen = (key) => pinned === key;
	/** A press anywhere but on a breakdown or its (i) closes the open one. */
	function away(e) {
		if (pinned && !e.target.closest?.('.breakdown, .info')) pinned = null;
	}
	/** Log entries are "<from><g|h><EXP|MNY><value>" (from q: an equipped item, e: the 강화 effect of one, k: the 오끄감 buff; g: a share of the base, h: a flat amount); `sources[i]` is the item behind entry i. */
	function sourcesOf(r, target) {
		const base = target === 'EXP' ? r.baseExp : r.baseMoney;
		const rows = [];
		(r.log ?? []).forEach((entry, i) => {
			if (entry.slice(2, 5) !== target) return;
			const value = Number(entry.slice(5));
			const flat = entry.charAt(1) === 'h';
			const item = r.sources?.[i] || '';
			const named = item && details[item]?.name;
			rows.push({
				label: named || t(`kkutu.js.bonusFrom.${entry.charAt(0)}`),
				note: named ? t(`kkutu.js.bonusFrom.${entry.charAt(0) === 'e' ? 'e' : 'q'}`) : '',
				amount: flat ? `+${Math.floor(value)}` : `+${(base * value).toFixed(1)}`,
				percent: flat ? '' : `+${Math.round(value * 1000) / 10}%`
			});
		});
		return { base, rows };
	}
	const rewardRowsRaw = $derived(
		mine
			? [
					{ key: 'exp', icon: 'xp', color: 'var(--cur-xp)', label: t('play.me.xp'), value: mine.reward.exp, baseLabel: t('kkutu.js.scoreOrigin'), ...sourcesOf(mine.reward, 'EXP') },
					{ key: 'money', icon: 'ping', color: 'var(--cur-ping)', label: t('play.currency.ping'), value: mine.reward.money, baseLabel: t('kkutu.js.moneyOrigin'), ...sourcesOf(mine.reward, 'MNY') }
				]
			: []
	);
	const rewardRows = $derived(rewardRowsRaw.map((r) => ({ ...r, pct: r.base > 0 ? Math.round(((r.value - r.base) / r.base) * 100) : 0 })));
	/** The company on the scene: my team mates. */
	const mates = $derived.by(() => {
		if (!view) return [];
		if (view.layout !== 'teams' || !view.myTeam) return []; // without teams the others are opponents, not company
		return view.players.filter((p) => !p.me && p.user && p.team === view.myTeam.team).slice(0, 4);
	});
	/** The line-up, bent into a U: me at the bottom in the middle, my team mates to my left and right in turn, each further out higher, smaller and softer. */
	const lineup = $derived.by(() => {
		const left = mates.filter((_, i) => i % 2 === 0).reverse();
		const right = mates.filter((_, i) => i % 2 === 1);
		const row = [...left.map((p) => ({ p })), { me: true }, ...right.map((p) => ({ p }))];
		const mid = left.length;
		return row.map((m, i) => {
			const d = Math.abs(i - mid);
			return { ...m, d, size: m.me ? (mates.length > 2 ? 270 : mates.length ? 300 : 380) : Math.round((mates.length > 2 ? 200 : 220) * (1 - 0.15 * d)), raise: d * 46, blur: d * 1.4, opacity: 1 - d * 0.12 };
		});
	});

	// the table of the players
	const columns = $derived([
		{ key: 'rank', label: t('play.result.col.rank'), width: 'w-12', numeric: true },
		{ key: 'name', label: t('play.result.col.name') },
		{ key: 'score', label: t('play.result.col.score'), align: 'right', numeric: true }
	]);
	// the widths the team bar grows into, and where my team's part starts
	const offsets = $derived.by(() => {
		let at = 0;
		return (view?.teams ?? []).map((tm) => {
			const o = at;
			at += tm.share * 100;
			return o;
		});
	});
	const myIndex = $derived(view?.teams.findIndex((tm) => tm.mine) ?? -1);
	const teamSize = (rank) => [28, 24, 21, 18][rank - 1] ?? 18;
	// "D팀 승리" / "B팀 패배" in a game with teams (my own team), plain 승리/패배 in a duel
	const outcomeText = $derived.by(() => {
		if (!view?.outcome) return '';
		const win = view.outcome === 'win';
		if (view.layout === 'teams' && view.myTeam) return t(win ? 'play.result.winTeam' : 'play.result.loseTeam', { team: view.myTeam.letter });
		return t(win ? 'play.result.win' : 'play.result.lose');
	});
	/** The glow of the screen: my team's colour when there are teams, the brand green otherwise. */
	const glow = $derived(view?.layout === 'teams' && view.myTeam ? view.myTeam.color : 'var(--kk-accent)');
	/** The accent that goes with it: red with orange, yellow with pink, green with teal, blue with purple (the brand green with teal). */
	const PAIRS = { 1: '#ff9a3c', 2: '#ff6fae', 3: '#27c9c0', 4: '#9b7bff' };
	const pair = $derived((view?.layout === 'teams' && view.myTeam && PAIRS[view.myTeam.team]) || '#14c8b8');
	const SPARKLES = [
		{ star: 1, x: -6, y: 20, size: 34, tint: '#ffffff', t: 2.2, d: 0 }, { star: 1, x: 8, y: -30, size: 18, tint: '#fff3c4', t: 1.9, d: 0.7 },
		{ star: 1, x: 30, y: 112, size: 15, tint: '#ffffff', t: 2.4, d: 1.2 }, { star: 1, x: 62, y: -40, size: 22, tint: '#fff3c4', t: 2.6, d: 0.3 },
		{ star: 1, x: 84, y: 118, size: 17, tint: '#ffffff', t: 2.1, d: 1.7 }, { star: 1, x: 106, y: 24, size: 36, tint: '#fff3c4', t: 2.5, d: 0.5 },
		{ star: 0, x: 16, y: 10, size: 6, tint: '#ffffff', t: 1.8, d: 0.9 }, { star: 0, x: 90, y: 90, size: 7, tint: '#fff3c4', t: 2, d: 0.1 }
	];
	/** The podium: the first three of the ranking, the winner in the middle (two on a duel, one alone). */
	const podium = $derived.by(() => {
		const top = (view?.players ?? []).slice(0, 3).map((p, i) => ({ p, place: i + 1 }));
		return top.length >= 3 ? [top[1], top[0], top[2]] : top.length === 2 ? [top[1], top[0]] : top;
	});
	const STEP_H = { 1: 230, 2: 190, 3: 160 };
	const WHITE = [
		{ star: 1, x: 8, y: 12, size: 22, tint: '#fff', t: 2.3, d: 0 }, { star: 1, x: 30, y: 4, size: 14, tint: '#fff', t: 1.9, d: 0.8 },
		{ star: 1, x: 70, y: 8, size: 18, tint: '#fff', t: 2.5, d: 0.3 }, { star: 1, x: 92, y: 22, size: 24, tint: '#fff', t: 2.1, d: 1.2 },
		{ star: 1, x: 16, y: 46, size: 14, tint: '#fff', t: 2.6, d: 1.7 }, { star: 1, x: 84, y: 52, size: 16, tint: '#fff', t: 2.2, d: 0.5 },
		{ star: 0, x: 22, y: 24, size: 5, tint: '#fff', t: 1.8, d: 0.9 }, { star: 0, x: 44, y: 14, size: 6, tint: '#fff', t: 2, d: 0.2 },
		{ star: 0, x: 58, y: 30, size: 5, tint: '#fff', t: 1.7, d: 1.4 }, { star: 0, x: 78, y: 36, size: 6, tint: '#fff', t: 2.1, d: 0.6 },
		{ star: 0, x: 5, y: 66, size: 5, tint: '#fff', t: 1.9, d: 1 }, { star: 0, x: 95, y: 70, size: 5, tint: '#fff', t: 2.4, d: 0.1 }
	];
	/** What my moremi says about the game: a line picked at random (once per result) from the set for a win, a loss or in between. */
	const SAYINGS = 6;
	let sayIdx = $state(0);
	const sayKind = $derived(view?.outcome === 'win' || medalOutcome === 'win' ? 'win' : view?.outcome === 'lose' || medalOutcome === 'loss' ? 'lose' : 'mid');
	const sayText = $derived(t(`play.result.say.${sayKind}.${sayIdx}`));
	const modeName = $derived(store.currentRoom?.mode ?? '');
	/** The medal of the 전적 list: gold for a win, a cracked one for the bottom half. */
	const medalOutcome = $derived(view?.outcome === 'win' || view?.me?.rank === 1 ? 'win' : view?.me && view.me.rank - 1 >= Math.ceil(view.players.length / 2) ? 'loss' : 'half');
	// a phone puts the chat button at the top of the screen, out of the way of the buttons at the bottom
	let winW = $state(typeof innerWidth === 'undefined' ? 1280 : innerWidth);
	const phone = $derived(winW < 768);
	// on a phone the breakdown of a reward is a bottom sheet (a pop-out over the card would be cut off by the edge of the screen)
	const pinnedRow = $derived(rewardRows.find((r) => r.key === pinned));
</script>

<svelte:window onkeydown={key} onpointerdown={away} bind:innerWidth={winW} />

{#snippet breakdownBox(rw)}
<dl class="breakdown kk-card" id="reward-{rw.key}">
			<div><dt>{rw.baseLabel}</dt><dd>{commify(rw.base)}</dd></div>
			{#each rw.rows as row, i (i)}
				<div class="buffrow"><dt>{row.label}{#if row.note}<span class="sub">{row.note}</span>{/if}</dt><dd>{row.amount}{#if row.percent}<span class="sub">{row.percent}</span>{/if}</dd></div>
			{/each}
			<div class="sum"><dt>{t('play.result.total')}</dt><dd>+{commify(rw.value)}</dd></div>
		</dl>
{/snippet}

{#snippet cell(p, col)}
	{#if col.key === 'rank'}{#if p.rank <= 3}<PlacementMedal placement={p.rank} won={p.rank === 1 && view.outcome === 'win'} outcome={p.rank === 1 && view.outcome === 'win' ? 'win' : 'half'} size={30} />{:else}<span class="plain-rank">{p.rank}</span>{/if}
	{:else if col.key === 'name'}{#if p.user}<NickName user={p.user} showCheck maskable={!p.user.robot && !p.me} />{/if}
	{:else}{commify(p.score)}{/if}
{/snippet}

<!-- a word, with the letters nobody could go on from in the red the board shows them in -->

{#snippet word(w)}{#if w.hanbang}{w.word.slice(0, w.hanbang[0])}<span class="dead">{w.word.slice(w.hanbang[0], w.hanbang[1])}</span>{w.word.slice(w.hanbang[1])}{:else}{w.word}{/if}{/snippet}


<!-- the name of the game over the verdict, centred -->
{#snippet heading()}
	<div class="heading" data-skip style="--rb-glow: {glow}; --rb-ray: {pair}">
		<div class="gamemode">{modeName || t('play.result.over')}</div>
		{#if view.me}
			<div class="verdict-box">
				<strong class="verdict-text big glowtext" class:lose={view.outcome === 'lose'}>{outcomeText || t('play.result.rank', { n: view.me.rank })}</strong>
				{#if view.outcome !== 'lose'}<Sparkles list={SPARKLES} />{/if}
			</div>
		{/if}
	</div>
{/snippet}

<dialog bind:this={el} class="stage" tabindex="-1" aria-label={t('kkutu.dialog.result.title')} oncancel={(e) => (e.preventDefault(), document.activeElement instanceof HTMLInputElement || (open = false))} onclose={() => (open = false)} onclick={(e) => (e.target === e.currentTarget || e.target.closest('[data-skip]')) && skip()}>
	{#if view && open && step === 'mine'}
		<div class="shade even" aria-hidden="true"></div>
		<div class="deco" style:--glow={glow} style:--pair={pair} aria-hidden="true"><span class="cut-lw"><i class="cut cut-l"></i></span><i class="cut cut-r"></i></div>
		<div class="mine" data-step="mine" style:--glow={glow} style:--pair={pair}>
			<!-- the scene: me on the light, my team mates standing behind me, cut off by the bottom edge -->
			<div class="mine-left" data-skip>
				{#if view.me?.user}
					<div class="lineup">
						{#each lineup as m (m.me ? 'me' : m.p.id)}
							{#if m.me}
								<div class="stand me-char">{#if sayText}<div class="say" role="status" data-say>{sayText}</div>{/if}<Moremi equip={view.me.user.equip} shop={store.shop} {parts} size={m.size} label={view.me.user.name} frame={false} /><div class="plate"><LevelIcon level={view.me.user.lv} size={18} /><NickName user={view.me.user} hideTag /></div></div>
							{:else}
								<div class="stand mate" style:margin-bottom="{m.raise}px"><span class="body" style:z-index={10 - m.d} style:opacity={m.opacity} style:--blur="{m.blur}px"><Moremi equip={m.p.user.equip} shop={store.shop} {parts} size={m.size} label={m.p.user.name} frame={false} /></span><div class="tag"><LevelIcon level={m.p.user.lv} size={14} maskable={!m.p.user.robot} /><NickName user={m.p.user} hideTag maskable={!m.p.user.robot} /></div></div>
							{/if}
						{/each}
					</div>
				{/if}
			</div>
			<div class="mine-right">
				{@render heading()}
				<div class="cards" class:solo={!view.words.best}>
					<div class="card-a kk-card" data-skip>
						<div class="headline">
							{#if view.me}<PlacementMedal placement={view.me.rank} won={view.outcome === 'win'} outcome={medalOutcome} size={52} />{/if}
							<div>
								<div class="headline-text">{view.layout === 'teams' ? t('play.result.lineTeam', { rank: view.me?.rank ?? '', team: view.myTeam?.letter ?? '', teamRank: view.myTeam?.rank ?? '' }) : view.gap ? t('play.result.gap', { n: commify(view.gap.diff) }) : t('play.result.rank', { n: view.me?.rank ?? '' })}</div>
								
							</div>
						</div>
						<dl class="stats">
							{#if view.layout === 'teams' && view.myTeam}<div><dt>{t('play.result.teamScore')}</dt><dd>{commify(view.myTeam.score)}</dd></div>{/if}
							<div><dt>{t('play.result.myScore')}</dt><dd>{commify(view.hero.score)}</dd></div>
							{#if view.words.best}
								<div><dt>{t('play.result.statWords')}</dt><dd>{t('play.result.count', { n: view.words.count })}</dd></div>
							{:else if view.stat}
								<div><dt>{t(view.stat.kind === 'solved' ? 'play.result.solved' : 'play.result.speed')}</dt><dd>{commify(view.stat.value)} <span class="sub">{t(view.stat.kind === 'solved' ? 'play.game.solvedUnit' : 'play.result.speedUnit')}</span></dd></div>
							{/if}
						</dl>
					</div>
					{#if view.words.best}
						<div class="card-w kk-card" data-skip>
							<div class="w-label">{t(view.words.kind === 'deadend' ? 'play.result.bestNote' : 'play.result.topWord')}</div>
							<div class="w-best"><span class="best-word">{@render word(view.words.best)}</span><span class="w-score">+{commify(view.words.best.score ?? 0)}</span></div>
							<div class="w-label">{t('play.result.tray')}</div>
							<div class="w-tray">{#each view.words.top.slice(0, 4) as w, i (i)}<span>{@render word(w)} <span class="sub">{commify(w.score ?? 0)}</span></span>{/each}{#if view.words.count > 4}<span class="sub">{t('play.result.more', { n: view.words.count - 4 })}</span>{/if}</div>
						</div>
					{/if}
				</div>
				<div class="card-b kk-card">
					<div class="rewards big" data-exp>
						{#each rewardRows as rw (rw.key)}
							<div class="reward-wrap" style:--cur={rw.color}>
								<div class="reward big" role="group" aria-label={rw.label}>
									<span class="reward-num" data-reward={rw.key}><GameIcon name={rw.icon} size={34} />+{commify(rw.value)}</span>
									<span class="reward-label">{rw.label}{#if rw.pct > 0}<b class="buff">(+{rw.pct}%)</b>{/if}<button type="button" class="info" aria-label={t('play.result.detail', { name: rw.label })} aria-expanded={isOpen(rw.key)} aria-controls="reward-{rw.key}" onclick={() => (pinned = pinned === rw.key ? null : rw.key)}><Icon name="info" size={16} /></button></span>
								</div>
								{#if isOpen(rw.key) && !phone}
									<div class="breakdown-pos center">
										{@render breakdownBox(rw)}
									</div>
								{/if}
							</div>
						{/each}
						{#if mine?.reward.ep}<div class="reward-wrap"><div class="reward big"><span class="reward-num" data-reward="ep"><Icon name="workspace_premium" size={34} />+{commify(mine.reward.ep)}</span><span class="reward-label">EP</span></div></div>{/if}
					</div>
					{#if mine}
						<div class="lvline"><span class="myLevel lvl"><LevelIcon level={shownLevel} size={26} />{t('play.result.level')} <span data-level>{shownLevel}</span></span><span class="gauge-text" data-gauge-text>{scoreText}</span></div>
						<div class="kk-bar gauge" role="presentation">
							<span class="gauge-before" style:width={bars.before}></span>
							<span class="gauge-current" style:width={bars.current}></span>
							<span class="gauge-bonus" style:width={bars.bonus}></span>
						</div>
					{/if}
				</div>
				<div class="mine-actions">
					<div class="mine-buttons">
						{#if store.replayReady && !net.guest}<button type="button" class="kk-btn log" onclick={() => store.saveReplay()}><Icon name="download" size={22} />{t('kkutu.dialog.result.saveReplay')}</button>{/if}
						<button type="button" class="kk-btn kk-btn-primary ok" onclick={() => (open = false)}>{t('kkutu.dialog.result.ok')}<kbd class="kk-key" aria-hidden="true">Enter</kbd></button>
					</div>
				</div>
			</div>
		</div>
	{:else if view && open}
		<div class="shade even intro" aria-hidden="true"></div>
		<div class="deco intro" style:--glow={glow} style:--pair={pair} aria-hidden="true"><span class="cut-lw"><i class="cut cut-l"></i></span><i class="cut cut-r"></i></div>
		<div class="frame" class:leaving data-layout={view.layout} style:--glow={glow} style:--pair={pair}>
			<!-- the scene: the podium under a spotlight, standing on the floor of the screen -->
			<div class="left" data-skip>
				<div class="stage-floor">
					<div class="white-sparks" style="--rb-glow: #fff; --rb-ray: #fff"><Sparkles list={WHITE} /></div>
					<div class="podium" data-podium>
						{#each podium as pl (pl.p.id)}
							<div class="pcol" data-place={pl.place} style:--c={view.layout === 'teams' ? pl.p.color : 'var(--kk-accent)'} style:--delay="{(3 - pl.place) * 160}ms">
								{#if pl.place === 1}<div class="spot" aria-hidden="true"></div>{/if}
								<div class="pchar">
									{#if store.resultSay?.[pl.p.id]}<div class="say" role="status">{t(store.resultSay[pl.p.id])}</div>{/if}
									{#if pl.p.user}<Moremi equip={pl.p.user.equip} shop={store.shop} {parts} size={pl.place === 1 ? 250 : 190} label={pl.p.user.name} frame={false} />{/if}
								</div>
								<!-- a plain white drum: the feet stand on its top, the name and the score are on its face -->
								<div class="pstep" style:height="{STEP_H[pl.place]}px">
									<i class="ptop"></i>
									<div class="pface">
										<div class="pname" class:self={pl.p.me}>{#if pl.p.user}<LevelIcon level={pl.p.user.lv} size={16} /><NickName user={pl.p.user} hideTag maskable={!pl.p.user.robot && !pl.p.me} />{/if}</div>
										<div class="pscore">{commify(pl.p.score)}</div>
									</div>
								</div>
							</div>
						{/each}
					</div>
				</div>
			</div>

			<!-- the right column: the same as my own result's -->
			<div class="mine-right">
				{@render heading()}
				{#if view.layout === 'teams'}
					<div class="card-t kk-card" data-skip>
						<div class="teambar" role="list">
							{#each view.teams as tm (tm.team)}
								<div role="listitem" class="seg" data-team={tm.letter} style:--c={tm.color} style:width="{grown ? tm.share * 100 : 0}%" style:flex-basis="{grown ? tm.share * 100 : 0}%">
									<div class="seg-top">
										{#if tm.rank === 1}<svg width="14" height="11" viewBox="0 0 18 14" aria-hidden="true"><path d="M1 12 L2 3 L6 7 L9 1 L12 7 L16 3 L17 12 Z" fill="currentColor" /></svg>{/if}
										<span class="seg-letter">{tm.letter}</span>
										<span class="seg-rank">{t('play.result.rank', { n: tm.rank })}</span>
									</div>
									<div class="seg-score" style:font-size="{teamSize(tm.rank)}px">{commify(tm.score)}</div>
								</div>
							{/each}
						</div>
						{#if myIndex >= 0}
							<div class="mymark"><span class="mymark-line" style:margin-left="{offsets[myIndex]}%" style:width="{view.teams[myIndex].share * 100}%"></span></div>
							<div class="mymark-text" style:margin-left="{offsets[myIndex]}%">{t('play.result.myTeam')}</div>
						{/if}
					</div>
				{/if}
				<div class="card-table panel-list kk-card" data-skip>
					<DataTable bare caption={t('kkutu.dialog.result.title')} {columns} rows={view.players} rowKey={(p) => p.id} rowClass={(p) => `${p.me ? 'me-row' : ''} ${view.layout === 'teams' ? `tint tint-${p.team}` : ''}`} {cell} />
				</div>
				<div class="mine-actions">
					<div class="mine-buttons">
						<button type="button" class="kk-btn kk-btn-primary ok" data-step-next onclick={next}>{t('play.result.next')}<kbd class="kk-key" aria-hidden="true">Enter</kbd></button>
					</div>
				</div>
			</div>
		</div>
	{/if}
	<!-- the page's chat button is behind the modal: this one is the same chat, inside it -->
	{#if view && open && store.net.chat && store.draft}<div class="chat-host"><ChatPanel {store} fab lift={phone ? 'top' : false} idSuffix="-result" /></div>{/if}
	<Dialog bind:open={() => phone && pinned !== null && !!pinnedRow, (v) => !v && (pinned = null)} title={pinnedRow?.label ?? ''}>
		{#if pinnedRow}<div class="sheetbox">{@render breakdownBox(pinnedRow)}</div>{/if}
	</Dialog>
</dialog>

<style>
	.verdict-text,
	.headline-text,
	.reward-num {
		font-family: 'ONE-Mobile-POP', 'NanumSquare Neo', sans-serif;
		font-weight: 400;
	}
	/* The game stays visible behind: blurred and dimmed, with a fade to black where the text is (left to right, and from the bottom).
	   The panel is the client's own card, so it, the table and the buttons follow the theme like everything else. */
	.stage {
		--cur-xp: #0c9a6b;
		--cur-ping: #b5700a;
		position: fixed;
		inset: 0;
		width: 100vw;
		max-width: none;
		height: 100dvh;
		max-height: none;
		margin: 0;
		padding: 0;
		border: 0;
		background: transparent;
		color: #fff;
		outline: none;
		overflow: auto;
	}
	.stage::backdrop {
		background: rgb(0 0 0 / 0.3);
		backdrop-filter: blur(10px);
	}
	.stage[open] {
		animation: stage-in 0.3s ease-out;
	}
	@keyframes stage-in {
		from {
			opacity: 0;
		}
	}
	.shade {
		position: fixed;
		inset: 0;
		pointer-events: none;
		background:
			linear-gradient(0deg, #000000ff 0, #000000a6 110px, #00000000 320px),
			linear-gradient(90deg, #000000f2 0%, #000000d9 55%, #000000b3 84%, #00000000 100%);
	}
	.shade.intro {
		animation: dim-in 0.7s ease-out both;
	}
	@keyframes dim-in {
		from {
			opacity: 0;
		}
	}
	.deco.intro .cut-lw {
		animation: cut-l-in 0.8s cubic-bezier(0.22, 1, 0.36, 1) 0.3s both;
	}
	.deco.intro .cut-r {
		animation: cut-r-in 0.8s cubic-bezier(0.22, 1, 0.36, 1) 0.65s both;
	}
	@keyframes cut-l-in {
		from {
			opacity: 0;
			transform: translateX(-100%);
		}
	}
	@keyframes cut-t-in {
		from {
			opacity: 0;
			transform: translateY(-100%);
		}
	}
	@keyframes cut-b-in {
		from {
			opacity: 0;
			transform: translateY(100%);
		}
	}
	@keyframes cut-r-in {
		from {
			opacity: 0;
			transform: translateX(100%);
		}
	}
	.shade.even {
		background: radial-gradient(ellipse at 50% 45%, #000000a6 0, #000000e6 70%, #000000f2 100%);
	}
	.mine {
		position: relative;
		min-height: 100%;
		box-sizing: border-box;
		display: flex;
		text-shadow: 0 1px 2px rgb(0 0 0 / 0.7), 0 0 8px rgb(0 0 0 / 0.6);
	}
	.mine {
		flex-direction: row;
		align-items: stretch;
		justify-content: center;
		gap: 40px;
		padding: 0 56px;
		max-width: 1360px;
		margin: 0 auto;
	}
	.mine-left {
		flex: 1;
		min-width: 0;
		position: relative;
		display: flex;
		flex-direction: column;
		align-items: center;
		justify-content: flex-end;
		gap: 10px;
		padding-bottom: 72px;
		background: radial-gradient(ellipse at 50% 100%, rgb(255 255 255 / 0.16), transparent 62%);
	}
	.mine-right {
		flex: none;
		width: min(660px, 48vw);
		display: flex;
		flex-direction: column;
		justify-content: center;
		gap: 16px;
		padding: 28px 0;
	}
	.heading {
		display: flex;
		flex-direction: column;
		align-items: center;
		gap: 2px;
	}
	.gamemode {
		font-family: 'ONE-Mobile-POP', 'NanumSquare Neo', sans-serif;
		font-size: 24px;
		color: #fff;
	}
	.verdict-text.big {
		font-size: 76px;
		text-align: center;
	}
	.card-a,
	.card-b {
		padding: 16px 20px;
		color: var(--kk-text);
		text-shadow: none;
		display: flex;
		flex-direction: column;
		gap: 12px;
	}
	.headline {
		display: flex;
		align-items: center;
		gap: 16px;
	}
	.headline-text {
		font-size: 24px;
		font-weight: 800;
	}
	.stats {
		margin: 0;
		font-weight: 700;
	}
	.stats > div {
		display: flex;
		justify-content: space-between;
		padding-top: 10px;
		border-top: 1.5px solid var(--kk-line);
	}
	.stats dt {
		color: var(--kk-muted);
	}
	.stats dd {
		margin: 0;
		font-weight: 800;
	}
	.stats .sub {
		color: var(--kk-muted);
		font-size: 13px;
	}
	.cards {
		display: grid;
		grid-template-columns: 1fr 1fr;
		gap: 12px;
	}
	.cards.solo {
		grid-template-columns: 1fr;
	}
	.card-w {
		padding: 14px 16px;
		color: var(--kk-text);
		text-shadow: none;
		display: flex;
		flex-direction: column;
		gap: 4px;
		min-width: 0;
	}
	.w-label {
		font-size: 12px;
		font-weight: 700;
		color: var(--kk-muted);
	}
	.w-best {
		display: flex;
		align-items: baseline;
		gap: 8px;
		font-size: 26px;
		font-weight: 800;
		font-family: 'ONE-Mobile-POP', 'NanumSquare Neo', sans-serif;
		font-weight: 400;
	}
	.w-score {
		font-size: 14px;
		color: var(--kk-accent-strong);
		font-weight: 800;
	}
	.w-tray {
		display: flex;
		flex-wrap: wrap;
		gap: 2px 12px;
		font-weight: 700;
		font-size: 14px;
	}
	.w-tray .sub,
	.card-w .sub {
		color: var(--kk-muted);
		font-weight: 600;
	}
	.lineup {
		animation: from-left 0.7s cubic-bezier(0.22, 1, 0.36, 1) both;
		display: flex;
		align-items: flex-end;
		justify-content: center;
		padding-bottom: 4px;
	}
	.stand {
		position: relative;
		flex: none;
		filter: drop-shadow(0 8px 10px rgb(0 0 0 / 0.5));
	}
	/* the mates are behind me and each other: softer the further out, and their feet go under the next one */
	.stand.mate {
		margin-inline: -46px;
		filter: none;
	}
	.stand.mate .body {
		position: relative;
		display: block;
		filter: blur(var(--blur, 0px)) drop-shadow(0 8px 10px rgb(0 0 0 / 0.5)) brightness(0.9);
	}
	.stand.me-char {
		z-index: 20;
	}
	/* a speech bubble over my head, popping up a moment after the screen */
	/* the dialog is white-on-dark: the chat keeps the theme's own colours */
	.chat-host {
		display: contents;
		color: var(--kk-text);
		text-shadow: none;
	}
	.say {
		position: absolute;
		left: 50%;
		bottom: calc(100% - 14px);
		z-index: 4;
		width: max-content;
		max-width: 240px;
		padding: 10px 16px;
		border-radius: var(--kk-r-lg);
		background: var(--kk-surface);
		color: var(--kk-text);
		text-shadow: none;
		font-size: 15px;
		font-weight: 800;
		line-height: 1.35;
		text-align: center;
		box-shadow: 0 3px 0 var(--kk-line-strong);
		transform: translateX(-50%);
		animation: say-pop 0.45s cubic-bezier(0.34, 1.6, 0.64, 1) 0.9s both;
	}
	.say::after {
		content: '';
		position: absolute;
		left: 50%;
		top: 100%;
		margin-left: -8px;
		border: 8px solid transparent;
		border-top-color: var(--kk-surface);
	}
	@keyframes say-pop {
		from {
			opacity: 0;
			transform: translateX(-50%) translateY(10px) scale(0.8);
		}
	}
	.stand .tag {
		position: absolute;
		left: 50%;
		bottom: 6px;
		transform: translateX(-50%);
		display: inline-flex;
		align-items: center;
		gap: 4px;
		padding: 2px 8px;
		border-radius: var(--kk-r-sm);
		background: var(--kk-surface);
		color: var(--kk-text);
		text-shadow: none;
		font-size: 12px;
		font-weight: 800;
		white-space: nowrap;
		box-shadow: 0 2px 0 var(--kk-line-strong);
		z-index: 30;
	}
	.stand .plate {
		position: absolute;
		left: 50%;
		top: 100%;
		transform: translateX(-50%);
		margin-top: 6px;
		white-space: nowrap;
	}
	.info {
		display: inline-grid;
		place-items: center;
		margin-left: 4px;
		padding: 0;
		border: 0;
		background: none;
		color: var(--kk-muted);
		cursor: pointer;
		vertical-align: middle;
	}
	.info:hover,
	.info[aria-expanded='true'] {
		color: var(--cur, var(--kk-accent-strong));
	}
	.buff {
		margin-left: 4px;
		color: var(--cur);
		font-weight: 800;
	}
	.breakdown .buffrow {
		color: var(--cur);
	}
	.breakdown .buffrow .sub {
		color: var(--cur);
		opacity: 0.8;
	}
	.rewards.big {
		justify-content: space-around;
		gap: 8px;
	}
	.reward.big {
		flex-direction: column;
		gap: 2px;
		padding: 4px 10px;
		margin: 0;
		font-size: 30px;
	}
	.reward-num {
		display: inline-flex;
		align-items: center;
		gap: 8px;
		font-weight: 800;
		font-variant-numeric: tabular-nums;
	}
	.reward-label {
		font-size: 14px;
		font-weight: 700;
		color: var(--kk-muted);
	}
	.breakdown-pos.center {
		left: 50%;
		transform: translateX(-50%);
	}
	.lvline {
		display: flex;
		justify-content: space-between;
		font-weight: 800;
	}
	.myLevel.lvl {
		margin-left: 0;
		display: inline-flex;
		align-items: center;
		gap: 6px;
	}
	.mine-actions {
		display: flex;
		flex-direction: column;
		align-items: center;
		gap: 8px;
	}
	.mine-buttons {
		width: 100%;
		display: flex;
		gap: 10px;
	}
	.mine-buttons .ok {
		flex: 1;
		min-height: 52px;
		font-size: 1.15rem;
	}
	.mine-buttons .log {
		min-height: 52px;
		display: inline-flex;
		align-items: center;
		gap: 6px;
		color: var(--kk-text);
		text-shadow: none;
	}
	/* gold without the metal: white into a light yellow, with a glow in my team's colour (the brand green without teams) */
	.glowtext {
		background: linear-gradient(180deg, #ffffff 25%, color-mix(in srgb, var(--pair) 30%, #fff) 100%);
		-webkit-background-clip: text;
		background-clip: text;
		color: transparent !important;
		text-shadow: none !important;
		filter: drop-shadow(0 0 14px var(--glow)) drop-shadow(0 3px 0 color-mix(in srgb, var(--glow) 60%, #000));
	}
	/* a lost game: the word is cut from grey stone, speckled, with a few cracks through it and no glow */
	.glowtext.lose {
		background-image:
			url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 300 90' preserveAspectRatio='none'%3E%3Cg fill='none' stroke='%23262a2f' stroke-width='2.6' stroke-linejoin='bevel' stroke-linecap='round'%3E%3Cpath d='M64 0 L55 20 L68 36 L57 58 L65 90'/%3E%3Cpath d='M68 36 L88 44 L96 58'/%3E%3Cpath d='M182 0 L192 18 L178 40 L190 64 L181 90'/%3E%3Cpath d='M178 40 L158 48'/%3E%3Cpath d='M252 8 L238 30 L250 46 L243 70'/%3E%3C/g%3E%3C/svg%3E"),
			radial-gradient(rgb(0 0 0 / 0.2) 1.2px, transparent 1.7px),
			radial-gradient(rgb(255 255 255 / 0.32) 1px, transparent 1.5px),
			linear-gradient(180deg, #d9dce0 0%, #a9aeb4 50%, #72777e 100%);
		background-size: 100% 100%, 9px 9px, 11px 11px, 100% 100%;
		background-position: 0 0, 0 0, 4px 5px, 0 0;
		background-repeat: no-repeat, repeat, repeat, no-repeat;
		filter: drop-shadow(0 4px 0 #3a3e44) drop-shadow(0 8px 8px rgb(0 0 0 / 0.5));
	}
	.verdict-box {
		position: relative;
		align-self: center;
		padding: 0 24px;
	}
	/* the colour cut-outs of the scene: slabs slanted across the edges, in the glow colour and a warm light one */
	.deco {
		position: fixed;
		inset: 0;
		pointer-events: none;
		overflow: hidden;
	}
	.cut {
		position: absolute;
		top: 0;
		bottom: 0;
	}
	/* the left slab: one shape, glow colour into its pair */
	.cut-lw {
		position: absolute;
		inset: 0 auto 0 0;
		width: 17%;
	}
	.cut-l {
		left: 0;
		width: 100%;
		clip-path: polygon(0 0, 60% 0, 100% 100%, 0 100%);
		background: linear-gradient(160deg, color-mix(in srgb, var(--pair) 55%, #fff), var(--glow) 55%, color-mix(in srgb, var(--glow) 70%, #000));
	}
	.cut-r {
		right: 0;
		width: 11%;
		clip-path: polygon(75% 0, 100% 0, 100% 100%, 0 100%);
		background: linear-gradient(200deg, color-mix(in srgb, var(--pair) 40%, #fff), var(--pair) 60%, color-mix(in srgb, var(--glow) 60%, var(--pair)));
	}
	@keyframes from-left {
		from {
			opacity: 0;
			transform: translateX(-70%);
		}
	}
	.frame.leaving .left {
		animation: to-left 0.48s cubic-bezier(0.55, 0, 0.8, 0.3) both;
	}
	.frame.leaving .mine-right {
		animation: to-right 0.48s cubic-bezier(0.55, 0, 0.8, 0.3) both;
	}
	@keyframes to-left {
		to {
			opacity: 0;
			transform: translateX(-110%);
		}
	}
	@keyframes to-right {
		to {
			opacity: 0;
			transform: translateX(110%);
		}
	}
	.stage-floor {
		position: relative;
		flex: 1;
		display: flex;
		align-items: flex-end;
		justify-content: center;
	}
	/* a column of light from above, behind the winner */
	.pcol {
		position: relative;
	}
	.spot {
		position: absolute;
		left: 50%;
		bottom: 0;
		width: 250px;
		height: 100vh;
		margin-left: -125px;
		z-index: 0;
		background: linear-gradient(180deg, rgb(255 255 255 / 0.03), rgb(255 255 255 / 0.17));
		animation: spot-on 0.9s ease-out 0.2s both;
	}
	@keyframes spot-on {
		from {
			opacity: 0;
		}
	}
	.white-sparks {
		position: absolute;
		left: 50%;
		bottom: 0;
		width: 520px;
		height: 100%;
		margin-left: -260px;
		pointer-events: none;
	}
	.podium {
		position: relative;
		display: flex;
		align-items: flex-end;
		gap: 10px;
	}
	.pcol {
		display: flex;
		flex-direction: column;
		align-items: center;
	}
	.pchar {
		position: relative;
		z-index: 3;
		margin-bottom: -12px;
		filter: drop-shadow(0 4px 4px rgb(0 0 0 / 0.4));
		animation: stand-up 0.55s cubic-bezier(0.34, 1.5, 0.64, 1) calc(var(--delay) + 350ms) both;
	}
	@keyframes stand-up {
		from {
			opacity: 0;
			transform: translateY(40px) scale(0.9);
		}
	}
	/* a plain white drum: a flat ellipse for its top, nothing shiny */
	.pstep {
		position: relative;
		width: 176px;
		box-sizing: border-box;
		background: #f3f5f6;
		transform-origin: 50% 100%;
		animation: step-rise 0.55s cubic-bezier(0.22, 1, 0.36, 1) var(--delay) both;
	}
	.ptop {
		position: absolute;
		left: 0;
		right: 0;
		top: -17px;
		height: 34px;
		z-index: 1;
		border-radius: 50%;
		background: #e3e7e9;
	}
	.pface {
		position: relative;
		height: 100%;
		overflow: hidden;
		display: flex;
		flex-direction: column;
		align-items: center;
		gap: 8px;
		padding-top: 24px;
	}
	@keyframes step-rise {
		from {
			transform: scaleY(0);
		}
	}
	.pname {
		display: inline-flex;
		align-items: center;
		gap: 4px;
		max-width: 100%;
		box-sizing: border-box;
		padding: 0 18px;
		overflow: hidden;
		white-space: nowrap;
		font-size: 14px;
		font-weight: 800;
		color: #11181c;
		text-shadow: none;
		mask-image: linear-gradient(90deg, transparent 0, #000 16%, #000 84%, transparent 100%);
	}
	.pname.self {
		color: var(--kk-accent-strong);
	}
	/* the score: just the number, centred, in plain dark ink */
	.pscore {
		font-family: 'ONE-Mobile-POP', 'NanumSquare Neo', sans-serif;
		font-size: 36px;
		line-height: 1;
		font-variant-numeric: tabular-nums;
		color: #11181c;
		text-shadow: none;
	}
	.frame {
		text-shadow: 0 1px 2px rgb(0 0 0 / 0.7), 0 0 8px rgb(0 0 0 / 0.6);
		position: relative;
		min-height: 100%;
		box-sizing: border-box;
		display: flex;
		align-items: stretch;
		justify-content: center;
		gap: 40px;
		padding: 0 56px;
		max-width: 1360px;
		margin: 0 auto;
	}
	.left {
		position: relative;
		flex: 1;
		min-width: 0;
		display: flex;
		flex-direction: column;
	}
	.card-t {
		padding: 14px 16px 10px;
		color: var(--kk-text);
		text-shadow: none;
	}
	.card-table {
		padding: 4px 8px;
		max-height: 46dvh;
		overflow: auto;
		color: var(--kk-text);
		text-shadow: none;
	}
	.teambar {
		display: flex;
		gap: 4px;
		height: 72px;
	}
	.seg {
		box-sizing: border-box;
		min-width: 0;
		padding: 8px 10px;
		display: flex;
		flex-direction: column;
		justify-content: space-between;
		overflow: hidden;
		border-radius: var(--kk-r-sm);
		background: var(--c);
		color: color-mix(in srgb, var(--c) 14%, black);
		box-shadow: 0 3px 0 color-mix(in srgb, var(--c) 55%, black);
		transition: width 1s cubic-bezier(0.22, 1, 0.36, 1), flex-basis 1s cubic-bezier(0.22, 1, 0.36, 1);
	}
	.seg-top {
		display: flex;
		align-items: center;
		gap: 5px;
		font-weight: 800;
	}
	.seg-letter {
		font-size: 20px;
		line-height: 1;
	}
	.seg-rank {
		margin-left: auto;
		font-size: 11px;
	}
	.seg-score {
		font-weight: 800;
		line-height: 1;
		font-variant-numeric: tabular-nums;
		white-space: nowrap;
	}
	.mymark {
		margin-top: 9px;
		height: 3px;
	}
	.mymark-line {
		display: block;
		height: 3px;
		border-radius: 2px;
		background: var(--kk-text);
	}
	.mymark-text {
		margin-top: 3px;
		font-size: 12px;
		font-weight: 700;
		color: var(--kk-muted);
	}
	.panel-list {
		flex: none;
		min-height: 0;
		overflow: auto;
		padding: 4px 8px;
	}
	.panel-list :global(tr.me-row) {
		background: var(--kk-accent-soft);
		font-weight: 800;
	}
	.panel-list :global(td) {
		padding-top: 10px;
		padding-bottom: 10px;
	}
	/* a team shows as the tint of its rows (rounded at the ends), light for the others and stronger and outlined for me */
	.panel-list :global(tr.tint),
	.panel-list :global(tr.me-row) {
		background: transparent;
	}
	.panel-list :global(tr.tint > td) {
		background: color-mix(in srgb, var(--c) 16%, transparent);
	}
	.panel-list :global(tr.tint.me-row > td) {
		background: color-mix(in srgb, var(--c) 38%, transparent);
		box-shadow: inset 0 2px 0 var(--c), inset 0 -2px 0 var(--c);
	}
	.panel-list :global(tr.tint > td:first-child) {
		border-radius: var(--kk-r-md) 0 0 var(--kk-r-md);
	}
	.panel-list :global(tr.tint > td:last-child) {
		border-radius: 0 var(--kk-r-md) var(--kk-r-md) 0;
	}
	.panel-list :global(tr.tint.me-row > td:first-child) {
		box-shadow: inset 0 2px 0 var(--c), inset 0 -2px 0 var(--c), inset 2px 0 0 var(--c);
	}
	.panel-list :global(tr.tint.me-row > td:last-child) {
		box-shadow: inset 0 2px 0 var(--c), inset 0 -2px 0 var(--c), inset -2px 0 0 var(--c);
	}
	.panel-list :global(tr.tint-1) { --c: var(--kk-team-1); }
	.panel-list :global(tr.tint-2) { --c: var(--kk-team-2); }
	.panel-list :global(tr.tint-3) { --c: var(--kk-team-3); }
	.panel-list :global(tr.tint-4) { --c: var(--kk-team-4); }
	.plain-rank {
		padding-left: 9px;
		font-weight: 800;
		color: var(--kk-muted);
	}
	.rewards {
		display: flex;
		flex-wrap: wrap;
		align-items: center;
		gap: 6px 18px;
		font-size: 14px;
		font-weight: 800;
	}
	.reward-wrap {
		position: relative;
	}
	.reward {
		display: inline-flex;
		align-items: center;
		gap: 4px;
		padding: 2px 4px;
		margin: -2px -4px;
		border-radius: var(--kk-r-sm);
		font: inherit;
		font-weight: 800;
		color: inherit;
		cursor: pointer;
	}
	.reward:hover,
	.reward:focus-visible {
		outline: 2px solid var(--kk-accent);
	}
	/* opens upward over the list; its padding keeps the pointer inside while it crosses from the label */
	.breakdown-pos {
		position: absolute;
		left: -4px;
		bottom: 100%;
		z-index: 5;
		padding-bottom: 8px;
	}
	.breakdown {
		margin: 0;
		min-width: 260px;
		padding: 10px 12px;
		display: flex;
		flex-direction: column;
		gap: 6px;
		font-size: 13px;
		font-weight: 600;
	}
	.sheetbox .breakdown {
		min-width: 0;
		padding: 0;
		border: 0;
		background: transparent;
		box-shadow: none;
		font-size: 15px;
		gap: 10px;
		color: var(--kk-text);
		text-shadow: none;
	}
	.breakdown > div {
		display: flex;
		justify-content: space-between;
		gap: 16px;
	}
	.breakdown dt {
		display: flex;
		flex-direction: column;
	}
	.breakdown dd {
		margin: 0;
		display: flex;
		flex-direction: column;
		align-items: flex-end;
		font-weight: 800;
		font-variant-numeric: tabular-nums;
	}
	.breakdown .sub {
		font-size: 11px;
		font-weight: 600;
		color: var(--kk-muted);
	}
	.breakdown .sum {
		padding-top: 6px;
		border-top: 1.5px solid var(--kk-line);
	}
	.myLevel {
		margin-left: auto;
		color: var(--kk-warn);
	}
	.gauge {
		display: flex;
		height: 8px;
	}
	.gauge-before {
		background: var(--kk-accent-strong);
	}
	.gauge-bonus {
		background: var(--kk-warn);
	}
	.gauge-text {
		font-size: 11px;
		font-weight: 700;
		color: var(--kk-muted);
		font-variant-numeric: tabular-nums;
	}
	.ok {
		flex: 1;
		font-size: 1.05rem;
		display: inline-flex;
		align-items: center;
		justify-content: center;
		gap: 10px;
	}
	.best-word {
		font-size: 44px;
		font-weight: 800;
		line-height: 1.1;
	}
	.dead {
		color: #ff7a6e;
	}
	/* me, standing on the scene, with the plate of the game's name tags under me */
	.plate {
		display: inline-flex;
		align-items: center;
		gap: 6px;
		padding: 6px 12px;
		border-radius: var(--kk-r-md);
		background: var(--kk-surface);
		color: var(--kk-text);
		box-shadow: 0 3px 0 var(--kk-line-strong);
		font-weight: 800;
		text-shadow: none;
	}
	@media (max-width: 900px) {
		.frame {
			flex-direction: column;
		}
		.left {
			padding: 20px 16px 12px;
		}
	}
	/* A phone, portrait: the character(s) on top, the facts under them (the desktop's two columns, one above the other). The
	   scene is drawn at its desktop size and shrunk with `zoom`, so the characters, the podium and the lineup keep their proportions. */
	@media (max-width: 767px) {
		.stage {
			overflow: hidden;
		}
		.mine,
		.frame {
			flex-direction: column;
			gap: 0;
			height: 100dvh;
			min-height: 0;
			max-width: none;
			padding: 0;
		}
		/* the cut-out slabs sit on the top and the bottom edge instead of the left and the right one (a phone is tall, not wide) */
		.shade {
			background: linear-gradient(0deg, #000000ff 0, #000000d9 45%, #00000073 100%);
		}
		.deco .cut-lw {
			inset: 0 0 auto 0;
			width: 100%;
			height: 10%;
		}
		.deco .cut-l {
			top: 0;
			bottom: 0;
			clip-path: polygon(0 0, 100% 0, 100% 30%, 0 100%);
		}
		.deco .cut-r {
			top: auto;
			bottom: 0;
			right: auto;
			left: 0;
			width: 100%;
			height: 8%;
			clip-path: polygon(0 70%, 100% 0, 100% 100%, 0 100%);
		}
		.deco.intro .cut-lw {
			animation-name: cut-t-in;
		}
		.deco.intro .cut-r {
			animation-name: cut-b-in;
		}
		.mine-left,
		.left {
			flex: none;
			height: 32dvh;
			padding: 0;
			overflow: hidden;
		}
		.left {
			justify-content: flex-end;
		}
		.stage-floor {
			zoom: 0.5;
		}
		.lineup {
			zoom: 0.5;
		}
		.mine-right {
			flex: 1 1 0;
			width: 100%;
			min-height: 0;
			box-sizing: border-box;
			justify-content: flex-start;
			gap: 8px;
			padding: 4px 12px 0;
			overflow-y: auto;
		}
		.verdict-text.big {
			font-size: 44px;
		}
		.gamemode {
			font-size: 16px;
		}
		.cards {
			grid-template-columns: 1fr;
			gap: 8px;
		}
		.card-a,
		.card-b,
		.card-w {
			padding: 10px 14px;
		}
		.card-table {
			max-height: none;
			flex: 0 1 auto;
			min-height: 0;
		}
		.teambar {
			height: 60px;
		}
		/* what I earned comes first, then how it went (the words) */
		.card-b {
			order: 1;
		}
		.cards {
			order: 2;
		}
		/* the buttons are the bottom of the screen: flush to its edge (the scrolling part has no bottom padding to hold them up), on a solid bar */
		.mine-actions {
			order: 3;
			position: sticky;
			bottom: 0;
			z-index: 2;
			margin: auto -12px 0;
			padding: 10px 12px calc(10px + env(safe-area-inset-bottom));
			background: #0f0f14;
			border-top: 1px solid rgb(255 255 255 / 0.14);
		}
		.mine-buttons .ok,
		.mine-buttons .log {
			min-height: 48px;
		}
	}
	@media (prefers-reduced-motion: reduce) {
		.shade.intro,
		.deco.intro .cut-lw,
		.deco.intro .cut-r,
		.say,
		.lineup,
		.spot,
		.pchar,
		.pstep,
		.frame.leaving .left,
		.frame.leaving .mine-right {
			animation: none;
		}
		.stage[open],
		.seg {
			animation: none;
			transition: none;
		}
	}
</style>
