<script>
  let { word, themeNames = {}, compact = false } = $props();

  const gradeLabels = {
    FINISH: ['종결', 'bg-rose-100 text-rose-700 dark:bg-rose-950/50 dark:text-rose-300'],
    DECISIVE: ['결정적', 'bg-orange-100 text-orange-700 dark:bg-orange-950/50 dark:text-orange-300'],
    VERY_HIGH: ['매우 강함', 'bg-amber-100 text-amber-700 dark:bg-amber-950/50 dark:text-amber-300'],
    HIGH: ['강함', 'bg-emerald-100 text-emerald-700 dark:bg-emerald-950/50 dark:text-emerald-300'],
    MEDIUM: ['보통', 'bg-sky-100 text-sky-700 dark:bg-sky-950/50 dark:text-sky-300'],
    LOW: ['안전', 'bg-slate-100 text-slate-600 dark:bg-slate-800 dark:text-slate-300']
  };

  let grade = $derived(gradeLabels[word.attackGrade] || gradeLabels.LOW);

  function speak() {
    if (!globalThis.speechSynthesis) return;
    const utterance = new SpeechSynthesisUtterance(word.word);
    utterance.lang = word.word.match(/[가-힣]/) ? 'ko-KR' : 'en-US';
    globalThis.speechSynthesis.cancel();
    globalThis.speechSynthesis.speak(utterance);
  }

  function cleanMean(value) {
    return String(value || '').replace(/＂\d+＂/g, ' · ').replace(/^\s*·\s*/, '').trim();
  }
</script>

<article class={`group min-w-0 rounded-2xl border border-slate-200 bg-white transition hover:-translate-y-0.5 hover:border-emerald-300 hover:shadow-lg dark:border-slate-700 dark:bg-slate-900 dark:hover:border-emerald-700 ${compact ? 'p-4' : 'p-5'}`}>
  <div class="flex min-w-0 items-start justify-between gap-3">
    <div class="min-w-0">
      <div class="flex min-w-0 flex-wrap items-center gap-2">
        <h3 class={`${compact ? 'text-lg' : 'text-xl'} min-w-0 break-all font-black tracking-tight text-slate-900 dark:text-white`}>{word.word}</h3>
        <span class={`shrink-0 rounded-full px-2 py-0.5 text-xs font-bold ${grade[1]}`}>{grade[0]}</span>
      </div>
      <p class="mt-1 break-words text-sm font-medium text-slate-500 dark:text-slate-400">
        {word.length}자 · {word.startChar} → {word.nextChar} · 다음 방어 {word.defenseCount.toLocaleString()}개
      </p>
    </div>
    <button type="button" onclick={speak} class="grid h-9 w-9 shrink-0 place-items-center rounded-xl text-slate-400 transition hover:bg-slate-100 hover:text-emerald-600 dark:hover:bg-slate-800" aria-label={`${word.word} 발음 듣기`}>
      <span class="material-symbols-outlined text-xl">volume_up</span>
    </button>
  </div>

  {#if !compact && cleanMean(word.mean)}
    <p class="mt-3 line-clamp-3 whitespace-pre-line break-words text-sm leading-6 text-slate-600 dark:text-slate-300">{cleanMean(word.mean)}</p>
  {/if}

  <div class="mt-3 flex min-w-0 flex-wrap items-center gap-2">
    {#each word.themes.slice(0, compact ? 2 : 5) as theme}
      <span class="max-w-full truncate rounded-lg bg-slate-100 px-2 py-1 text-xs font-semibold text-slate-600 dark:bg-slate-800 dark:text-slate-300">{themeNames[theme] || theme}</span>
    {/each}
    {#if word.themes.length === 0}
      <span class="text-xs text-slate-400">일반 단어</span>
    {/if}
    <a href={`https://kkutu.wiki/w/${encodeURIComponent(word.word)}`} target="_blank" rel="noreferrer" class="ml-auto inline-flex shrink-0 items-center gap-1 text-xs font-bold text-emerald-600 hover:underline dark:text-emerald-400">
      리오위키 <span class="material-symbols-outlined text-sm">open_in_new</span>
    </a>
  </div>
</article>
