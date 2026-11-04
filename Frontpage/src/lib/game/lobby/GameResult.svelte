<script>
	import { t } from '../i18n.svelte.js';
	import { EXP, getLevel } from '../level.js';
	import { ms } from '../motion.js';
	import { playSound } from '../audio.svelte.js';
	import { toast } from '../ui/toast.svelte.js';
	import Tooltip from '../ui/Tooltip.svelte';
	import Dialog from './Dialog.svelte';
	import Icon from './Icon.svelte';
	import LevelIcon from './LevelIcon.svelte';
	import NickName from './NickName.svelte';
	import '../gameStage.css';

	/**
	 * The result board of the legacy client (`roundEnd`): the ranking, what I earned, and the experience gauge animated by the
	 * same steps. It opens two seconds after the game ends; closing it takes me back to the waiting room.
	 */
	let { store } = $props();
	const net = $derived(store.net);
	const result = $derived(store.game?.result);
	const rows = $derived(result?.rows ?? []);
	const mine = $derived(rows.find((r) => r.me));
	const commify = (n) => Number(n).toLocaleString('en-US');

	let open = $state(false);
	let wasOpen = false;
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
		if (open) wasOpen = true;
		else if (wasOpen) {
			wasOpen = false;
			store.closeResult();
		}
	});

	// ---- the gauge: legacy roundEndAnimation, step for step ----
	let bars = $state({ before: 0, current: 0, bonus: 0 });
	let shownLevel = $state(1);
	let scoreText = $state('');
	$effect(() => {
		if (!open || !mine) return;
		const r = mine.reward;
		const s = { exp: mine.before, level: getLevel(mine.before), left: r.exp, _exp: mine.before, _boing: r.baseExp, coef: 0 };
		let raf;
		let settle;
		const draw = (val, before, goal) => `${((val - before) / (goal - before)) * 100}%`;
		const step = () => {
			const goal = EXP[s.level - 1];
			const before = EXP[s.level - 2] || 0;
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
						toast(`${nl}${t('kkutu.js.level')}${t('kkutu.js.accomplished')}`, { icon: 'xp' });
						playSound('lvup');
					}
					s.level = nl;
				}
				raf = requestAnimationFrame(step);
			}
			const going = s.exp - s._exp;
			bars = { before: draw(s._exp, before, goal), current: draw(Math.min(going, s._boing), 0, goal - before), bonus: draw(Math.max(0, going - s._boing), 0, goal - before) };
			shownLevel = s.level;
			scoreText = `${commify(Math.round(s.exp))} / ${commify(goal)}`;
		};
		step();
		// the dialog fades in for half a second before the gauge starts to move
		settle = setTimeout(() => (s.coef = 0.05), ms(500));
		return () => {
			cancelAnimationFrame(raf);
			clearTimeout(settle);
		};
	});

	/** The breakdown shown over the box: legacy explainReward (log entries are "<from><g|h><EXP|MNY><value>"). */
	function explain(r) {
		const rows = (org, target) =>
			r.log
				.filter((item) => item.slice(2, 5) === target)
				.map((item) => {
					const value = Number(item.slice(5));
					return { head: t(`kkutu.js.bonusFrom.${item.charAt(0)}`), body: item.charAt(1) === 'g' ? `+${(org * value).toFixed(1)}` : item.charAt(1) === 'h' ? `+${Math.floor(value)}` : '' };
				});
		return { exp: rows(r.baseExp, 'EXP'), money: rows(r.baseMoney, 'MNY') };
	}
</script>

{#snippet breakdown(r)}
	{@const ex = explain(r)}
	<div style="color:#BBBBBB">
		<h4 style="color:#FFFF44">{t('kkutu.js.scoreGain')}</h4>
		<div>{t('kkutu.js.scoreOrigin')} {r.baseExp}</div>
		{#each ex.exp as row, i (i)}<div>{row.head} {row.body}</div>{/each}
		<h4 style="color:#FFFF44">{t('kkutu.js.moneyGain')}</h4>
		<div>{t('kkutu.js.moneyOrigin')} {r.baseMoney}</div>
		{#each ex.money as row, i (i)}<div>{row.head} {row.body}</div>{/each}
	</div>
{/snippet}

<Dialog bind:open title={t('kkutu.dialog.result.title')}>
	<div class="kkr mx-auto">
		<div class="result-board" role="list">
			{#each rows as r (r.id)}
				{@const u = net.users[r.id]}
				<div role="listitem" data-row={r.id} class="result-board-item {r.me ? 'result-board-me' : ''}">
					<div class="result-board-rank {r.team ? `team-${r.team}` : ''}">{r.rank + 1}</div>
					<div class="result-board-level"><LevelIcon level={r.levelAfter} size={20} maskable={!u?.robot && !r.me} /></div>
					<div class="result-board-name">{#if u}<NickName user={u} showCheck maskable={!u.robot && !r.me} />{/if}</div>
					<div class="result-board-score">{commify(r.score || 0)}{t('kkutu.js.pts')}</div>
					<div class="result-board-reward">{r.reward.exp ? `+${commify(r.reward.exp)}` : '-'}</div>
					<div class="result-board-lvup" style:display={r.levelAfter > r.levelBefore ? 'block' : 'none'}><span class="icon"><Icon name="arrow_upward" size={14} /></span><div>{t('kkutu.js.lvUp')}</div></div>
				</div>
			{/each}
		</div>
		{#snippet box()}
			<div class="result-me" data-exp style:opacity={mine ? 1 : 0}>
				{#if mine}
					<div class="result-me-score" data-reward="exp">{t('kkutu.js.scoreGain')} +{commify(mine.reward.exp)}{#if mine.reward.exp - mine.reward.baseExp > 0}<span class="result-me-bonus">({commify(mine.reward.exp - mine.reward.baseExp)})</span>{/if}</div>
					<div class="result-me-money" data-reward="money">
						{t('kkutu.js.moneyGain')} +{commify(mine.reward.money)}{#if mine.reward.money - mine.reward.baseMoney > 0}<span class="result-me-bonus">({commify(mine.reward.money - mine.reward.baseMoney)})</span>{/if}
						{#if mine.reward.ep}<br /><span class="result-me-bonus" data-reward="ep">{t('kkutu.js.epGain')} +{commify(mine.reward.ep)}</span>{/if}
					</div>
					<div class="result-me-level">
						<div class="result-me-level-head">{t('kkutu.dialog.result.level')}</div>
						<div class="result-me-level-body" data-level>{shownLevel}</div>
					</div>
					<div class="graph result-me-gauge">
						<div class="graph-bar result-me-before-bar" style:width={bars.before}></div>
						<div class="graph-bar result-me-current-bar" style:width={bars.current}></div>
						<div class="graph-bar result-me-bonus-bar" style:width={bars.bonus}></div>
					</div>
					<div class="result-me-score-text">{scoreText}</div>
				{/if}
			</div>
		{/snippet}
		{#if mine}
			{#snippet tip()}{@render breakdown(mine.reward)}{/snippet}
			<Tooltip content={tip} placement="top">{@render box()}</Tooltip>
		{:else}{@render box()}{/if}
		<div class="tail">
			<button type="button" class="kk-btn kk-btn-primary min-h-11 px-6" onclick={() => (open = false)}>{t('kkutu.dialog.result.ok')}</button>
		</div>
	</div>
</Dialog>
