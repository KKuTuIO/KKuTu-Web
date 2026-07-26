<script>
  export let payload = null;
  export let players = [];

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
  const STATUS_LABELS = ['승인', '거절', '무시', '시간 초과'];

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
      status: STATUS_LABELS[Math.max(0, Math.min(3, Number(row[3]) || 0))],
      resultCode: String(row[4] || '-'),
      scope: `${String(row[5] || '-')}:${String(row[6] || '-')}`,
      inputMismatch: Number(row[7]) === 1,
      chainMismatch: Number(row[8]) === 1,
      complete: Number(row[9]) === 1,
      compact: compact.join('').replace(/\++/g, '+').replace(/\+$/, ''),
      transitions
    };
  }

  $: entries = Array.isArray(payload?.e)
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
</script>

{#if entries.length}
  <section class="mt-5 rounded-xl border border-amber-300 bg-amber-50/70 p-3 dark:border-amber-700 dark:bg-amber-950/20">
    <div class="flex flex-wrap items-center justify-between gap-2">
      <div>
        <div class="font-semibold">Key 입력 내역</div>
        <div class="text-xs text-amber-800 dark:text-amber-200">운영자 전용 · 일반 전적 응답에는 포함되지 않습니다.</div>
      </div>
      <span class="rounded-full bg-amber-200 px-2 py-1 text-xs font-bold text-amber-900 dark:bg-amber-800 dark:text-amber-100">
        {entries.length}회
      </span>
    </div>

    {#if payload?.o === 1}
      <div class="mt-2 rounded bg-red-100 px-2 py-1 text-xs font-semibold text-red-700 dark:bg-red-950/60 dark:text-red-200">
        비정상적으로 큰 경기여서 서버 메모리 한도 이후 입력은 저장되지 않았습니다.
      </div>
    {/if}

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

    <div class="mt-3 space-y-2">
      {#each visibleEntries as entry}
        <article class="rounded-lg border border-amber-200 bg-white p-3 text-sm dark:border-amber-800 dark:bg-slate-900">
          <div class="flex flex-wrap items-center gap-2">
            <b>{entry.nickname}</b>
            <span class="rounded bg-slate-100 px-1.5 py-0.5 text-xs dark:bg-slate-700">{entry.status}</span>
            <span class="text-xs text-slate-500 dark:text-slate-300">라운드 {entry.round} · +{(entry.elapsedGameMs / 1000).toFixed(2)}초 · {entry.scope}</span>
            {#if entry.inputMismatch}
              <span class="rounded bg-red-100 px-1.5 py-0.5 text-xs font-bold text-red-700 dark:bg-red-950 dark:text-red-200">입력 불일치</span>
            {/if}
            {#if entry.chainMismatch}
              <span class="rounded bg-red-100 px-1.5 py-0.5 text-xs font-bold text-red-700 dark:bg-red-950 dark:text-red-200">체인 불일치</span>
            {/if}
            {#if !entry.complete}
              <span class="rounded bg-orange-100 px-1.5 py-0.5 text-xs font-bold text-orange-700 dark:bg-orange-950 dark:text-orange-200">trace 일부 유실</span>
            {/if}
          </div>
          <div class="mt-2 break-all rounded bg-slate-100 px-2 py-1.5 font-mono text-sm dark:bg-slate-800">
            {entry.compact || '(표시값 변화 없음)'}
          </div>
          <div class="mt-2 flex flex-wrap items-center justify-between gap-2 text-xs text-slate-500 dark:text-slate-300">
            <span>{entry.transitions.length} events · {entry.resultCode}</span>
            <button
              class="rounded border border-slate-300 px-2 py-1 font-semibold hover:bg-slate-100 dark:border-slate-600 dark:hover:bg-slate-800"
              on:click={() => (expandedKey = expandedKey === entry.key ? '' : entry.key)}
            >{expandedKey === entry.key ? '정확한 전이 닫기' : '정확한 전이 보기'}</button>
          </div>
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
  </section>
{/if}
