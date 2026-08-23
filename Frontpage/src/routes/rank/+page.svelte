<script nonce="kkutuio">
  import { onMount } from 'svelte';
  import { browser } from '$app/environment';
  const title = '랭킹';
  const rankColor = ["yellow-500", "green-500", "blue-500", "purple-500"];

  import { getLevelImage } from '../../lib/getLevelImg.js';
  import { getMoremi } from '../../lib/getMoremi.js';

  let currentPage = 0;
  let loading = false;
  let initialized = false;
  let lastLoadedPage = null;
  let searchQuery = '';
  let resultLabel = '';
  let openMenuKey = '';
  let copiedId = '';
  let copyTimer;

  var topRankData = {
    "data": {
        "page": 0,
        "data": [
            {
                "id": "google-110124117658863556489",
                "name": "Guest",
                "rank": "0",
                "score": "0"
            }
        ]
    }
};

  var rankData = {
        "data": {
            "page": 0,
            "data": [
            ]
        }
  };

  onMount(async () => {
    const urlParams = new URLSearchParams(window.location.search);
    const page = urlParams.get('page');
    if(page) currentPage = Number(page) - 1;
    await refreshRanking();
    lastLoadedPage = currentPage;
    initialized = true;
  });


  async function fetchRankData(page) {
    loading = true;
    try {
      const res = await fetch(`/ranking?page=${page}`);
      if (!res.ok) throw new Error();
      rankData = await res.json();
      resultLabel = '';
    } finally {
      loading = false;
    }
  }

  async function refreshRanking() {
    loading = true;
    try {
      const topResponse = await fetch('/ranking?page=0');
      const pageResponse = currentPage === 0 ? topResponse : await fetch(`/ranking?page=${currentPage}`);
      if (!topResponse.ok || !pageResponse.ok) throw new Error();
      const topData = await topResponse.json();
      topRankData = {
        ...topData,
        data: { ...topData.data, data: topData.data.data.slice(0, 4) }
      };
      rankData = currentPage === 0 ? topData : await pageResponse.json();
      resultLabel = '';
    } finally {
      loading = false;
    }
  }

  async function drawMoremi(uid){
    return await getMoremi(uid);
  }

  async function showMyRank() {
    loading = true;
    try {
      const response = await fetch('/ranking?me=true');
      if (!response.ok) throw new Error();
      rankData = await response.json();
      currentPage = rankData.data.page;
      lastLoadedPage = currentPage;
      resultLabel = '내 순위';
    } finally {
      loading = false;
    }
  }

  async function searchRanking() {
    const query = searchQuery.trim();
    if (!query) return;
    loading = true;
    try {
      const response = await fetch(`/ranking?query=${encodeURIComponent(query)}`);
      if (!response.ok) throw new Error();
      rankData = await response.json();
      currentPage = rankData.data.page;
      lastLoadedPage = currentPage;
      resultLabel = `검색 결과: ${query}`;
    } finally {
      loading = false;
    }
  }

  // Keep the web ranking notation consistent with the in-game leaderboard.
  function displayDelta(delta) {
    const value = String(delta || '*');
    if (value.startsWith('+')) return { text: value.replace('+', '↑'), className: 'text-red-600 dark:text-red-400' };
    if (value.startsWith('-')) return { text: value.replace('-', '↓'), className: 'text-blue-600 dark:text-blue-400' };
    if (value === '0') return { text: '-', className: 'text-gray-400 dark:text-gray-400' };
    return { text: value, className: 'text-gray-400 dark:text-gray-400' };
  }

  function togglePlayerMenu(key) {
    openMenuKey = openMenuKey === key ? '' : key;
  }

  function viewRecords(id) {
    window.location.href = `/records?type=id&q=${encodeURIComponent(id)}`;
  }

  async function copyIdentifier(id) {
    try {
      if (navigator.clipboard?.writeText) {
        await navigator.clipboard.writeText(id);
      } else {
        const input = document.createElement('textarea');
        input.value = id;
        input.style.position = 'fixed';
        input.style.opacity = '0';
        document.body.appendChild(input);
        input.select();
        document.execCommand('copy');
        input.remove();
      }
      copiedId = id;
      clearTimeout(copyTimer);
      copyTimer = setTimeout(() => (copiedId = ''), 1800);
    } finally {
      openMenuKey = '';
    }
  }
  //on currentPage change
  $: {
    if (currentPage < 0) currentPage = 0;
    if (browser && initialized && currentPage !== lastLoadedPage) {
      lastLoadedPage = currentPage;
      fetchRankData(currentPage);
    }
  }

  function handleImgErr(e, category, filename) {
      const img = e.target;
      const baseURL = "https://cdn.kkutu.io/img/kkutu/moremi";
      if (img.src.includes('/event/')) {
          img.src = `${baseURL}/${category}/default.png`;
          img.onerror = null; 
      } else {
          if (!filename || filename === "default.png") img.src = `${baseURL}/${category}/default.png`;
          else img.src = `${baseURL}/event/${filename}`;
      }
  }
</script>

<svelte:head>
  <title>끄투리오 - {title}</title>
</svelte:head>
<svelte:window on:click={() => (openMenuKey = '')} />
<div class="min-h-screen bg-slate-950 py-4 text-slate-100">
  <section class="rankBg relative flex min-h-[340px] flex-col items-center justify-center overflow-hidden px-4 pb-24 pt-28 md:pt-36">
    <div class="absolute inset-0 bg-gradient-to-b from-slate-950/30 via-slate-950/25 to-slate-950"></div>
    <div class="relative z-[1] flex items-center gap-3 text-slate-200">
      <span class="material-symbols-outlined text-2xl">emoji_events</span>
      <p class="text-base font-medium sm:text-lg">한 눈에 보는 순위</p>
    </div>
    <div class="relative z-[1] mt-2 flex items-center gap-2">
      <h1 class="text-4xl font-black tracking-tight text-white sm:text-5xl">랭킹</h1>
      <button class="grid h-10 w-10 place-items-center rounded-xl text-white/80 transition hover:bg-white/15 disabled:opacity-50" on:click={refreshRanking} disabled={loading} aria-label="랭킹 새로고침">
        <span class:animate-spin={loading} class="material-symbols-outlined">{loading ? 'progress_activity' : 'refresh'}</span>
      </button>
    </div>
    <p class="relative z-[1] mt-3 text-center text-sm text-slate-200 sm:text-base">끄투리오의 랭킹과 순위 변동을 확인하세요.</p>
  </section>

  <main class="relative z-[2] mx-2 -mt-16 mb-24 max-w-screen-xl rounded-2xl border border-slate-300/40 bg-slate-100/95 p-3 text-slate-900 shadow-2xl shadow-slate-950/30 backdrop-blur md:mx-auto md:p-5 dark:border-slate-700 dark:bg-slate-900/90 dark:text-slate-100">
    <section class="grid grid-cols-1 gap-3 sm:grid-cols-2 xl:grid-cols-4">
    {#each topRankData.data.data as rank, i}
      {@const delta = displayDelta(rank.delta)}
      <article class="group relative rounded-2xl border border-slate-200 bg-white/95 p-4 shadow-sm transition duration-200 hover:-translate-y-0.5 hover:shadow-md dark:border-slate-700 dark:bg-slate-900">
        <div class="absolute inset-x-0 top-0 h-1" style={`background-color: ${rankColor[Number(rank.rank)]}`}></div>
        <button type="button" aria-label={`${rank.name || '플레이어'} 메뉴`} on:click|stopPropagation={() => togglePlayerMenu(`top-${rank.id}`)} class="absolute right-3 top-3 z-10 grid h-8 w-8 place-items-center rounded-lg bg-white/80 text-slate-500 shadow-sm backdrop-blur transition hover:bg-slate-100 hover:text-slate-900 dark:bg-slate-800/80 dark:text-slate-400 dark:hover:bg-slate-700 dark:hover:text-white"><span class="material-symbols-outlined">more_vert</span></button>
        <div class="flex items-start justify-between gap-3">
          <div>
            <span class="inline-flex rounded-full bg-slate-900 px-2.5 py-1 text-xs font-black text-white dark:bg-slate-700">{rank.rank + 1}위</span>
            <div class="mt-3 flex items-center gap-2 text-lg font-bold">
              <div class="level" style={getLevelImage(Number(rank.score))}></div>
              <span class="max-w-[150px] truncate">
                {#if rank.name && rank.name.includes('#')}
                  {rank.name.split('#')[0]}<small class="font-normal">#{rank.name.split('#')[1]}</small>
                {:else}
                  {rank.name || '알 수 없음'}
                {/if}
              </span>
            </div>
          </div>
          <!-- Moremi Render -->
          <div class="relative h-24 w-24 shrink-0 overflow-hidden rounded-2xl border border-slate-200 bg-slate-50 shadow-sm dark:border-slate-700 dark:bg-slate-800">
          {#await drawMoremi(rank["id"]) then equip}
            <img src={`https://cdn.kkutu.io/img/kkutu/moremi/back/${equip.Mback || "default.png"}`} class="absolute h-full w-full object-cover" alt="BG" on:error={(e) => handleImgErr(e, 'back', equip.Mback)} />
            <img src={`https://cdn.kkutu.io/img/kkutu/moremi/body/${equip.Mbody || "default.png"}`} class="absolute h-full w-full object-cover" alt="Moremi Body" />
            <img src={`https://cdn.kkutu.io/img/kkutu/moremi/eye/${equip.Meye || "default.png"}`} class="absolute h-full w-full object-cover" alt="Moremi Eye" on:error={(e) => handleImgErr(e, 'eye', equip.Meye)} />
            <img src={`https://cdn.kkutu.io/img/kkutu/moremi/mouth/${equip.Mmouth || "default.png"}`} class="absolute h-full w-full object-cover" alt="Moremi Mouth" on:error={(e) => handleImgErr(e, 'mouth', equip.Mmouth)} />
            <img src={`https://cdn.kkutu.io/img/kkutu/moremi/clothes/${equip.Mclothes || "default.png"}`} class="absolute h-full w-full object-cover" alt="Moremi Pants" on:error={(e) => handleImgErr(e, 'clothes', equip.Mclothes)} />
            <img src={`https://cdn.kkutu.io/img/kkutu/moremi/shoes/${equip.Mshoes || "default.png"}`} class="absolute h-full w-full object-cover" alt="Moremi Shoes" on:error={(e) => handleImgErr(e, 'shoes', equip.Mshoes)} />
            <img src={`https://cdn.kkutu.io/img/kkutu/moremi/head/${equip.Mhead || "default.png"}`} class="absolute h-full w-full object-cover" alt="Moremi Head" on:error={(e) => handleImgErr(e, 'head', equip.Mhead)} />
            <img src={`https://cdn.kkutu.io/img/kkutu/moremi/hand/${equip.Mrhand || "default.png"}`} class="rightHand absolute h-full w-full object-cover" alt="Moremi Hand" on:error={(e) => handleImgErr(e, 'hand', equip.Mrhand)} />
            <img src={`https://cdn.kkutu.io/img/kkutu/moremi/hand/${equip.Mlhand || "default.png"}`} class="absolute h-full w-full object-cover" alt="Moremi Hand" on:error={(e) => handleImgErr(e, 'hand', equip.Mlhand)} />
            <img src={`https://cdn.kkutu.io/img/kkutu/moremi/headDeco/${equip.MheadDeco || "default.png"}`} class="absolute h-full w-full object-cover" alt="Moremi HeadDeco" on:error={(e) => handleImgErr(e, 'headDeco', equip.MheadDeco)} />
            <img src={`https://cdn.kkutu.io/img/kkutu/moremi/dressDeco/${equip.MdressDeco || "default.png"}`} class="absolute h-full w-full object-cover" alt="Moremi Badge" on:error={(e) => handleImgErr(e, 'dressDeco', equip.MdressDeco)} />
            <img src={`https://cdn.kkutu.io/img/kkutu/moremi/badge/${equip.BDG || "default.png"}`} class="absolute h-full w-full object-cover" alt="Moremi Badge" on:error={(e) => handleImgErr(e, 'badge', equip.BDG)} />
          {:catch error}
            <div></div>
          {/await}
        </div>
        </div>
        {#if openMenuKey === `top-${rank.id}`}
          <div role="menu" on:click|stopPropagation class="absolute right-3 top-12 z-30 w-44 overflow-hidden rounded-2xl border border-slate-200 bg-white p-1.5 text-sm shadow-xl shadow-slate-950/20 dark:border-slate-700 dark:bg-slate-800">
            <button on:click={() => viewRecords(rank.id)} class="w-full rounded-xl px-3 py-2.5 text-left font-bold transition hover:bg-sky-50 hover:text-sky-700 dark:hover:bg-slate-700 dark:hover:text-sky-300">전적 보기</button>
            <button on:click={() => copyIdentifier(rank.id)} class="w-full rounded-xl px-3 py-2.5 text-left font-bold transition hover:bg-slate-100 dark:hover:bg-slate-700">{copiedId === rank.id ? '식별번호 복사 완료' : '식별번호 복사'}</button>
          </div>
        {/if}
        <div class="mt-4 flex items-end justify-between border-t border-slate-100 pt-3 dark:border-slate-700">
          <div><p class="text-xs font-medium text-slate-500 dark:text-slate-400">누적 점수</p><p class="mt-0.5 text-lg font-black">{Number(rank.score).toLocaleString()}<small class="ml-0.5 text-xs font-medium">점</small></p></div>
          <span class={`rounded-lg bg-slate-100 px-2.5 py-1 text-sm font-black dark:bg-slate-800 ${delta.className}`} aria-label={`순위 변동 ${delta.text}`}>{delta.text}</span>
        </div>
      </article>
    {/each}
    </section>

    <section class="mt-5 overflow-hidden rounded-2xl border border-slate-200 bg-white/95 shadow-sm dark:border-slate-700 dark:bg-slate-900">
      <div class="flex flex-col gap-3 border-b border-slate-200 bg-slate-50 p-3 sm:flex-row sm:items-center sm:justify-between sm:p-4 dark:border-slate-700 dark:bg-slate-800/70">
        <div><h2 class="flex items-center gap-2 text-lg font-black"><span class="material-symbols-outlined text-amber-500">leaderboard</span>{resultLabel || '전체 랭킹'}</h2><p class="mt-1 text-xs text-slate-500 dark:text-slate-400">전체 순위는 누계 점수를 기준으로 산정됩니다.</p></div>
        <div class="flex flex-col gap-2 sm:flex-row">
          <button on:click={showMyRank} disabled={loading} class="inline-flex h-10 items-center justify-center gap-1.5 rounded-xl bg-slate-900 px-4 text-sm font-bold text-white shadow-sm transition hover:bg-slate-700 disabled:opacity-50 dark:bg-slate-700 dark:hover:bg-slate-600"><span class="material-symbols-outlined text-lg">person</span>내 순위</button>
          <form class="flex rounded-xl border border-slate-200 bg-white p-1 shadow-sm focus-within:ring-2 focus-within:ring-sky-400/50 dark:border-slate-600 dark:bg-slate-900" on:submit|preventDefault={searchRanking}>
            <input bind:value={searchQuery} aria-label="별명 또는 UUID 검색" placeholder="별명 또는 UUID 검색" class="min-w-0 flex-1 bg-transparent px-2 text-sm outline-none placeholder:text-slate-400 sm:w-52" />
            <button disabled={loading} aria-label="랭킹 검색" class="grid h-8 w-8 place-items-center rounded-lg bg-sky-600 text-white transition hover:bg-sky-500 disabled:opacity-50"><span class="material-symbols-outlined text-lg">search</span></button>
          </form>
        </div>
      </div>
      <div class="overflow-x-auto">
      <table class="w-full min-w-[620px] text-left">
        <thead class="bg-slate-100 text-xs uppercase tracking-wide text-slate-500 dark:bg-slate-800 dark:text-slate-300">
          <tr>
            <th class="px-4 py-3 text-center font-bold">순위</th><th class="px-4 py-3 font-bold">별명</th><th class="px-4 py-3 text-center font-bold">레벨</th><th class="px-4 py-3 text-right font-bold">점수</th><th class="px-4 py-3 text-center font-bold">변동</th>
          </tr>
        </thead>
        <tbody>
          {#each rankData.data.data as rank, i}
          {@const delta = displayDelta(rank.delta)}
          <tr class="border-t border-slate-100 transition hover:bg-sky-50/60 dark:border-slate-800 dark:hover:bg-slate-800/70">
            <td class="px-4 py-3 text-center font-black">{rank.rank + 1}<small class="ml-0.5 font-medium text-slate-500">위</small></td>
            <td class="relative px-4 py-3">
              <div class="flex items-center justify-between gap-2">
                <span class="min-w-0 truncate font-bold">{#if rank.name && rank.name.includes('#')}
                    {rank.name.split('#')[0]}<small class="font-normal">#{rank.name.split('#')[1]}</small>
                  {:else}
                    {rank.name || '알 수 없음'}
                  {/if}</span>
                <button type="button" aria-label={`${rank.name || '플레이어'} 메뉴`} on:click|stopPropagation={() => togglePlayerMenu(`table-${rank.id}`)} class="grid h-8 w-8 shrink-0 place-items-center rounded-lg text-slate-500 transition hover:bg-slate-100 hover:text-slate-900 dark:text-slate-400 dark:hover:bg-slate-800 dark:hover:text-white"><span class="material-symbols-outlined">more_vert</span></button>
              </div>
              {#if openMenuKey === `table-${rank.id}`}
                <div role="menu" on:click|stopPropagation class="absolute right-3 top-11 z-30 w-44 overflow-hidden rounded-2xl border border-slate-200 bg-white p-1.5 text-sm shadow-xl shadow-slate-950/20 dark:border-slate-700 dark:bg-slate-800">
                  <button on:click={() => viewRecords(rank.id)} class="w-full rounded-xl px-3 py-2.5 text-left font-bold transition hover:bg-sky-50 hover:text-sky-700 dark:hover:bg-slate-700 dark:hover:text-sky-300">전적 보기</button>
                  <button on:click={() => copyIdentifier(rank.id)} class="w-full rounded-xl px-3 py-2.5 text-left font-bold transition hover:bg-slate-100 dark:hover:bg-slate-700">{copiedId === rank.id ? '식별번호 복사 완료' : '식별번호 복사'}</button>
                </div>
              {/if}
            </td>
            <td class="px-4 py-3"><div class="flex justify-center">
              <div class="level" style={getLevelImage(Number(rank.score))}></div>
            </div></td>
            <td class="px-4 py-3 text-right font-semibold">{Number(rank.score).toLocaleString()}<small class="ml-0.5 font-normal text-slate-500">점</small></td>
            <td class="px-4 py-3 text-center"><span class={`inline-flex min-w-8 justify-center rounded-lg bg-slate-100 px-2 py-1 text-sm font-black dark:bg-slate-800 ${delta.className}`}>{delta.text}</span></td>
          </tr>
          {:else}
          <tr><td colspan="5" class="px-4 py-14 text-center text-sm text-slate-500 dark:text-slate-400"><span class="material-symbols-outlined mb-2 block text-3xl">person_search</span>표시할 랭킹 정보가 없습니다.</td></tr>
          {/each}
        </tbody>
      </table>
      </div>
      <div class="flex items-center justify-center gap-3 border-t border-slate-200 p-3 dark:border-slate-700">
        <button on:click={() => currentPage--} disabled={currentPage === 0 || loading} aria-label="이전 페이지" class="grid h-9 w-9 place-items-center rounded-xl border border-slate-200 bg-white transition hover:bg-slate-100 disabled:cursor-not-allowed disabled:opacity-40 dark:border-slate-700 dark:bg-slate-800 dark:hover:bg-slate-700"><span class:animate-spin={loading} class="material-symbols-outlined">{loading ? 'progress_activity' : 'chevron_left'}</span></button>
        <span class="min-w-20 text-center text-sm font-bold text-slate-600 dark:text-slate-300">{currentPage + 1} 페이지</span>
        <button on:click={() => currentPage++} disabled={loading || rankData.data.data.length < 15} aria-label="다음 페이지" class="grid h-9 w-9 place-items-center rounded-xl border border-slate-200 bg-white transition hover:bg-slate-100 disabled:cursor-not-allowed disabled:opacity-40 dark:border-slate-700 dark:bg-slate-800 dark:hover:bg-slate-700"><span class:animate-spin={loading} class="material-symbols-outlined">{loading ? 'progress_activity' : 'chevron_right'}</span></button>
      </div>
    </section>
  </main>
</div>
