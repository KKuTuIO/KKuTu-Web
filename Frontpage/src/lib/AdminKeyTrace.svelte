<script>
  export let gameId = '';
  export let players = [];

  let isOpen = false;
  let loading = false;
  let loaded = false;
  let loadError = '';
  let payload = null;
  let selectedRound = null;
  let expandedKey = '';

  const EVENT_LABELS = ['beforeinput', 'input', 'compositionstart', 'compositionupdate', 'compositionend'];
  const INPUT_TYPE_LABELS = [
    '-',
    'insertText',
    'insertCompositionText',
    'deleteCompositionText',
    'deleteContentBackward',
    'deleteContentForward',
    'insertLineBreak',
    'insertParagraph'
  ];
  const STATUS_LABELS = ['통과', '단어 오류'];

  function decodeEntry(row, index) {
    if (!Array.isArray(row) || !Array.isArray(row[10])) return null;
    let value = [];
    let committedLength = 0;
    const compact = [];
    const transitions = [];

    for (let transitionIndex = 0; transitionIndex < row[10].length; transitionIndex++) {
      const delta = row[10][transitionIndex];
      if (!Array.isArray(delta) || delta.length < 7) continue;
      const prefix = Math.max(0, Math.min(value.length, Number(delta[4]) || 0));
      const maxSuffix = Math.max(0, value.length - prefix);
      const suffix = Math.max(0, Math.min(maxSuffix, Number(delta[5]) || 0));
      const inserted = Array.from(String(delta[6] || ''));
      const next = [
        ...value.slice(0, prefix),
        ...inserted,
        ...(suffix ? value.slice(value.length - suffix) : [])
      ];
      const nextValue = next.join('');
      const eventCode = Math.max(0, Math.min(4, Number(delta[1]) || 0));
      const changed = nextValue !== value.join('');

      if (changed) {
        if (next.length === 0) {
          compact.push('//');
          committedLength = 0;
        } else if (next.length < committedLength) {
          compact.push(`//${nextValue}`);
          committedLength = 0;
        } else {
          const fragment = next.slice(committedLength).join('');
          compact.push(fragment || nextValue);
        }
      }
      if (eventCode === 4) {
        compact.push('+');
        committedLength = next.length;
      }

      value = next;
      transitions.push({
        index: transitionIndex + 1,
        deltaMs: Math.max(0, Number(delta[0]) || 0),
        event: EVENT_LABELS[eventCode],
        inputType: typeof delta[2] === 'number'
          ? (INPUT_TYPE_LABELS[Math.max(0, Math.min(7, delta[2]))] || `type#${delta[2]}`)
          : String(delta[2] || '-'),
        composing: Number(delta[3]) === 1,
        value: nextValue
      });
    }

    const playerIndex = Math.max(0, Number(row[0]) || 0);
    return {
      key: `${index}:${playerIndex}:${Number(row[2]) || 0}`,
      playerIndex,
      nickname: players[playerIndex]?.nickname || `Player#${playerIndex}`,
      round: Math.max(0, Number(row[1]) || 0),
      elapsedGameMs: Math.max(0, Number(row[2]) || 0),
      disposition: Math.max(0, Math.min(1, Number(row[3]) || 0)),
      status: String(row[4] || '') === 'UNSUBMITTED_AT_GAME_END'
        ? '미제출 종료'
        : String(row[4] || '') === 'UNSUBMITTED_TRACE_DIAGNOSTIC'
          ? '미제출 패킷'
        : STATUS_LABELS[Math.max(0, Math.min(1, Number(row[3]) || 0))],
      resultCode: String(row[4] || '-'),
      scope: `${String(row[5] || '-')}:${String(row[6] || '-')}`,
      inputMismatch: Number(row[7]) === 1,
      chainMismatch: Number(row[8]) === 1,
      complete: Number(row[9]) === 1,
      transitionOverflowCount: Math.max(0, Number(row[11]) || 0),
      packetRejections: Array.isArray(row[12])
        ? row[12].slice(0, 8).map((reason) => String(reason || '')).filter(Boolean)
        : [],
      compact: compact.join('').replace(/\++/g, '+').replace(/\+$/, ''),
      transitions
    };
  }

  $: entries = isOpen && Array.isArray(payload?.e)
    ? payload.e.map(decodeEntry).filter(Boolean)
    : [];
  $: rounds = [...new Set(entries.map((entry) => entry.round))].sort((a, b) => a - b);
  $: visibleEntries = selectedRound === null
    ? entries
    : entries.filter((entry) => entry.round === selectedRound);

  function roundButtonClass(selected) {
    return `rounded-lg border px-3 py-1 text-xs font-semibold ${
      selected
        ? 'border-amber-500 bg-amber-500 text-white'
        : 'border-amber-300 hover:bg-amber-100 dark:border-amber-700 dark:hover:bg-amber-950'
    }`;
  }

  function statusClass(disposition) {
    return disposition === 0
      ? 'bg-emerald-100 text-emerald-700 dark:bg-emerald-950 dark:text-emerald-200'
      : 'bg-red-100 text-red-700 dark:bg-red-950 dark:text-red-200';
  }

  async function toggleOpen() {
    isOpen = !isOpen;
    if (!isOpen || loaded || loading || !gameId) return;

    loading = true;
    loadError = '';
    try {
      const response = await fetch(`/api/replay/admin/game/${encodeURIComponent(gameId)}/key-trace`);
      const body = await response.json().catch(() => ({}));
      if (!response.ok || !body?.ok || !body?.game?.keyTraceDecoded) {
        throw new Error('입력 내역을 불러오지 못했습니다.');
      }
      payload = body.game.keyTraceDecoded;
      loaded = true;
    } catch (error) {
      loadError = error?.message || '입력 내역을 불러오지 못했습니다.';
    } finally {
      loading = false;
    }
  }
</script>

<section class="mt-5 rounded-xl border border-amber-300 bg-amber-50/70 p-3 dark:border-amber-700 dark:bg-amber-950/20">
    <button class="flex w-full flex-wrap items-center justify-between gap-2 text-left" on:click={toggleOpen} aria-expanded={isOpen}>
      <div>
        <div class="font-semibold">Key 입력 내역</div>
        <div class="text-xs text-amber-800 dark:text-amber-200">하기 입력 내역은 단순 참고 용도로만 사용되어야 합니다.</div>
      </div>
      <span class="flex items-center gap-2">
        {#if isOpen && entries.length}
          <span class="rounded-full bg-amber-200 px-2 py-1 text-xs font-bold text-amber-900 dark:bg-amber-800 dark:text-amber-100">{entries.length}회</span>
        {/if}
        <span class={`material-symbols-outlined text-amber-800 transition-transform duration-200 dark:text-amber-200 ${isOpen ? 'rotate-180' : ''}`}>expand_more</span>
      </span>
    </button>

    {#if isOpen && loading}
      <div class="mt-3 flex min-h-24 flex-col items-center justify-center gap-2 rounded-lg bg-white/70 text-sm text-amber-900 dark:bg-slate-900/60 dark:text-amber-100">
        <span class="material-symbols-outlined animate-spin text-xl">progress_activity</span>
        입력 내역을 불러오는 중이에요.
      </div>
    {:else if isOpen && loadError}
      <div class="mt-3 rounded bg-red-100 px-2 py-2 text-xs font-semibold text-red-700 dark:bg-red-950/60 dark:text-red-200">{loadError}</div>
    {:else if isOpen && payload?.o === 1}
      <div class="mt-2 rounded bg-red-100 px-2 py-1 text-xs font-semibold text-red-700 dark:bg-red-950/60 dark:text-red-200">
        경기 정보를 불러올 수 없습니다
      </div>
    {/if}

    {#if isOpen && entries.length}
    <div class="mt-3 flex flex-wrap gap-2">
      <button
        class={roundButtonClass(selectedRound === null)}
        on:click={() => (selectedRound = null)}
      >전체</button>
      {#each rounds as round}
        <button
          class={roundButtonClass(selectedRound === round)}
          on:click={() => (selectedRound = round)}
        >라운드 {round}</button>
      {/each}
    </div>

    <div class="mt-3 divide-y divide-amber-200 overflow-hidden rounded-lg border border-amber-200 bg-white px-3 dark:divide-amber-900 dark:border-amber-800 dark:bg-slate-900">
      {#each visibleEntries as entry}
        <article class="py-2 text-sm">
          <div class="flex min-w-0 flex-wrap items-center gap-x-2 gap-y-1">
            <b class="shrink-0">{entry.nickname}</b>
            <span class={`shrink-0 rounded px-1.5 py-0.5 text-xs font-semibold ${statusClass(entry.disposition)}`}>{entry.status}</span>
            <span class="min-w-0 text-xs text-slate-500 dark:text-slate-300">
              라운드 {entry.round} · +{(entry.elapsedGameMs / 1000).toFixed(2)}초 · {entry.scope}
            </span>
            <span class="text-xs font-semibold text-slate-500 dark:text-slate-300">
              {entry.transitions.length} events · {entry.resultCode}
            </span>
            <button
              class="ml-auto shrink-0 rounded px-1.5 py-0.5 text-xs font-semibold text-slate-500 hover:bg-slate-100 hover:text-slate-800 dark:text-slate-300 dark:hover:bg-slate-800 dark:hover:text-white"
              on:click={() => (expandedKey = expandedKey === entry.key ? '' : entry.key)}
            >{expandedKey === entry.key ? '상세내역 닫기' : '상세내역 조회'}</button>
          </div>

          <div class="mt-1 break-all rounded bg-slate-100 px-2 py-1 font-mono text-sm leading-5 dark:bg-slate-800">
            {entry.compact || '(표시값 변화 없음)'}
          </div>

          {#if entry.packetRejections.length || entry.inputMismatch || entry.chainMismatch || !entry.complete || entry.transitionOverflowCount > 0}
            <div class="mt-1 flex flex-wrap gap-1">
              {#each entry.packetRejections as reason}
                <span class="rounded bg-violet-100 px-1.5 py-0.5 text-xs font-bold text-violet-700 dark:bg-violet-950 dark:text-violet-200">
                  패킷 거부: {reason}
                </span>
              {/each}
              {#if entry.inputMismatch}
                <span class="rounded bg-red-100 px-1.5 py-0.5 text-xs font-bold text-red-700 dark:bg-red-950 dark:text-red-200">입력 불일치</span>
              {/if}
              {#if entry.chainMismatch}
                <span class="rounded bg-red-100 px-1.5 py-0.5 text-xs font-bold text-red-700 dark:bg-red-950 dark:text-red-200">체인 불일치</span>
              {/if}
              {#if !entry.complete}
                <span class="rounded bg-orange-100 px-1.5 py-0.5 text-xs font-bold text-orange-700 dark:bg-orange-950 dark:text-orange-200">trace 일부 유실</span>
              {/if}
              {#if entry.transitionOverflowCount > 0}
                <span class="rounded bg-slate-200 px-1.5 py-0.5 text-xs font-bold text-slate-700 dark:bg-slate-700 dark:text-slate-100">상세 전이 {entry.transitionOverflowCount}개 생략</span>
              {/if}
            </div>
          {/if}

          {#if expandedKey === entry.key}
            <div class="mt-2 max-h-80 overflow-auto rounded border border-slate-200 dark:border-slate-700">
              <table class="w-full min-w-[680px] text-xs">
                <thead class="sticky top-0 bg-slate-100 text-left dark:bg-slate-800">
                  <tr>
                    <th class="p-2">#</th>
                    <th class="p-2">간격</th>
                    <th class="p-2">DOM event</th>
                    <th class="p-2">inputType</th>
                    <th class="p-2">조합</th>
                    <th class="p-2">표시값</th>
                  </tr>
                </thead>
                <tbody>
                  {#each entry.transitions as transition}
                    <tr class="border-t border-slate-100 dark:border-slate-800">
                      <td class="p-2">{transition.index}</td>
                      <td class="p-2">+{transition.deltaMs}ms</td>
                      <td class="p-2">{transition.event}</td>
                      <td class="p-2">{transition.inputType}</td>
                      <td class="p-2">{transition.composing ? 'Y' : 'N'}</td>
                      <td class="p-2 font-mono">{transition.value || '∅'}</td>
                    </tr>
                  {/each}
                </tbody>
              </table>
            </div>
          {/if}
        </article>
      {/each}
    </div>
    {/if}
  </section>
