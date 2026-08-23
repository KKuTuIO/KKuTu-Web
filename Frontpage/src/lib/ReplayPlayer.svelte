<script>
  import { createEventDispatcher, onDestroy, onMount, tick } from 'svelte';
  import { fade, scale } from 'svelte/transition';
  import { createReplayModel, formatReplayTime, getReplayState } from './replayModel.js';

  export let detail;

  const AUDIO_BASE = 'https://cdn.kkutu.io/media/kkutuio/';
  const speedOptions = [0.5, 1, 1.5, 2];
  const dispatch = createEventDispatcher();
  const sounds = new Map();
  let dialog;
  let stageViewport;
  let playerControls;
  let resizeObserver;
  let stageScale = 1;
  let stageViewportHeight = 410;
  let isFullscreen = false;
  let pseudoFullscreen = false;
  let model = null;
  let state = null;
  let currentMs = 0;
  let playing = false;
  let muted = false;
  let playbackRate = 1;
  let frameId = 0;
  let lastFrameAt = 0;
  let loadedDetail = null;
  let previousBodyOverflow = '';
  let activeTurnSound = null;
  let activeTurnId = '';

  $: if (detail && detail !== loadedDetail) {
    loadedDetail = detail;
    pause();
    model = createReplayModel(detail);
    currentMs = 0;
    playbackRate = 1;
    tick().then(updateStageScale);
  }
  $: state = model ? getReplayState(model, currentMs) : null;
  $: fullscreenActive = isFullscreen || pseudoFullscreen;
  function updateStageScale() {
    if (!stageViewport) return;
    const widthScale = stageViewport.clientWidth / 1000;
    if (fullscreenActive && typeof window !== 'undefined') {
      const controlsHeight = playerControls?.offsetHeight || 76;
      stageViewportHeight = Math.max(1, window.innerHeight - controlsHeight);
      stageScale = Math.max(0.1, Math.min(widthScale, stageViewportHeight / 410));
    } else {
      stageScale = Math.min(1, widthScale);
      stageViewportHeight = 410 * stageScale;
    }
  }

  async function toggleFullscreen() {
    if (!dialog || typeof document === 'undefined') return;
    if (pseudoFullscreen) {
      pseudoFullscreen = false;
      tick().then(updateStageScale);
      return;
    }
    try {
      if (document.fullscreenElement === dialog) {
        await document.exitFullscreen();
        return;
      }
      if (!dialog.requestFullscreen) throw new Error('Fullscreen API is unavailable');
      await dialog.requestFullscreen({ navigationUI: 'hide' });
      if (typeof screen !== 'undefined' && screen.orientation?.lock) {
        try { await screen.orientation.lock('landscape'); } catch (_) {}
      }
    } catch (_) {
      pseudoFullscreen = true;
      tick().then(updateStageScale);
    }
  }

  function handleFullscreenChange() {
    isFullscreen = typeof document !== 'undefined' && document.fullscreenElement === dialog;
    if (!isFullscreen && typeof screen !== 'undefined' && screen.orientation?.unlock) {
      try { screen.orientation.unlock(); } catch (_) {}
    }
    tick().then(updateStageScale);
  }

  function audioFor(key) {
    if (typeof Audio === 'undefined') return null;
    if (!sounds.has(key)) {
      const audio = new Audio(`${AUDIO_BASE}${key}.mp3`);
      audio.preload = 'auto';
      audio.load();
      sounds.set(key, audio);
    }
    return sounds.get(key);
  }

  function playSound(key, offsetMs = 0) {
    if (muted) return null;
    const audio = audioFor(key);
    if (!audio) return null;
    audio.pause();
    try { audio.currentTime = Math.max(0, offsetMs / 1000); } catch (_) {}
    audio.playbackRate = playbackRate;
    audio.preservesPitch = true;
    audio.volume = key.startsWith('T') ? 0.65 : 0.8;
    audio.play().catch(() => {});
    return audio;
  }

  function stopAllAudio() {
    for (const audio of sounds.values()) {
      audio.pause();
      try { audio.currentTime = 0; } catch (_) {}
    }
    activeTurnSound = null;
    activeTurnId = '';
  }

  function syncTurnSound() {
    if (!playing || muted || !model) {
      if (activeTurnSound) activeTurnSound.pause();
      activeTurnSound = null;
      activeTurnId = '';
      return;
    }
    const turn = model.turns.find((item) => currentMs >= item.startTime && currentMs < item.endTime);
    if (!turn) {
      if (activeTurnSound) activeTurnSound.pause();
      activeTurnSound = null;
      activeTurnId = '';
      return;
    }
    if (activeTurnId === turn.id && activeTurnSound) {
      activeTurnSound.playbackRate = playbackRate;
      return;
    }
    if (activeTurnSound) activeTurnSound.pause();
    activeTurnId = turn.id;
    activeTurnSound = playSound(`T${turn.speed}`, currentMs - turn.startTime);
  }

  function processAudioCues(fromMs, toMs) {
    if (!model || muted || toMs < fromMs) return;
    for (const cue of model.audioCues) {
      if (cue.type === 'turn' || cue.time <= fromMs || cue.time > toMs) continue;
      if (cue.type === 'letter' && activeTurnSound) {
        activeTurnSound.pause();
        try { activeTurnSound.currentTime = 0; } catch (_) {}
        activeTurnSound = null;
        activeTurnId = '';
      }
      playSound(cue.sound);
    }
  }

  function close() {
    pause();
    pseudoFullscreen = false;
    if (typeof document !== 'undefined' && document.fullscreenElement === dialog) {
      document.exitFullscreen().catch(() => {});
    }
    dispatch('close');
  }

  function animationFrame(now) {
    if (!playing || !model) return;
    if (!lastFrameAt) lastFrameAt = now;
    const elapsed = Math.min(250, now - lastFrameAt);
    const previousMs = currentMs;
    lastFrameAt = now;
    currentMs = Math.min(model.durationMs, currentMs + elapsed * playbackRate);
    processAudioCues(previousMs, currentMs);
    syncTurnSound();
    if (currentMs >= model.durationMs) {
      playing = false;
      stopAllAudio();
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
    processAudioCues(currentMs - 1, currentMs);
    syncTurnSound();
    frameId = requestAnimationFrame(animationFrame);
  }

  function pause() {
    playing = false;
    lastFrameAt = 0;
    if (frameId) cancelAnimationFrame(frameId);
    frameId = 0;
    stopAllAudio();
  }

  function togglePlayback() {
    if (playing) pause();
    else play();
  }

  function toggleMute() {
    muted = !muted;
    if (muted) stopAllAudio();
    else syncTurnSound();
  }

  function changePlaybackRate(event) {
    playbackRate = Number(event.currentTarget.value) || 1;
    if (activeTurnSound) activeTurnSound.playbackRate = playbackRate;
    for (const [key, audio] of sounds) if (!key.startsWith('T') && !audio.paused) audio.playbackRate = playbackRate;
  }

  function seekTo(value) {
    if (!model) return;
    currentMs = Math.min(model.durationMs, Math.max(0, Number(value) || 0));
    stopAllAudio();
    if (playing) {
      lastFrameAt = performance.now();
      syncTurnSound();
    }
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
      if (pseudoFullscreen) {
        pseudoFullscreen = false;
        tick().then(updateStageScale);
        return;
      }
      if (isFullscreen && typeof document !== 'undefined') {
        document.exitFullscreen().catch(() => {});
        return;
      }
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
    const classes = ['game-user'];
    if (state?.activeTurn?.playerIndex === player.index) classes.push('game-user-current');
    const timeout = [...(model?.events || [])].reverse().find((event) => event.kind === 'CTO' && event.time <= currentMs);
    if (timeout?.playerIndex === player.index && currentMs < timeout.time + 3000) classes.push('game-user-bomb');
    return classes.join(' ');
  }

  function levelIconStyle(level) {
    const spriteLevel = Math.max(0, Math.floor(Number(level) || 1) - 1);
    const x = (spriteLevel % 25) * -100;
    const y = Math.floor(spriteLevel / 25) * -100;
    return `background-position: ${x}% ${y}%; background-size: 2560%`;
  }

  function scoreText(value) {
    const score = Math.round(Number(value) || 0);
    if (score < 0) return `-${String(Math.abs(score)).padStart(4, '0')}`;
    if (score > 99999) return `${String(Math.round(score * 0.001)).padStart(4, '0')}k`;
    return String(score).padStart(5, '0');
  }

  onMount(async () => {
    previousBodyOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    await tick();
    dialog?.focus();
    updateStageScale();
    if (typeof ResizeObserver !== 'undefined') {
      resizeObserver = new ResizeObserver(updateStageScale);
      if (stageViewport) resizeObserver.observe(stageViewport);
    }
    document.addEventListener('fullscreenchange', handleFullscreenChange);
    for (const key of ['game_start', 'round_start', 'fail', 'timeout', 'mission', 'kung', 'Al']) audioFor(key);
    for (let speed = 0; speed <= 10; speed++) {
      audioFor(`T${speed}`);
      audioFor(`As${speed}`);
      audioFor(`K${speed}`);
    }
  });

  onDestroy(() => {
    pause();
    resizeObserver?.disconnect();
    if (typeof document !== 'undefined') {
      document.removeEventListener('fullscreenchange', handleFullscreenChange);
      if (document.fullscreenElement === dialog) document.exitFullscreen().catch(() => {});
      document.body.style.overflow = previousBodyOverflow;
    }
  });
</script>

<svelte:window on:keydown={handleKeydown} on:resize={updateStageScale} />

<div class="replay-overlay" transition:fade={{ duration: 150 }} on:click|self={close} role="presentation">
  <section
    bind:this={dialog}
    class="replay-modal"
    class:fullscreen-active={fullscreenActive}
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
      <div class="game-stage-viewport" bind:this={stageViewport} style={`height: ${stageViewportHeight}px`}>
        <div class="game-stage-shell" style={`width: ${1000 * stageScale}px; height: ${410 * stageScale}px`}>
          <div class="game-stage" style={`transform: scale(${stageScale})`}>
          <div class="room-strip">
            <span>[{model.gameId ? model.gameId.slice(0, 8) : '-'}] {model.roomTitle}</span>
            <span>{model.modeName} · 참가자 {model.players.length}명 · 라운드 {state.currentRound}/{model.totalRounds} · {Math.round(model.roomTimeMs / 1000)}초</span>
          </div>
          <div class="game-surface">
            <div class="game-background" aria-hidden="true">
              {#each Array(10) as _}
                <img src="https://cdn.kkutu.io/img/kkutu/gamebg.png" referrerpolicy="no-referrer" alt="" />
              {/each}
            </div>
            <div class="mission-hand" aria-hidden="true">
              <img src="https://cdn.kkutu.io/img/kkutu/lefthand.png" referrerpolicy="no-referrer" alt="" />
            </div>

            <div class="jjoriping">
              <img class="jjo-eye-left" src="https://cdn.kkutu.io/img/jjoeyeL.svg" referrerpolicy="no-referrer" alt="" />
              <img class="jjo-nose" src="https://cdn.kkutu.io/img/jjonose.svg" referrerpolicy="no-referrer" alt="" />
              <img class="jjo-eye-right" src="https://cdn.kkutu.io/img/jjoeyeR.svg" referrerpolicy="no-referrer" alt="" />
              <div class="jjo-display-bar">
                <div
                  class:game-fail-text={state.displayMode === 'failure'}
                  class:letter-mode={state.displayMode === 'letters'}
                  class="jjo-display"
                  style={`--effect-duration: ${2000 / playbackRate}ms; animation-play-state: ${playing ? 'running' : 'paused'}`}
                >
                  {#if state.displayMode === 'letters'}
                    {#each state.displayLetters as letter (letter.index)}
                      <span class:shown={letter.visible} class="display-text">{letter.char}</span>
                    {/each}
                  {:else}
                    <span>{state.displayText}</span>
                  {/if}
                </div>
                <div class="graph jjo-turn-time">
                  <div class="graph-bar" style={`width: ${state.turnRatio * 100}%`}></div>
                  <b>{(state.turnRemainingMs / 1000).toFixed(1)}초</b>
                </div>
                <div class:round-extreme={state.roundRemainingMs <= 5000} class="graph jjo-round-time">
                  <div class="graph-bar" style={`width: ${state.roundRatio * 100}%`}></div>
                  <b>{(state.roundRemainingMs / 1000).toFixed(1)}초</b>
                </div>
              </div>
            </div>

            <div class="chain-hand" aria-label={`체인 ${state.acceptedCount}`}>
              <img src="https://cdn.kkutu.io/img/kkutu/righthand.png" referrerpolicy="no-referrer" alt="" />
              <strong>{state.acceptedCount}</strong>
            </div>

            <div class="rounds" aria-label={`현재 ${state.currentRound}라운드`}>
              {#each model.roundTitles as title, index}
                <span class:rounds-current={index + 1 === state.currentRound}>{title}</span>
              {/each}
            </div>

            <div class="history-holder" aria-live="polite">
              <div class="history">
                {#each state.visibleEvents as replayEvent (replayEvent.id)}
                  <div class="history-item" title={replayEvent.description}>{replayEvent.label}</div>
                {/each}
              </div>
            </div>

            <div class="game-body">
              {#each model.players as player}
                <article class={playerCardClass(player)}>
                  {#if player.placement > 0 && currentMs >= model.durationMs}
                    <span class="placement">{player.placement}위</span>
                  {/if}
                  <div class="moremi game-user-image">
                    {#if player.robot}
                      <img class="moremi-layer" src="https://cdn.kkutu.io/img/kkutu/moremi/robot.png" referrerpolicy="no-referrer" alt="" />
                    {:else}
                      <img class="moremi-layer" src={moremiUrl('back', player.outfit.Mback)} referrerpolicy="no-referrer" alt="" on:error={(event) => fallbackMoremi(event, 'back')} />
                      <img class="moremi-layer" src={moremiUrl('body', player.outfit.Mbody)} referrerpolicy="no-referrer" alt="" on:error={(event) => fallbackMoremi(event, 'body')} />
                      <img class="moremi-layer" src={moremiUrl('shoes', player.outfit.Mshoes)} referrerpolicy="no-referrer" alt="" on:error={(event) => fallbackMoremi(event, 'shoes')} />
                      <img class="moremi-layer" src={moremiUrl('clothes', player.outfit.Mclothes)} referrerpolicy="no-referrer" alt="" on:error={(event) => fallbackMoremi(event, 'clothes')} />
                      <img class="moremi-layer" src={moremiUrl('eye', player.outfit.Meye)} referrerpolicy="no-referrer" alt="" on:error={(event) => fallbackMoremi(event, 'eye')} />
                      <img class="moremi-layer" src={moremiUrl('mouth', player.outfit.Mmouth)} referrerpolicy="no-referrer" alt="" on:error={(event) => fallbackMoremi(event, 'mouth')} />
                      <img class="moremi-layer" src={moremiUrl('head', player.outfit.Mhead)} referrerpolicy="no-referrer" alt="" on:error={(event) => fallbackMoremi(event, 'head')} />
                      <img class="moremi-layer" src={moremiUrl('eyeDeco', player.outfit.MeyeDeco)} referrerpolicy="no-referrer" alt="" on:error={(event) => fallbackMoremi(event, 'eyeDeco')} />
                      <img class="moremi-layer" src={moremiUrl('faceDeco', player.outfit.MfaceDeco)} referrerpolicy="no-referrer" alt="" on:error={(event) => fallbackMoremi(event, 'faceDeco')} />
                      <img class="moremi-layer right-hand" src={moremiUrl('hand', player.outfit.Mrhand)} referrerpolicy="no-referrer" alt="" on:error={(event) => fallbackMoremi(event, 'hand')} />
                      <img class="moremi-layer" src={moremiUrl('hand', player.outfit.Mlhand)} referrerpolicy="no-referrer" alt="" on:error={(event) => fallbackMoremi(event, 'hand')} />
                      <img class="moremi-layer" src={moremiUrl('headDeco', player.outfit.MheadDeco)} referrerpolicy="no-referrer" alt="" on:error={(event) => fallbackMoremi(event, 'headDeco')} />
                      <img class="moremi-layer" src={moremiUrl('dressDeco', player.outfit.MdressDeco)} referrerpolicy="no-referrer" alt="" on:error={(event) => fallbackMoremi(event, 'dressDeco')} />
                      <img class="moremi-layer" src={moremiUrl('front', player.outfit.Mfront)} referrerpolicy="no-referrer" alt="" on:error={(event) => fallbackMoremi(event, 'front')} />
                      {#if player.outfit.BDG}
                        <img class="moremi-layer" src={moremiUrl('badge', player.outfit.BDG)} referrerpolicy="no-referrer" alt="" on:error={(event) => fallbackMoremi(event, 'badge')} />
                      {/if}
                    {/if}
                  </div>
                  <div class="game-user-title">
                    <span class="game-user-level" title={`레벨 ${player.level || 1}`} style={levelIconStyle(player.level)}></span>
                    <strong class="game-user-name" title={player.nickname}>{player.nickname}</strong>
                  </div>
                  <div class="game-user-score" aria-label={`${state.scores[player.index] || 0}점`}>
                    {#each scoreText(state.scores[player.index] || 0) as digit}
                      <span>{digit}</span>
                    {/each}
                  </div>
                  {#each state.scorePopups.filter((popup) => popup.playerIndex === player.index) as popup (popup.id)}
                    <div
                      class:bonus={popup.bonus}
                      class:lost={popup.value < 0}
                      class="delta-score"
                      style={`--effect-duration: ${2000 / playbackRate}ms; animation-play-state: ${playing ? 'running' : 'paused'}`}
                    >
                      {popup.value > 0 ? '+' : ''}{popup.value}
                    </div>
                  {/each}
                </article>
              {/each}
            </div>
          </div>
          </div>
        </div>
      </div>

      <div class="player-controls" bind:this={playerControls}>
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
            <button type="button" title="이전 기록" aria-label="이전 기록" on:click={() => jumpEvent(-1)}><span class="material-symbols-outlined">skip_previous</span></button>
            <button type="button" title="10초 뒤로" aria-label="10초 뒤로" on:click={() => skip(-10000)}><span class="material-symbols-outlined">replay_10</span></button>
            <button class="play-button" type="button" title={playing ? '일시정지 (Space)' : '재생 (Space)'} aria-label={playing ? '일시정지' : '재생'} on:click={togglePlayback}>
              <span class="material-symbols-outlined">{playing ? 'pause' : 'play_arrow'}</span>
            </button>
            <button type="button" title="10초 앞으로" aria-label="10초 앞으로" on:click={() => skip(10000)}><span class="material-symbols-outlined">forward_10</span></button>
            <button type="button" title="다음 기록" aria-label="다음 기록" on:click={() => jumpEvent(1)}><span class="material-symbols-outlined">skip_next</span></button>
            <button type="button" title={muted ? '소리 켜기' : '음소거'} aria-label={muted ? '소리 켜기' : '음소거'} on:click={toggleMute}><span class="material-symbols-outlined">{muted ? 'volume_off' : 'volume_up'}</span></button>
          </div>
          <span class="time-label">{formatReplayTime(currentMs)} / {formatReplayTime(model.durationMs)}</span>
          <label class="speed-control">
            <span>재생 속도</span>
            <select value={playbackRate} on:change={changePlaybackRate} aria-label="재생 속도">
              {#each speedOptions as speed}<option value={speed}>{speed}×</option>{/each}
            </select>
          </label>
          <button
            class="fullscreen-button"
            type="button"
            title={fullscreenActive ? '전체화면 종료' : '전체화면'}
            aria-label={fullscreenActive ? '전체화면 종료' : '전체화면'}
            on:click={toggleFullscreen}
          >
            <span class="material-symbols-outlined">{fullscreenActive ? 'fullscreen_exit' : 'fullscreen'}</span>
          </button>
        </div>
      </div>
    {:else}
      <div class="unavailable"><span class="material-symbols-outlined">error</span><strong>이 경기의 리플레이를 재생할 수 없습니다.</strong></div>
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
    width: min(1040px, 100%);
    max-height: calc(100vh - 32px);
    overflow: auto;
    border: 1px solid rgba(148, 163, 184, 0.45);
    border-radius: 14px;
    color: #f8fafc;
    background: #111827;
    box-shadow: 0 28px 90px rgba(0, 0, 0, 0.55);
    outline: none;
  }

  .replay-modal:fullscreen,
  .replay-modal.fullscreen-active {
    width: 100vw;
    height: 100vh;
    max-height: none;
    display: flex;
    flex-direction: column;
    overflow: hidden;
    border: 0;
    border-radius: 0;
    background: #000;
    overscroll-behavior: none;
  }
  .replay-modal.fullscreen-active:not(:fullscreen) {
    position: fixed;
    inset: 0;
    z-index: 1000;
  }
  .replay-modal:fullscreen .replay-header,
  .replay-modal.fullscreen-active .replay-header { display: none; }

  .replay-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    min-height: 54px;
    padding: 8px 12px 8px 16px;
    border-bottom: 1px solid rgba(148, 163, 184, 0.25);
    background: #0f172a;
  }

  .header-copy, .header-copy > div { display: flex; align-items: center; min-width: 0; }
  .header-copy { gap: 10px; }
  .header-copy > div { gap: 9px; }
  .header-copy strong { flex: none; font-size: 16px; }
  .header-copy span:last-child { overflow: hidden; color: #94a3b8; font-size: 13px; text-overflow: ellipsis; white-space: nowrap; }

  .close-button, .control-buttons button {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    border: 0;
    color: #f8fafc;
    background: transparent;
    cursor: pointer;
  }
  .close-button { width: 38px; height: 38px; border-radius: 9px; }
  .close-button:hover, .control-buttons button:hover { background: rgba(255, 255, 255, 0.12); }

  .game-stage-viewport {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 100%;
    flex: none;
    overflow: hidden;
    background: #4caf58;
  }
  .replay-modal:fullscreen .game-stage-viewport,
  .replay-modal.fullscreen-active .game-stage-viewport { flex: 1 1 auto; background: #000; }
  .game-stage-shell { position: relative; flex: none; overflow: hidden; }
  .game-stage {
    width: 1000px;
    height: 410px;
    color: #111;
    background: #ddd;
    transform-origin: left top;
    font-family: Arial, 'Noto Sans KR', sans-serif;
  }
  .room-strip {
    display: flex;
    align-items: center;
    justify-content: space-between;
    width: 1000px;
    height: 20px;
    padding: 0 8px;
    background: #ddd;
    box-sizing: border-box;
    font-size: 12px;
  }
  .room-strip span { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
  .game-surface {
    position: relative;
    width: 1000px;
    height: 390px;
    overflow: hidden;
    background-color: #4caf58;
  }
  .game-background {
    position: absolute;
    inset: 0;
    display: grid;
    grid-template-columns: repeat(5, 200px);
    grid-template-rows: repeat(2, 200px);
    width: 1000px;
    height: 400px;
    overflow: hidden;
  }
  .game-background img { display: block; width: 200px; height: 200px; }

  .mission-hand, .chain-hand {
    position: absolute;
    top: 50px;
    width: 100px;
    height: 100px;
    color: #eee;
    font-size: 24px;
    font-weight: bold;
    text-align: center;
    text-shadow: 0 1px 5px #141414;
  }
  .mission-hand { left: 105px; }
  .chain-hand { left: 785px; }
  .mission-hand img, .chain-hand img { position: absolute; inset: 0; width: 100px; height: 100px; }
  .chain-hand strong { position: relative; z-index: 1; display: block; padding-top: 55px; }

  .jjoriping { position: absolute; top: 0; left: 250px; width: 500px; height: 145px; }
  .jjoriping > img { position: absolute; z-index: 1; }
  .jjo-eye-left { top: 11px; left: 0; width: 58px; height: 46px; }
  .jjo-nose { top: 49px; left: 239px; width: 23px; height: 6px; }
  .jjo-eye-right { top: 11px; left: 442px; width: 58px; height: 46px; }
  .jjo-display-bar {
    position: absolute;
    top: 36px;
    left: 0;
    width: 486px;
    height: 80px;
    padding: 20px 5px 5px;
    border: 2px solid #000;
    border-radius: 0 0 10px 10px;
    background: #deaf56;
    box-sizing: content-box;
  }
  .jjo-display {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 476px;
    height: 23px;
    padding: 8px 5px;
    overflow: hidden;
    border-radius: 10px 10px 0 0;
    color: #eee;
    background: rgba(0, 0, 0, 0.7);
    font-size: 20px;
    text-align: center;
    white-space: nowrap;
    box-sizing: content-box;
  }
  .display-text { width: 20px; flex: 0 0 20px; visibility: hidden; text-align: center; }
  .display-text.shown { visibility: visible; animation: word-hit 0.1s ease-out; }
  .game-fail-text { color: #ff7777; animation: fail-blink var(--effect-duration, 2s) linear; }
  .graph {
    position: relative;
    width: 484px;
    height: 20px;
    overflow: hidden;
    border-right: 1px solid rgba(0, 0, 0, 0.7);
    border-left: 1px solid rgba(0, 0, 0, 0.7);
    color: #fff;
    text-align: right;
    text-shadow: 0 1px 3px #141414;
    box-sizing: content-box;
  }
  .graph-bar { height: 16px; padding-top: 4px; box-sizing: content-box; transition: width 80ms linear; }
  .graph b { position: absolute; top: 4px; right: 4px; z-index: 1; font-size: 11px; line-height: 12px; }
  .jjo-turn-time { background: #70712d; }
  .jjo-round-time { border-bottom: 1px solid rgba(0, 0, 0, 0.7); border-radius: 0 0 10px 10px; background: #223c6c; }
  .jjo-turn-time .graph-bar { background: #e6e846; }
  .jjo-round-time .graph-bar { background: #3573e4; }
  .round-extreme { background: #ff6d6d !important; }

  .rounds {
    position: absolute;
    top: 4px;
    left: 5px;
    z-index: 2;
    width: 990px;
    color: #fff;
    text-align: center;
    text-shadow: 0 1px 1px #141414;
    pointer-events: none;
  }
  .rounds span { margin: 0 3px; font-size: 12px; }
  .rounds .rounds-current { color: #ffff3b; font-size: 16px; }

  .history-holder { position: absolute; top: 150px; left: 5px; width: 990px; height: 40px; overflow: hidden; }
  .history { display: flex; width: 1200px; height: 42px; }
  .history-item {
    width: 200px;
    height: 28px;
    flex: 0 0 200px;
    padding: 4px 0;
    margin: 3px;
    overflow: hidden;
    border-radius: 10px;
    color: #eee;
    background: #232323;
    font-size: 12px;
    line-height: 28px;
    text-align: center;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .game-body {
    position: absolute;
    top: 195px;
    left: 4px;
    display: flex;
    flex-wrap: wrap;
    align-content: flex-start;
    width: 992px;
    height: 195px;
    overflow-x: hidden;
    overflow-y: scroll;
    scrollbar-width: none;
    scroll-behavior: smooth;
  }
  .game-body::-webkit-scrollbar { display: none; }
  .game-user {
    position: relative;
    width: 110px;
    height: 167px;
    flex: 0 0 110px;
    padding: 1px;
    margin: 13px 3px 3px;
    overflow: visible;
    border: 3px solid #ddd;
    border-radius: 10px;
    background: #ddd;
    box-shadow: 0 1px 1px #141414;
    box-sizing: content-box;
    transition: all 300ms ease;
  }
  .game-user-current { height: 177px; margin-top: 0; background: #dfd; animation: current-blink 2s linear infinite; }
  .game-user-bomb { border-color: #f66; }
  .game-user-image { position: relative; width: 100px; height: 100px; margin: 3px 5px; }
  .moremi-layer { position: absolute; inset: 0; width: 100%; height: 100%; object-fit: contain; }
  .right-hand { transform: scaleX(-1); }
  .game-user-title { display: flex; height: 22px; }
  .game-user-level {
    position: relative;
    width: 18px;
    height: 18px;
    flex: 0 0 18px;
    margin: 1px;
    background-image: url('https://cdn.kkutu.io/img/kkutu/lv/newlv.png');
  }
  .game-user-name {
    width: 87px;
    height: 20px;
    padding-left: 3px;
    margin: 3px 0;
    overflow: hidden;
    font-size: 15px;
    line-height: 20px;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  .game-user-score {
    display: flex;
    width: 100px;
    padding: 0 5px;
    overflow: hidden;
    border-radius: 10px;
    font-size: 30px;
    font-weight: bold;
    line-height: 32px;
    box-sizing: content-box;
  }
  .game-user-score span { width: 20px; flex: 0 0 20px; text-align: center; }
  .placement { position: absolute; top: 3px; right: 3px; z-index: 5; padding: 2px 4px; border-radius: 4px; color: #fff; background: #b45309; font-size: 10px; font-weight: bold; }
  .delta-score {
    position: absolute;
    z-index: 10;
    top: 157px;
    left: 0;
    width: 100%;
    color: #25f;
    font-weight: bold;
    text-align: center;
    text-shadow: 0 1px 2px #000033;
    pointer-events: none;
    animation: score-going var(--effect-duration, 2s) ease 1 both;
  }
  .delta-score.bonus { color: #66f; }
  .delta-score.lost { color: #f59e9e; }

  .player-controls { flex: none; padding: 8px 14px 13px; background: #090e1a; }
  .seek-bar { width: 100%; height: 18px; margin: 0; appearance: none; background: transparent; cursor: pointer; }
  .seek-bar::-webkit-slider-runnable-track { height: 5px; border-radius: 999px; background: linear-gradient(to right, #ef4444 var(--seek-progress), #475569 var(--seek-progress)); }
  .seek-bar::-moz-range-track { height: 5px; border-radius: 999px; background: #475569; }
  .seek-bar::-moz-range-progress { height: 5px; border-radius: 999px; background: #ef4444; }
  .seek-bar::-webkit-slider-thumb { width: 14px; height: 14px; margin-top: -4.5px; appearance: none; border: 0; border-radius: 50%; background: #ef4444; }
  .seek-bar::-moz-range-thumb { width: 14px; height: 14px; border: 0; border-radius: 50%; background: #ef4444; }
  .control-row { display: flex; align-items: center; gap: 13px; min-height: 42px; }
  .control-buttons { display: flex; align-items: center; gap: 2px; }
  .control-buttons button { width: 38px; height: 38px; border-radius: 50%; }
  .control-buttons .play-button { width: 42px; height: 42px; color: #0f172a; background: #f8fafc; }
  .control-buttons .play-button:hover { background: #e2e8f0; }
  .time-label { flex: 1; color: #cbd5e1; font-family: ui-monospace, SFMono-Regular, Menlo, monospace; font-size: 13px; }
  .speed-control { display: flex; align-items: center; gap: 8px; color: #cbd5e1; font-size: 12px; }
  .speed-control select { padding: 5px 7px; border: 1px solid #475569; border-radius: 7px; color: #f8fafc; background: #1e293b; }
  .fullscreen-button {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 38px;
    height: 38px;
    flex: none;
    border: 0;
    border-radius: 50%;
    color: #f8fafc;
    background: transparent;
    cursor: pointer;
  }
  .fullscreen-button:hover { background: rgba(255, 255, 255, 0.12); }
  .unavailable { display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 12px; min-height: 360px; color: #cbd5e1; }
  .unavailable .material-symbols-outlined { color: #fb7185; font-size: 42px; }

  @keyframes current-blink { 0%, 100% { border-color: #3eff3e; } 50% { border-color: #009000; } }
  @keyframes fail-blink { 0%, 50% { text-decoration: line-through; } 25%, 75%, 100% { text-decoration: inherit; } }
  @keyframes word-hit { from { margin-top: -6px; font-size: 36px; } to { margin-top: 0; font-size: 20px; } }
  @keyframes score-going {
    0% { margin-top: -80px; font-size: 24px; opacity: 1; }
    10% { margin-top: -95px; font-size: 36px; opacity: 0.95; }
    100% { margin-top: -25px; font-size: 20px; opacity: 0.1; }
  }

  @media (max-width: 640px) {
    .replay-overlay { padding: 0; }
    .replay-modal { min-height: 100vh; max-height: 100vh; border: 0; border-radius: 0; }
    .header-copy > div { display: block; }
    .header-copy span:last-child { display: block; max-width: 60vw; }
    .control-row { flex-wrap: wrap; justify-content: center; gap: 6px 10px; }
    .control-buttons { order: 1; }
    .time-label { order: 2; flex: none; min-width: 96px; }
    .speed-control { order: 3; }
    .fullscreen-button { order: 4; }
    .control-buttons button { width: 32px; height: 32px; }
    .control-buttons .play-button { width: 38px; height: 38px; }
  }
</style>
