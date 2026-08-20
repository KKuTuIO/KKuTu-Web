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
    const res = await fetch(`/ranking?page=${page}`);
    const data = await res.json();
    rankData = data;
  }

  async function refreshRanking() {
    loading = true;
    try {
      const topResponse = await fetch('/ranking?page=0');
      const pageResponse = currentPage === 0 ? topResponse : await fetch(`/ranking?page=${currentPage}`);
      if (!topResponse.ok || !pageResponse.ok) throw new Error();
      const topData = await topResponse.json();
      topRankData = topData;
      topRankData.data.data = topRankData.data.data.slice(0, 4);
      rankData = currentPage === 0 ? topData : await pageResponse.json();
    } finally {
      loading = false;
    }
  }

  async function drawMoremi(uid){
    return await getMoremi(uid);
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
<div class="dark:bg-gray-900">
  <div class="pt-32 px-4 pb-24 flex flex-col items-center rankBg">
      <div class="flex items-center gap-2"><h1 class="text-white text-5xl font-bold mb-2">랭킹</h1><button class="mb-2 grid h-10 w-10 place-items-center rounded-full text-white/80 transition hover:bg-white/15 disabled:opacity-50" on:click={refreshRanking} disabled={loading} aria-label="새로고침"><span class:animate-spin={loading} class="material-symbols-outlined">{loading ? 'progress_activity' : 'refresh'}</span></button></div>
      <p class="text-gray-300 text-xl my-4">끄투리오의 랭킹을 확인하세요.</p>
  </div>
  <div class="lg:shadow-md mx-2 lg:mx-auto max-w-screen-xl -mt-16 mb-24 p-2 lg:p-4 bg-gray-100 dark:bg-gray-800 dark:text-white rounded-lg">
    <div class="grid grid-cols-2 lg:grid-cols-4 lg:px-36 lg:gap-x-12 gap-x-8 gap-y-4">
    {#each topRankData.data.data as rank, i}
      <div class="border-2 p-2 text-center rounded-lg bg-white dark:bg-gray-700"
        style={`border-color: ${rankColor[Number(rank.rank)]}`}>
        <span class="font-bold"
        style={`color: ${rankColor[Number(rank.rank)]}`}>
        {rank.rank + 1}위</span>
        <div class="border-b dark:border-gray-600 my-2 mx-2"></div>
        <!-- Moremi Render -->
        <div class="w-[120px] h-[120px] mx-auto mb-4">
          {#await drawMoremi(rank["id"]) then equip}
            <img src={`https://cdn.kkutu.io/img/kkutu/moremi/back/${equip.Mback || "default.png"}`} class="absolute object-cover w-[120px] h-[120px]" alt="BG" on:error={(e) => handleImgErr(e, 'back', equip.Mback)} />
            <img src={`https://cdn.kkutu.io/img/kkutu/moremi/body/${equip.Mbody || "default.png"}`} class="absolute object-cover w-[120px] h-[120px]" alt="Moremi Body" />
            <img src={`https://cdn.kkutu.io/img/kkutu/moremi/eye/${equip.Meye || "default.png"}`} class="absolute object-cover w-[120px] h-[120px]" alt="Moremi Eye" on:error={(e) => handleImgErr(e, 'eye', equip.Meye)} />
            <img src={`https://cdn.kkutu.io/img/kkutu/moremi/mouth/${equip.Mmouth || "default.png"}`} class="absolute object-cover w-[120px] h-[120px]" alt="Moremi Mouth" on:error={(e) => handleImgErr(e, 'mouth', equip.Mmouth)} />
            <img src={`https://cdn.kkutu.io/img/kkutu/moremi/clothes/${equip.Mclothes || "default.png"}`} class="absolute object-cover w-[120px] h-[120px]" alt="Moremi Pants" on:error={(e) => handleImgErr(e, 'clothes', equip.Mclothes)} />
            <img src={`https://cdn.kkutu.io/img/kkutu/moremi/shoes/${equip.Mshoes || "default.png"}`} class="absolute object-cover w-[120px] h-[120px]" alt="Moremi Shoes" on:error={(e) => handleImgErr(e, 'shoes', equip.Mshoes)} />
            <img src={`https://cdn.kkutu.io/img/kkutu/moremi/head/${equip.Mhead || "default.png"}`} class="absolute object-cover w-[120px] h-[120px]" alt="Moremi Head" on:error={(e) => handleImgErr(e, 'head', equip.Mhead)} />
            <img src={`https://cdn.kkutu.io/img/kkutu/moremi/hand/${equip.Mrhand || "default.png"}`} class="rightHand absolute object-cover w-[120px] h-[120px]" alt="Moremi Hand" on:error={(e) => handleImgErr(e, 'hand', equip.Mrhand)} />
            <img src={`https://cdn.kkutu.io/img/kkutu/moremi/hand/${equip.Mlhand || "default.png"}`} class="absolute object-cover w-[120px] h-[120px]" alt="Moremi Hand" on:error={(e) => handleImgErr(e, 'hand', equip.Mlhand)} />
            <img src={`https://cdn.kkutu.io/img/kkutu/moremi/headDeco/${equip.MheadDeco || "default.png"}`} class="absolute object-cover w-[120px] h-[120px]" alt="Moremi HeadDeco" on:error={(e) => handleImgErr(e, 'headDeco', equip.MheadDeco)} />
            <img src={`https://cdn.kkutu.io/img/kkutu/moremi/dressDeco/${equip.MdressDeco || "default.png"}`} class="absolute object-cover w-[120px] h-[120px]" alt="Moremi DressDeco" on:error={(e) => handleImgErr(e, 'dressDeco', equip.MdressDeco)} />
            <img src={`https://cdn.kkutu.io/img/kkutu/moremi/badge/${equip.BDG || "default.png"}`} class="absolute object-cover w-[120px] h-[120px]" alt="Moremi Badge" on:error={(e) => handleImgErr(e, 'badge', equip.BDG)} />
          {:catch error}
            <div></div>
          {/await}
        </div>
        <!-- User Name -->
        <div class="items-center justify-center flex text-lg font-bold">
          <div class="level mr-2" style={getLevelImage(Number(rank.score))}></div>
          {#if rank.name.includes('#')}
              {rank.name.split('#')[0]}<small>#{rank.name.split('#')[1]}</small>
          {:else}
              {rank.name}
          {/if}
        </div>
        <span class="font-normal text-right dark:text-gray-300 text-gray-500">
          {Number(rank.score).toLocaleString()}점
        </span>
      </div>
    {/each}
    </div>
    <!-- Rank List -->
    <div class="lg:px-36 px-2">
      <table class="w-full text-left my-8">
        <thead class="bg-gray-200 dark:bg-gray-700 dark:text-white">
          <tr>
            <th class="py-2 text-center">순위</th>
            <th class="py-2 text-center">별명</th>
            <th class="py-2 text-center">레벨</th>
            <th class="py-2 text-center">점수</th>
          </tr>
        </thead>
        <tbody>
          {#each rankData.data.data as rank, i}
          <tr class="border-b dark:border-gray-600">
            <td class="py-2 text-center">{rank.rank + 1}위</td>
            <td class="py-2 text-center">
              {#if rank.name.includes('#')}
                  {rank.name.split('#')[0]}<small class="font-normal">#{rank.name.split('#')[1]}</small>
              {:else}
                  {rank.name}
              {/if}
            </td>
            <td class="py-2 flex items-center justify-center">
              <div class="level" style={getLevelImage(Number(rank.score))}></div>
            </td>
            <td class="py-2 text-center">
              {Number(rank.score).toLocaleString()}점
            </td>
          </tr>
          {/each}
        </tbody> 
      </table>
      <!-- Pagination -->
      <div class="flex justify-center items-center gap-x-4">
			<button
      on:click={() => currentPage--}
      class="flex items-center justify-center text-gray-400 dark:text-gray-300 hover:text-gray-500 dark:hover:text-gray-400 hover:bg-gray-200 dark:hover:bg-gray-700 py-1 px-1 rounded-full transform ease-in duration-100 active:scale-95">
        <span class="material-symbols-outlined">
            chevron_left
        </span>
      </button>
      <span class="text-gray-400 dark:text-gray-300">
        {currentPage + 1} 페이지
      </span>
			<button
      on:click={() => currentPage++}
      class="flex items-center justify-center text-gray-400 dark:text-gray-300 hover:text-gray-500 dark:hover:text-gray-400 hover:bg-gray-200 dark:hover:bg-gray-700 py-1 px-1 rounded-full transform ease-in duration-100 active:scale-95">
        <span class="material-symbols-outlined">
            chevron_right
        </span>
      </button>
      </div>
    </div>
  </div>
</div>
