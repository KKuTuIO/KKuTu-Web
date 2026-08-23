<script>
  import { createEventDispatcher, onDestroy, onMount, tick } from 'svelte';
  import { fade, scale } from 'svelte/transition';
  import { createReplayModel, formatReplayTime, getReplayState } from './replayModel.js';

  export let detail;

  const dispatch = createEventDispatcher();
  const speedOptions = [0.5, 1, 1.5, 2];
  let dialog;
  let model = null;
  let state = null;
  let currentMs = 0;
  let playing = false;
  let playbackRate = 1;
  let frameId = 0;
  let lastFrameAt = 0;
  let loadedDetail = null;
  let previousBodyOverflow = '';

  $: if (detail && detail !== loadedDetail) {
    loadedDetail = detail;
    pause();
    model = createReplayModel(detail);
    currentMs = 0;
    playbackRate = 1;
  }
  $: state = model ? getReplayState(model, currentMs) : null;

  function close() {
    pause();
    dispatch('close');
  }

  function animationFrame(now) {
    if (!playing || !model) return;
    if (!lastFrameAt) lastFrameAt = now;
    const elapsed = Math.min(250, now - lastFrameAt);
    lastFrameAt = now;
    currentMs = Math.min(model.durationMs, currentMs + elapsed * playbackRate);
    if (currentMs >= model.durationMs) {
      playing = false;
      frameId = 0;
      return;
    }
    frameId = requestAnimationFrame(animationFrame);
  }

  function play() {
    if (!model || playing) return;
    if (currentMs >= model.durationMs) currentMs = 0;
    playing = true;
    lastFrameAt = performance.now();
    frameId = requestAnimationFrame(animationFrame);
  }

  function pause() {
    playing = false;
    lastFrameAt = 0;
    if (frameId) cancelAnimationFrame(frameId);
    frameId = 0;
  }

  function togglePlayback() {
    if (playing) pause();
    else play();
  }

  function seekTo(value) {
    if (!model) return;
    currentMs = Math.min(model.durationMs, Math.max(0, Number(value) || 0));
    if (playing) lastFrameAt = performance.now();
  }

  function skip(deltaMs) {
    seekTo(currentMs + deltaMs);
  }

  function jumpEvent(direction) {
    if (!model?.events?.length) return;
    if (direction < 0) {
      const previous = model.events.filter((event) => event.time < currentMs - 100).pop();
      seekTo(previous?.time ?? 0);
      return;
    }
    const next = model.events.find((event) => event.time > currentMs + 100);
    seekTo(next?.time ?? model.durationMs);
  }

  function handleKeydown(event) {
    if (event.key === 'Escape') {
      event.preventDefault();
      close();
    } else if (event.key === ' ' && !['INPUT', 'SELECT', 'BUTTON'].includes(event.target?.tagName)) {
      event.preventDefault();
      togglePlayback();
    } else if (event.key === 'ArrowLeft') {
      event.preventDefault();
      skip(-5000);
    } else if (event.key === 'ArrowRight') {
      event.preventDefault();
      skip(5000);
    }
  }

  function moremiFile(value, fallback = 'default.png') {
    if (!value) return fallback;
    if (value === 'stars') return 'stars.gif';
    if (value.endsWith('.png') || value.endsWith('.gif')) return value;
    return `${value}.png`;
  }

  function moremiUrl(category, value, fallback = 'default.png') {
    return `https://cdn.kkutu.io/img/kkutu/moremi/${category}/${moremiFile(value, fallback)}`;
  }

  function fallbackMoremi(event, category) {
    const fallback = `https://cdn.kkutu.io/img/kkutu/moremi/${category}/default.png`;
    if (event.currentTarget.src === fallback) {
      event.currentTarget.style.display = 'none';
      event.currentTarget.onerror = null;
      return;
    }
    event.currentTarget.src = fallback;
  }

  function playerCardClass(player) {
    return state?.activeEvent?.playerIndex === player.index ? 'player-card current' : 'player-card';
  }

  function levelIconStyle(level) {
    const spriteLevel = Math.max(0, Math.min(1299, Math.floor(Number(level) || 1) - 1));
    const x = (spriteLevel % 25) * -100;
    const y = Math.floor(spriteLevel / 25) * -100;
    return `background-position: ${x}% ${y}%`;
  }

  onMount(async () => {
    previousBodyOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    await tick();
    dialog?.focus();
  });

  onDestroy(() => {
    pause();
    if (typeof document !== 'undefined') document.body.style.overflow = previousBodyOverflow;
  });
</script>

<svelte:window on:keydown={handleKeydown} />

<div class="replay-overlay" transition:fade={{ duration: 150 }} on:click|self={close} role="presentation">
  <section
    bind:this={dialog}
    class="replay-modal"
    in:scale={{ duration: 180, start: 0.97 }}
    out:fade={{ duration: 100 }}
    role="dialog"
    aria-modal="true"
    aria-label="리플레이 플레이어"
    tabindex="-1"
  >
    <header class="replay-header">
      <div class="header-copy">
        <span class="material-symbols-outlined">movie</span>
        <div>
          <strong>리플레이</strong>
          <span>{model?.roomTitle || '경기 기록'}</span>
        </div>
      </div>
      <button class="close-button" type="button" aria-label="리플레이 닫기" title="닫기 (Esc)" on:click={close}>
        <span class="material-symbols-outlined">close</span>
      </button>
    </header>

    {#if model && state}
      <div class="game-stage">
        <div class="room-strip">
          <span>[{model.gameId ? model.gameId.slice(0, 8) : '-'}] {model.roomTitle}</span>
          <span>{model.modeName} / {model.rule} · 참가자 {model.players.length}명 · 라운드 {model.totalRounds}</span>
        </div>

        <div class="round-label">
          <span>라운드</span>
          <strong>{Math.max(1, state.currentRound)}</strong>
          <span>/ {model.totalRounds}</span>
        </div>

        <div class="game-top">
            <div class="jjoriping-display">
              <div class="eyes" aria-hidden="true">
                <img class="eye-left" src="https://cdn.kkutu.io/img/jjoeyeL.svg" alt="" />
                <img class="nose" src="https://cdn.kkutu.io/img/jjonose.svg" alt="" />
                <img class="eye-right" src="https://cdn.kkutu.io/img/jjoeyeR.svg" alt="" />
              </div>
              <div class:rejected={state.activeEvent?.rejected} class="word-display">
                {state.activeEvent?.label || (currentMs >= model.durationMs ? '경기 종료' : 'READY')}
              </div>
              <div class="turn-bar">
                <span style={`width: ${(1 - state.turnProgress) * 100}%`}></span>
                <b>{state.activeEvent ? `${(state.activeEvent.elapsedTurnMs / 1000).toFixed(1)}초` : ''}</b>
              </div>
              <div class="round-bar">
                <span style={`width: ${state.roundProgress * 100}%`}></span>
                <b>{formatReplayTime(currentMs)}</b>
              </div>
            </div>

            <div class="chain-badge" aria-label={`체인 ${state.acceptedCount}`}>
              <img src="https://cdn.kkutu.io/img/kkutu/righthand.svg" alt="" />
              <strong>{state.acceptedCount}</strong>
            </div>
        </div>

        <div class="event-history" aria-live="polite">
            {#if state.visibleEvents.length}
              {#each state.visibleEvents as replayEvent}
                <div class:rejected={replayEvent.rejected} class="history-chip">
                  <strong>{replayEvent.playerName}</strong>
                  <span>{replayEvent.label}</span>
                  {#if replayEvent.scoreDelta}
                    <em>{replayEvent.scoreDelta > 0 ? '+' : ''}{replayEvent.scoreDelta}</em>
                  {/if}
                </div>
              {/each}
            {:else}
              <div class="history-empty">재생 버튼을 눌러 경기를 시작하세요.</div>
            {/if}
        </div>

        <div class="player-grid">
            {#each model.players as player}
              <article class={playerCardClass(player)}>
                {#if player.placement > 0 && currentMs >= model.durationMs}
                  <span class="placement">{player.placement}위</span>
                {/if}
                <div class="avatar-shell">
                  {#if player.robot}
                    <img class="moremi-layer" src="https://cdn.kkutu.io/img/kkutu/moremi/robot.png" alt="" />
                  {:else}
                    <img class="moremi-layer" src={moremiUrl('back', player.outfit.Mback)} alt="" on:error={(event) => fallbackMoremi(event, 'back')} />
                    <img class="moremi-layer" src={moremiUrl('body', player.outfit.Mbody)} alt="" on:error={(event) => fallbackMoremi(event, 'body')} />
                    <img class="moremi-layer" src={moremiUrl('eye', player.outfit.Meye)} alt="" on:error={(event) => fallbackMoremi(event, 'eye')} />
                    <img class="moremi-layer" src={moremiUrl('mouth', player.outfit.Mmouth)} alt="" on:error={(event) => fallbackMoremi(event, 'mouth')} />
                    <img class="moremi-layer" src={moremiUrl('clothes', player.outfit.Mclothes)} alt="" on:error={(event) => fallbackMoremi(event, 'clothes')} />
                    <img class="moremi-layer" src={moremiUrl('shoes', player.outfit.Mshoes)} alt="" on:error={(event) => fallbackMoremi(event, 'shoes')} />
                    <img class="moremi-layer" src={moremiUrl('head', player.outfit.Mhead)} alt="" on:error={(event) => fallbackMoremi(event, 'head')} />
                    <img class="moremi-layer right-hand" src={moremiUrl('hand', player.outfit.Mrhand)} alt="" on:error={(event) => fallbackMoremi(event, 'hand')} />
                    <img class="moremi-layer" src={moremiUrl('hand', player.outfit.Mlhand)} alt="" on:error={(event) => fallbackMoremi(event, 'hand')} />
                    <img class="moremi-layer" src={moremiUrl('headDeco', player.outfit.MheadDeco)} alt="" on:error={(event) => fallbackMoremi(event, 'headDeco')} />
                    <img class="moremi-layer" src={moremiUrl('dressDeco', player.outfit.MdressDeco)} alt="" on:error={(event) => fallbackMoremi(event, 'dressDeco')} />
                    {#if player.outfit.BDG}
                      <img class="moremi-layer" src={moremiUrl('badge', player.outfit.BDG)} alt="" on:error={(event) => fallbackMoremi(event, 'badge')} />
                    {/if}
                  {/if}
                </div>
                <div class="player-meta">
                  <span class="level-icon" style={levelIconStyle(player.level)} title={`레벨 ${player.level || 1}`} aria-label={`레벨 ${player.level || 1}`}></span>
                  <strong title={player.nickname}>{player.nickname}</strong>
                </div>
                <div class="player-score">{Number(state.scores[player.index] || 0).toLocaleString()}</div>
              </article>
            {/each}
        </div>
      </div>

      <div class="player-controls">
        <input
          class="seek-bar"
          style={`--seek-progress: ${(currentMs / model.durationMs) * 100}%`}
          type="range"
          min="0"
          max={model.durationMs}
          step="50"
          value={currentMs}
          aria-label="리플레이 재생 위치"
          on:input={(event) => seekTo(event.currentTarget.value)}
        />
        <div class="control-row">
          <div class="control-buttons">
            <button type="button" title="이전 기록" aria-label="이전 기록" on:click={() => jumpEvent(-1)}>
              <span class="material-symbols-outlined">skip_previous</span>
            </button>
            <button type="button" title="10초 뒤로" aria-label="10초 뒤로" on:click={() => skip(-10000)}>
              <span class="material-symbols-outlined">replay_10</span>
            </button>
            <button class="play-button" type="button" title={playing ? '일시정지 (Space)' : '재생 (Space)'} aria-label={playing ? '일시정지' : '재생'} on:click={togglePlayback}>
              <span class="material-symbols-outlined">{playing ? 'pause' : 'play_arrow'}</span>
            </button>
            <button type="button" title="10초 앞으로" aria-label="10초 앞으로" on:click={() => skip(10000)}>
              <span class="material-symbols-outlined">forward_10</span>
            </button>
            <button type="button" title="다음 기록" aria-label="다음 기록" on:click={() => jumpEvent(1)}>
              <span class="material-symbols-outlined">skip_next</span>
            </button>
          </div>
          <span class="time-label">{formatReplayTime(currentMs)} / {formatReplayTime(model.durationMs)}</span>
          <label class="speed-control">
            <span>재생 속도</span>
            <select bind:value={playbackRate} aria-label="재생 속도">
              {#each speedOptions as speed}
                <option value={speed}>{speed}×</option>
              {/each}
            </select>
          </label>
        </div>
      </div>
    {:else}
      <div class="unavailable">
        <span class="material-symbols-outlined">error</span>
        <strong>이 경기의 리플레이를 재생할 수 없습니다.</strong>
      </div>
    {/if}
  </section>
</div>

<style>
  .replay-overlay {
    position: fixed;
    inset: 0;
    z-index: 100;
    display: flex;
    align-items: center;
    justify-content: center;
    padding: 16px;
    background: rgba(2, 6, 23, 0.82);
    backdrop-filter: blur(7px);
  }

  .replay-modal {
    width: min(1120px, 100%);
    max-height: calc(100vh - 32px);
    overflow: auto;
    border: 1px solid rgba(148, 163, 184, 0.45);
    border-radius: 18px;
    color: #f8fafc;
    background: #111827;
    box-shadow: 0 28px 90px rgba(0, 0, 0, 0.55);
    outline: none;
  }

  .replay-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    min-height: 58px;
    padding: 10px 14px 10px 18px;
    border-bottom: 1px solid rgba(148, 163, 184, 0.25);
    background: #0f172a;
  }

  .header-copy,
  .header-copy > div {
    display: flex;
    align-items: center;
    min-width: 0;
  }

  .header-copy { gap: 10px; }
  .header-copy > div { gap: 9px; }
  .header-copy strong { flex: none; font-size: 16px; }
  .header-copy span:last-child { overflow: hidden; color: #94a3b8; font-size: 13px; text-overflow: ellipsis; white-space: nowrap; }

  .close-button,
  .control-buttons button {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    border: 0;
    color: #f8fafc;
    background: transparent;
    cursor: pointer;
  }

  .close-button {
    width: 38px;
    height: 38px;
    border-radius: 9px;
  }

  .close-button:hover,
  .control-buttons button:hover { background: rgba(255, 255, 255, 0.12); }

  .game-stage {
    position: relative;
    min-height: 560px;
    padding: 10px 14px 18px;
    overflow: hidden;
    color: #111827;
    background-color: #4caf58;
    background-image: url('https://cdn.kkutu.io/img/kkutu/gamebg.png');
    background-size: 200px 200px;
  }

  .room-strip {
    display: flex;
    justify-content: space-between;
    gap: 16px;
    padding: 4px 9px;
    border-radius: 5px;
    color: #243042;
    background: rgba(248, 250, 252, 0.85);
    font-size: 12px;
  }

  .room-strip span { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

  .round-label {
    display: flex;
    align-items: baseline;
    justify-content: center;
    gap: 5px;
    height: 31px;
    padding-top: 7px;
    color: #fff;
    font-size: 13px;
    text-shadow: 0 1px 2px #17351d;
  }

  .round-label strong { color: #fff45c; font-size: 18px; }

  .game-top {
    position: relative;
    display: flex;
    justify-content: center;
    min-height: 147px;
  }

  .jjoriping-display {
    position: relative;
    width: min(500px, 64vw);
    height: 109px;
    margin-top: 36px;
    padding: 20px 5px 5px;
    border: 2px solid #000;
    border-radius: 0 0 10px 10px;
    background-color: #deaf56;
    box-shadow: 0 4px 0 rgba(0, 0, 0, 0.18);
    box-sizing: border-box;
  }

  .eyes {
    position: absolute;
    inset: -36px -2px auto;
    height: 46px;
    pointer-events: none;
  }

  .eyes img { position: absolute; }
  .eyes .eye-left { top: 0; left: 0; width: 58px; height: 46px; }
  .eyes .eye-right { top: 0; right: 0; width: 58px; height: 46px; }
  .eyes .nose { top: 29px; left: 50%; width: 23px; height: 6px; transform: translateX(-50%); }

  .word-display {
    display: flex;
    align-items: center;
    justify-content: center;
    height: 39px;
    overflow: hidden;
    border-radius: 10px 10px 0 0;
    padding: 0 5px;
    color: #f8fafc;
    background: rgba(0, 0, 0, 0.72);
    font-size: clamp(18px, 3vw, 28px);
    font-weight: 800;
    text-align: center;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .word-display.rejected { color: #fda4af; }

  .turn-bar,
  .round-bar {
    position: relative;
    height: 20px;
    overflow: hidden;
    color: white;
    background: #70712d;
  }

  .round-bar { border-radius: 0 0 9px 9px; background: #223c6c; }
  .turn-bar span,
  .round-bar span { position: absolute; inset: 0 auto 0 0; background: #e6e846; transition: width 80ms linear; }
  .round-bar span { background: #3573e4; }
  .turn-bar b,
  .round-bar b { position: absolute; inset: 2px 8px auto auto; font-size: 11px; font-weight: 700; text-shadow: 0 1px 2px #111; }

  .chain-badge {
    position: absolute;
    top: 25px;
    left: calc(50% + min(330px, 35vw));
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    width: 82px;
    height: 82px;
    color: #fff;
    text-shadow: 0 1px 2px #102a65;
  }

  .chain-badge img { position: absolute; inset: 0; width: 100%; height: 100%; }
  .chain-badge strong { position: relative; z-index: 1; margin-top: 22px; font-size: 27px; line-height: 1; }

  .event-history {
    display: grid;
    grid-template-columns: repeat(5, minmax(0, 1fr));
    gap: 7px;
    min-height: 54px;
    margin-top: 6px;
  }

  .history-chip,
  .history-empty {
    min-width: 0;
    padding: 7px 9px;
    overflow: hidden;
    border-radius: 9px;
    color: #e2e8f0;
    background: rgba(15, 23, 42, 0.9);
    font-size: 11px;
  }

  .history-chip strong,
  .history-chip span { display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
  .history-chip strong { color: #bfdbfe; }
  .history-chip em { color: #86efac; font-style: normal; font-weight: 800; }
  .history-chip.rejected span { color: #fda4af; }
  .history-empty { grid-column: 1 / -1; display: flex; align-items: center; justify-content: center; color: #94a3b8; }

  .player-grid {
    display: grid;
    grid-template-columns: repeat(8, minmax(0, 1fr));
    grid-auto-rows: 174px;
    gap: 7px;
    height: 174px;
    margin-top: 9px;
    overflow-x: hidden;
    overflow-y: scroll;
    scrollbar-color: rgba(15, 23, 42, .55) rgba(255, 255, 255, .18);
    scrollbar-width: thin;
  }

  .player-card {
    position: relative;
    min-width: 0;
    padding: 6px;
    border: 3px solid #d1d5db;
    border-radius: 12px;
    background: #e5e7eb;
    box-shadow: 0 2px 2px rgba(0, 0, 0, .34);
    box-sizing: border-box;
    overflow: hidden;
    transition: transform 160ms ease, border-color 160ms ease, background 160ms ease;
  }

  .player-card.current {
    border-color: #15803d;
    background: #dcfce7;
    box-shadow: 0 0 0 2px rgba(21, 128, 61, .28), 0 2px 2px rgba(0, 0, 0, .34);
  }

  .placement {
    position: absolute;
    z-index: 1;
    top: 4px;
    right: 4px;
    padding: 2px 5px;
    border-radius: 5px;
    color: #fff;
    background: #b45309;
    font-size: 10px;
    font-weight: 900;
  }

  .avatar-shell {
    position: relative;
    display: flex;
    align-items: center;
    justify-content: center;
    aspect-ratio: 1;
    overflow: hidden;
    border-radius: 7px;
    background: #dbeafe;
  }

  .avatar-shell .moremi-layer { position: absolute; inset: 0; width: 100%; height: 100%; object-fit: contain; }
  .avatar-shell .right-hand { transform: scaleX(-1); }

  .player-meta {
    display: flex;
    align-items: center;
    gap: 4px;
    margin-top: 4px;
    font-size: 11px;
  }

  .level-icon {
    display: block;
    flex: none;
    width: 18px;
    height: 18px;
    background-image: url('https://cdn.kkutu.io/img/kkutu/lv/newlv.png');
    background-size: 2560%;
    background-repeat: no-repeat;
  }
  .player-meta strong { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
  .player-score { overflow: hidden; font-family: ui-monospace, SFMono-Regular, Menlo, monospace; font-size: clamp(17px, 2vw, 25px); line-height: 1.1; text-overflow: ellipsis; }

  .player-controls {
    padding: 8px 14px 13px;
    background: #090e1a;
  }

  .seek-bar {
    width: 100%;
    height: 18px;
    margin: 0;
    appearance: none;
    background: transparent;
    cursor: pointer;
  }

  .seek-bar::-webkit-slider-runnable-track {
    height: 5px;
    border-radius: 999px;
    background: linear-gradient(to right, #ef4444 var(--seek-progress), #475569 var(--seek-progress));
  }

  .seek-bar::-moz-range-track { height: 5px; border-radius: 999px; background: #475569; }
  .seek-bar::-moz-range-progress { height: 5px; border-radius: 999px; background: #ef4444; }
  .seek-bar::-webkit-slider-thumb { width: 14px; height: 14px; margin-top: -4.5px; appearance: none; border: 0; border-radius: 50%; background: #ef4444; }
  .seek-bar::-moz-range-thumb { width: 14px; height: 14px; border: 0; border-radius: 50%; background: #ef4444; }

  .control-row {
    display: flex;
    align-items: center;
    gap: 13px;
    min-height: 42px;
  }

  .control-buttons { display: flex; align-items: center; gap: 2px; }
  .control-buttons button { width: 38px; height: 38px; border-radius: 50%; }
  .control-buttons .play-button { width: 42px; height: 42px; background: #f8fafc; color: #0f172a; }
  .control-buttons .play-button:hover { background: #e2e8f0; }
  .time-label { flex: 1; color: #cbd5e1; font-family: ui-monospace, SFMono-Regular, Menlo, monospace; font-size: 13px; }

  .speed-control { display: flex; align-items: center; gap: 8px; color: #cbd5e1; font-size: 12px; }
  .speed-control select { border: 1px solid #475569; border-radius: 7px; padding: 5px 7px; color: #f8fafc; background: #1e293b; }

  .unavailable {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    gap: 12px;
    min-height: 360px;
    color: #cbd5e1;
  }

  .unavailable .material-symbols-outlined { color: #fb7185; font-size: 42px; }

  @media (max-width: 900px) {
    .game-stage { min-height: 520px; }
    .chain-badge { left: auto; right: 18px; width: 66px; height: 66px; }
    .chain-badge strong { font-size: 22px; }
  }

  @media (max-width: 640px) {
    .replay-overlay { padding: 0; }
    .replay-modal { max-height: 100vh; min-height: 100vh; border: 0; border-radius: 0; }
    .header-copy > div { display: block; }
    .header-copy span:last-child { display: block; max-width: 62vw; }
    .room-strip span:last-child { display: none; }
    .game-stage { min-height: 545px; padding-inline: 8px; }
    .jjoriping-display { width: calc(100% - 74px); margin-right: 58px; }
    .chain-badge { top: 30px; right: 2px; width: 58px; height: 58px; }
    .chain-badge strong { font-size: 19px; }
    .event-history { grid-template-columns: repeat(2, minmax(0, 1fr)); }
    .history-chip:nth-last-child(-n+3) { display: none; }
    .player-grid { grid-template-columns: repeat(4, minmax(0, 1fr)); grid-auto-rows: 126px; height: 259px; }
    .player-card { padding: 4px; }
    .player-score { font-size: 16px; }
    .control-row { flex-wrap: wrap; justify-content: center; gap: 6px 10px; }
    .control-buttons { order: 1; }
    .time-label { order: 2; flex: none; min-width: 100px; }
    .speed-control { order: 3; }
    .control-buttons button { width: 34px; height: 34px; }
    .control-buttons .play-button { width: 38px; height: 38px; }
  }

</style>
