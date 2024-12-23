<script>
  import { onMount } from 'svelte';
  const title = '단어장';

  let startChar = '';
  let missionChar = '';
  let isSearchFocused = false;
  let isWaiting = false;
  let wordData = [];
  let langValue = 'ko';

  onMount(() => {
    document.addEventListener('keydown', function(e) {
      if (e.key === 'Tab') {
        e.preventDefault();
      }
      if (e.ctrlKey && e.key === 'f') {
        e.preventDefault();
      }
    });
  });

  function handleSearch(){
    isWaiting = true;
    wordData = [];

    fetch(`/wordsheet/${langValue}/${startChar}?mission=${missionChar}`)
      .then(res => {
        if (!res.ok) {
          if (res.status === 400) {
            alert('손님 계정으로는 검색이 불가능합니다.\n로그인 후 이용해주세요.');
          }
          if (res.status === 404) {
            wordData = [];
          }
          throw new Error('Network response was not ok');
        }
        return res.json();
      })
      .then(data => {
        if (data.error !== undefined) {
          if (data.error === "400"){
            alert('손님 계정으로는 검색이 불가능합니다.\n로그인 후 이용해주세요.');
          }
          if (data.error === "404"){
            wordData = [];
          }
        }
        else{
          wordData = data;
        }
        
        isWaiting = false;
      })
      .catch(error => {
        console.error('Fetch error:', error);
        wordData = [{ message: error.message }];
        isWaiting = false;
      });
  }
</script>

<svelte:head>
  <title>끄투리오 - {title}</title>
</svelte:head>

<div class="min-h-screen dark:bg-gray-900 pb-8 dark:text-white">
  <div class="flex flex-col items-center pt-28 px-4">
    <!-- Logo Section -->
    <div class="mb-8 flex justify-center items-center">
      <h1 class="text-4xl font-bold">단어장</h1>
      <span class="text-2xl text-green-600 dark:text-green-400 ml-2">Beta</span>
    </div>

    <!-- Search Box -->
    <div class="w-full max-w-2xl pb-8">
      <div class="relative">
        <div class={`flex items-center w-full rounded-full border 
          ${isSearchFocused ? 'shadow-lg border-gray-300' : 'border-gray-200'} 
          dark:border-gray-700 px-5 py-3 bg-white dark:bg-gray-800`}>
          
          <!-- Search Icon -->
          <span class="material-symbols-outlined text-gray-400 dark:text-gray-500">
            search
          </span>

          <!-- Search Input -->
          <div class="flex px-4">
            <input
              type="text"
              bind:value={startChar}
              on:focus={() => isSearchFocused = true}
              on:blur={() => isSearchFocused = false}
              placeholder="시작"
              maxlength="1"
              required
              class="font-bold text-center w-8 outline-none bg-transparent dark:text-white"
            />
            <span class="mr-2">(으)로 시작하는</span>
            <!--<span class="hidden md:inline-block">(으)로 시작하고,</span>
            <span class="md:hidden">-</span>
            <input
              type="text"
              bind:value={endChar}
              on:keydown={handleSearch}
              on:focus={() => isSearchFocused = true}
              on:blur={() => isSearchFocused = false}
              placeholder="끝"
              maxlength="1"
              class="font-bold text-center w-8 outline-none bg-transparent dark:text-white"
            />
            <span class="hidden md:inline-block">(으)로 끝나는</span>
            <span class="md:hidden">의 </span>-->
            <input
              type="text"
              bind:value={missionChar}
              on:focus={() => isSearchFocused = true}
              on:blur={() => isSearchFocused = false}
              placeholder="미션"
              maxlength="1"
              class="font-bold text-center w-8 outline-none bg-transparent dark:text-white"
            />&nbsp;미션 단어
          </div>
        </div>
      </div>
      <div class="flex justify-center mt-4">
        <label class="flex items-center gap-2">
          <input type="radio" name="language" value="ko" checked on:change={() => langValue = 'ko'}>
          <span>한국어</span>
        </label>
        <label class="flex items-center gap-2 ml-4">
          <input type="radio" name="language" value="en" on:change={() => langValue = 'en'}>
          <span>영어</span>
        </label>
      </div>

      <!-- Search Buttons -->
       {#if !isWaiting}
      <div class="flex justify-center gap-4 mt-4">
        <button class="bg-[#55aa55] hover:bg-[#51a351] font-bold text-white flex rounded-full py-1 px-3 transform ease-in duration-100 active:scale-95 hover:backdrop-blur-lg"
          on:click={handleSearch}>
          검색
        </button>
      </div>
      {/if}

      <p class="text-center text-sm mt-4 text-gray-600">
        최대 1자 입력 가능하며, 미션 단어는 필수가 아닙니다.<br>
        검색 버튼을 눌러야 검색할 수 있습니다.
      </p>

    </div>

    {#if isWaiting}
    <div class="mt-8">
      <div class="flex items-center justify-center">
        끄투리오의 DB를 검색 중입니다...
      </div>
    </div>
    {:else}
    <!-- Info Section -->
     {#if wordData.length > 0}
     {#each wordData as word}
      <div class="w-full max-w-2xl mt-4">
        <div class="bg-white dark:bg-gray-800 dark:text-white p-4 rounded-lg border dark:border-gray-700">
          <h2 class="text-lg font-bold">{word.word}</h2>
          <p class="mt-2 text-sm text-gray-600">
            글자 길이: {word.word.length}자 |
            주제 ID: {word.theme}
          </p>
        </div>
      </div>
    {/each}
    {:else}
    검색 결과가 없습니다.
    {/if}
    {/if}

  </div>
</div>