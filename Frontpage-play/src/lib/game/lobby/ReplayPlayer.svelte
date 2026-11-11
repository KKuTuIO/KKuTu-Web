<script>
	import { onDestroy, onMount, untrack } from 'svelte';
	import { t } from '../i18n.svelte.js';
	import { createReplay, SPEEDS } from '../replayPlayer.svelte.js';
	import { createGameAudio } from '../gameAudio.js';
	import { clock } from '../replayPayload.js';
	import Icon from './Icon.svelte';
	import GameHeader from './GameHeader.svelte';
	import GameScreen from './GameScreen.svelte';
	import Tooltip from '../ui/Tooltip.svelte';
	import ChipRow from './ChipRow.svelte';

	/**
	 * A recording on the real game screen, with a player bar under it: play / pause, from the start, and a timeline (with a tick at the start
	 * of every round) to jump along. Space pauses, the arrow keys jump 5 seconds.
	 * @type {{store:any, rec:any, onclose:()=>void}}
	 */
	let { store, rec, onclose } = $props();
	// one player per recording: the page opens a new one for another file
	const replay = untrack(() => createReplay(rec, { meta: store.net.meta, shop: store.shop, onexit: () => onclose(), audio: (net) => createGameAudio(net) }));
	const { view, chapters, duration } = replay;
	let speed = $state(1);
	let box = $state();
	let full = $state(false);
	function fullscreen() {
		if (document.fullscreenElement) document.exitFullscreen();
		else box?.requestFullscreen?.();
	}
	// the chat shows a 리플레이 tab with this game's chat while it plays, and the lobby's music steps aside (see the page)
	onMount(() => {
		store.replayView = replay.store;
		replay.play();
	});
	onDestroy(() => {
		replay.dispose();
		queueMicrotask(() => {
			if (store.replayView === replay.store) store.replayView = null;
		});
	});

	function toggle() {
		if (view.playing) replay.pause();
		else replay.play();
	}
	function key(e) {
		if (e.target instanceof HTMLInputElement && e.target.type !== 'range') return;
		if (e.key === ' ' || e.key === 'k') (e.preventDefault(), toggle());
		else if (e.key === 'ArrowRight') (e.preventDefault(), replay.seek(replay.position() + 5000));
		else if (e.key === 'ArrowLeft') (e.preventDefault(), replay.seek(replay.position() - 5000));
	}
	const pct = (ms) => (duration ? (ms / duration) * 100 : 0);
</script>

<svelte:window onkeydown={key} />

<svelte:document onfullscreenchange={() => (full = document.fullscreenElement === box)} />

<section bind:this={box} aria-label={t('kkutu.js.replay')} class="flex min-h-0 flex-1 flex-col gap-3 {full ? 'bg-(--kk-bg) p-3' : ''}">
	<!-- the game's own header: the room, its rules, the rounds with the 제시어 of each, and the way out -->
	<GameHeader store={replay.store} />
	<GameScreen store={replay.store} />
	<div class="kk-card flex shrink-0 flex-wrap items-center gap-x-3 gap-y-2 px-3 py-2.5 {full ? '' : 'pr-20'}">
		<Tooltip text="{view.playing ? t('play.replay.pause') : t('play.replay.play')} (Space)">
			<button type="button" aria-label={view.playing ? t('play.replay.pause') : t('play.replay.play')} aria-keyshortcuts="Space" onclick={toggle} class="kk-btn kk-btn-primary grid size-11 shrink-0 place-items-center p-0"><Icon name={view.playing ? 'pause' : 'play_arrow'} size={24} fill /></button>
		</Tooltip>
		<Tooltip text={t('play.replay.restart')}><button type="button" aria-label={t('play.replay.restart')} onclick={() => (replay.seek(0), replay.play())} class="kk-btn grid size-11 shrink-0 place-items-center p-0"><Icon name="replay" size={22} /></button></Tooltip>
		<Tooltip text={t('play.replay.back10')}><button type="button" aria-label={t('play.replay.back10')} onclick={() => replay.seek(replay.position() - 10000)} class="kk-btn grid size-11 shrink-0 place-items-center p-0"><Icon name="replay_10" size={22} /></button></Tooltip>
		<Tooltip text={t('play.replay.forward10')}><button type="button" aria-label={t('play.replay.forward10')} onclick={() => replay.seek(replay.position() + 10000)} class="kk-btn grid size-11 shrink-0 place-items-center p-0"><Icon name="forward_10" size={22} /></button></Tooltip>
		<span class="w-24 shrink-0 text-sm font-bold tabular-nums">{clock(view.position)} / {clock(duration)}</span>
		<div class="relative flex h-10 min-w-40 flex-1 items-center">
			<!-- the start of every round, so the timeline reads like chapters -->
			{#each chapters as c (c.at)}<i aria-hidden="true" class="pointer-events-none absolute top-1/2 h-3 w-0.5 -translate-y-1/2 rounded-full bg-(--kk-muted)/70" style:left="{pct(c.at)}%"></i>{/each}
			<input type="range" min="0" max={duration} step="100" value={view.position} aria-label={t('play.replay.seek')} aria-valuetext={clock(view.position)} oninput={(e) => replay.seek(Number(e.currentTarget.value))} style:--p="{pct(view.position)}%" class="kk-seek relative z-10 h-6 w-full cursor-pointer" />
		</div>
		<ChipRow options={SPEEDS.map((v) => ({ value: v, label: `${v}×` }))} bind:value={speed} size="sm" label={t('play.replay.speed')} onchange={(v) => replay.setSpeed(v)} />
		<Tooltip text={full ? t('play.replay.fullscreenExit') : t('play.replay.fullscreen')}><button type="button" aria-label={full ? t('play.replay.fullscreenExit') : t('play.replay.fullscreen')} onclick={fullscreen} class="kk-btn grid size-11 shrink-0 place-items-center p-0"><Icon name={full ? 'fullscreen_exit' : 'fullscreen'} size={22} /></button></Tooltip>
	</div>
</section>

<style>
	.kk-seek {
		appearance: none;
		background: transparent;
		accent-color: var(--kk-accent);
	}
	.kk-seek::-webkit-slider-runnable-track {
		height: 6px;
		border-radius: 999px;
		background: linear-gradient(to right, var(--kk-accent) var(--p, 0%), var(--kk-line) var(--p, 0%));
	}
	.kk-seek::-webkit-slider-thumb {
		appearance: none;
		width: 16px;
		height: 16px;
		margin-top: -5px;
		border-radius: 50%;
		background: var(--kk-accent);
		box-shadow: 0 2px 0 var(--kk-fill-edge);
	}
	.kk-seek::-moz-range-track {
		height: 6px;
		border-radius: 999px;
		background: var(--kk-line);
	}
	.kk-seek::-moz-range-progress {
		height: 6px;
		border-radius: 999px;
		background: var(--kk-accent);
	}
	.kk-seek::-moz-range-thumb {
		width: 16px;
		height: 16px;
		border: 0;
		border-radius: 50%;
		background: var(--kk-accent);
	}
</style>
