<script nonce="kkutuio">
  import { onMount } from 'svelte';
  let title = 'OKG.GG';
  let currentStatus = 'main';

  onMount(() => {
    const urlParams = new URLSearchParams(window.location.search);
    const uid = urlParams.get('uid');
    if (uid) {
      currentStatus = 'user';
      title = '유저 정보';
    }
  });
</script>

<svelte:head>
  <title>끄투리오 - {title}</title>
</svelte:head>

<!-- 페이지별 컨텐츠 -->
<div class="dark:bg-gray-900">
  <div class="min-h-screen h-full py-48 px-4 flex flex-col items-center rankBg">
      <p class="text-gray-300 text-2xl my-4">공식이 내놓는</p>
      <h1 class="text-white text-5xl font-bold mb-2">끄투리오 전적 검색</h1>
      <div class="flex items-center border-3 border-white rounded-full p-2 mt-12">
        <input type="text" class="ml-4 w-72 bg-transparent text-white" placeholder="식별번호를 입력하세요."/>
        <button class="text-white px-4">
          <i class="material-symbols-outlined icons-header">search</i>
        </button>
      </div>
  </div>
  {#if currentStatus === 'main'}
  {:else}
    <div class="lg:shadow-md mx-2 lg:mx-auto max-w-screen-xl -mt-16 mb-24 p-2 lg:p-4 bg-gray-100 dark:bg-gray-800 dark:text-white rounded-lg">
      ㅗㅕㅗㅕㅗㅕ
    </div>
  {/if}
</div>