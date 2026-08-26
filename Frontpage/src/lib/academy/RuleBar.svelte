<script>
  let { config = $bindable(), meta = null, compact = false } = $props();
  let advanced = $state(false);

  const flags = [
    ['includeLoanword', '외래어'],
    ['includeSpaced', '띄어쓰기'],
    ['includeDialect', '방언'],
    ['includeOld', '옛말'],
    ['includeCultural', '문화어'],
    ['includeKung', '쿵쿵따 전용']
  ];

  function numberValue(event, fallback) {
    const value = Number(event.currentTarget.value);
    return Number.isFinite(value) ? value : fallback;
  }

  function dictionaryLabel(dictionary) {
    if (dictionary.value === 'BASIC') return '기초 사전';
    if (dictionary.value === 'STANDARD') return '표준 사전';
    if (dictionary.value === 'COMBINED') return '전체 사전';
    return dictionary.label;
  }
</script>

<section class={`min-w-0 rounded-2xl border border-slate-200 bg-white shadow-sm dark:border-slate-700 dark:bg-slate-900 ${compact ? 'p-3' : 'p-4'}`} aria-label="학습 사전 규칙">
  <div class="grid min-w-0 gap-3 sm:grid-cols-2 lg:grid-cols-4">
    <label class="grid min-w-0 gap-1 text-sm font-semibold text-slate-600 dark:text-slate-300">
      언어
      <select bind:value={config.lang} class="w-full min-w-0 rounded-xl border border-slate-200 bg-slate-50 px-3 py-2.5 text-slate-900 outline-none focus:border-emerald-500 dark:border-slate-700 dark:bg-slate-800 dark:text-white">
        <option value="ko">한국어</option>
        <option value="en">영어</option>
      </select>
    </label>
    <label class="grid min-w-0 gap-1 text-sm font-semibold text-slate-600 dark:text-slate-300">
      사전
      <select bind:value={config.dictionary} class="w-full min-w-0 rounded-xl border border-slate-200 bg-slate-50 px-3 py-2.5 text-slate-900 outline-none focus:border-emerald-500 dark:border-slate-700 dark:bg-slate-800 dark:text-white">
        {#each meta?.dictionaries || [
          {value:'BASIC',label:'기초 사전'},
          {value:'STANDARD',label:'표준 사전'},
          {value:'COMBINED',label:'전체 사전'}
        ] as dictionary}
          <option value={dictionary.value}>{dictionaryLabel(dictionary)}</option>
        {/each}
      </select>
    </label>
    <label class="grid min-w-0 gap-1 text-sm font-semibold text-slate-600 dark:text-slate-300">
      진행 방향
      <select bind:value={config.direction} class="w-full min-w-0 rounded-xl border border-slate-200 bg-slate-50 px-3 py-2.5 text-slate-900 outline-none focus:border-emerald-500 dark:border-slate-700 dark:bg-slate-800 dark:text-white">
        <option value="FORWARD">끝말잇기</option>
        <option value="REVERSE">앞말잇기</option>
      </select>
    </label>
    <div class="flex min-w-0 items-end gap-2">
      <label class="flex min-h-11 min-w-0 flex-1 cursor-pointer items-center justify-between rounded-xl border border-slate-200 bg-slate-50 px-3 text-sm font-bold text-slate-700 dark:border-slate-700 dark:bg-slate-800 dark:text-slate-200">
        두음법칙
        <input type="checkbox" bind:checked={config.duum} disabled={config.lang !== 'ko'} class="h-4 w-4 shrink-0 accent-emerald-600" />
      </label>
      <button type="button" onclick={() => advanced = !advanced} class="grid h-11 w-11 shrink-0 place-items-center rounded-xl border border-slate-200 text-slate-500 transition hover:bg-slate-100 dark:border-slate-700 dark:text-slate-300 dark:hover:bg-slate-800" aria-label="고급 규칙" title="고급 규칙">
        <span class="material-symbols-outlined">tune</span>
      </button>
    </div>
  </div>

  {#if advanced}
    <div class="mt-4 min-w-0 border-t border-slate-100 pt-4 dark:border-slate-800">
      <div class="grid min-w-0 gap-3 sm:grid-cols-2 lg:grid-cols-4">
        <label class="grid min-w-0 gap-1 text-sm font-semibold text-slate-600 dark:text-slate-300">
          최소 길이
          <input type="number" min="1" max="64" value={config.minLength} oninput={(event) => config.minLength = numberValue(event, 2)} class="w-full min-w-0 rounded-xl border border-slate-200 bg-slate-50 px-3 py-2 dark:border-slate-700 dark:bg-slate-800 dark:text-white" />
        </label>
        <label class="grid min-w-0 gap-1 text-sm font-semibold text-slate-600 dark:text-slate-300">
          최대 길이
          <input type="number" min="1" max="128" value={config.maxLength} oninput={(event) => config.maxLength = numberValue(event, 64)} class="w-full min-w-0 rounded-xl border border-slate-200 bg-slate-50 px-3 py-2 dark:border-slate-700 dark:bg-slate-800 dark:text-white" />
        </label>
        <label class="grid min-w-0 gap-1 text-sm font-semibold text-slate-600 dark:text-slate-300 sm:col-span-2">
          포함할 주제 코드 <span class="font-normal text-slate-400">쉼표로 구분, 비우면 전체</span>
          <input value={(config.themes || []).join(',')} oninput={(event) => config.themes = event.currentTarget.value.split(',').map((item) => item.trim()).filter(Boolean)} placeholder="예: KOT, MAP" class="w-full min-w-0 rounded-xl border border-slate-200 bg-slate-50 px-3 py-2 dark:border-slate-700 dark:bg-slate-800 dark:text-white" />
        </label>
      </div>
      <div class="mt-3 flex min-w-0 flex-wrap gap-2">
        {#each flags as [field, label]}
          <label class={`cursor-pointer rounded-full border px-3 py-1.5 text-sm font-semibold transition ${config[field] ? 'border-emerald-200 bg-emerald-50 text-emerald-700 dark:border-emerald-800 dark:bg-emerald-950/40 dark:text-emerald-300' : 'border-slate-200 bg-white text-slate-400 dark:border-slate-700 dark:bg-slate-900'}`}>
            <input type="checkbox" bind:checked={config[field]} class="sr-only" />
            {label}
          </label>
        {/each}
      </div>
    </div>
  {/if}
</section>
