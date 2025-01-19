<script>
  import { onMount } from 'svelte';
  const title = '단어장';

  let dialogOpen = false;
  let dialogTitle = '';
  let dialogContent = '';
  let dialogType = 'info';

  let themes = [];
  const searchTypes = [
    {
      "lang": "ko",
      "name": "끝말잇기"
    },
    {
      "lang": "en",
      "name": "영어 끝말"
    }
  ]
  const injAbles = `
word.theme.SYS=★
word.theme.IMS=THE iDOLM@STER
word.theme.VOC=음성 합성 엔진
word.theme.KRR=개구리 중사 케로로
word.theme.KTV=국내 방송 프로그램
word.theme.KOT=대한민국 철도역
word.theme.DOT=도타 2
word.theme.THP=동방 프로젝트
word.theme.DRR=듀라라라!!
word.theme.DGM=디지몬
word.theme.RAG=음식물/식료품
word.theme.LVL=러브 라이브!
word.theme.LOL=리그 오브 레전드
word.theme.TQS=특수촬영물
word.theme.MMM=마법소녀 마도카☆마기카
word.theme.MAP=메이플스토리
word.theme.MOB=모바일 게임
word.theme.CYP=사이퍼즈
word.theme.STA=스타크래프트
word.theme.OIJ=신조어/밈/속어/용어/유행어/명대사
word.theme.KGR=아지랑이 프로젝트
word.theme.ELW=엘소드
word.theme.OVW=오버워치
word.theme.NEX=PC게임
word.theme.WOW=월드 오브 워크래프트
word.theme.KPO=유명인
word.theme.JLN=라이트 노벨
word.theme.JAN=만화/애니메이션/웹툰
word.theme.ZEL=젤다의 전설
word.theme.POK=포켓몬스터
word.theme.HSS=하스스톤
word.theme.MOV=영화
word.theme.HDC=함대 컬렉션
word.theme.HOS=히어로즈 오브 더 스톰
word.theme.BDM=Bang Dream!
word.theme.KIO=끄투리오
word.theme.CON=콘솔 게임
word.theme.HRT=대한민국 문화재
word.theme.BRD=브랜드/회사
word.theme.SFX=특수촬영물
word.theme.NFM=소설/시/수필/동화
word.theme.WZW=위저딩 월드
word.theme.OJU=100% Orange Juice
word.theme.E2R=EZ2ON REBOOT R
word.theme.CKR=쿠키런
word.theme.MCR=마인크래프트
word.theme.MMB=모두의마블
word.theme.HSR=붕괴: 스타레일
word.theme.e03=★
word.theme.e05=동물
word.theme.e08=인체
word.theme.e12=감정
word.theme.e13=음식
word.theme.e15=지명
word.theme.e18=사람
word.theme.e20=식물
word.theme.e43=날씨
word.theme.e53=화학
word.theme.10=가톨릭
word.theme.20=건설
word.theme.30=경제
word.theme.40=고적
word.theme.50=고유
word.theme.60=공업
word.theme.70=광업
word.theme.80=교육
word.theme.90=교통
word.theme.100=군사
word.theme.110=기계
word.theme.120=기독교
word.theme.130=논리
word.theme.140=농업
word.theme.150=문학
word.theme.160=물리
word.theme.170=미술
word.theme.180=민속
word.theme.190=동물
word.theme.200=법률
word.theme.210=불교
word.theme.220=사회
word.theme.230=생물
word.theme.240=수학
word.theme.250=수산
word.theme.260=수공
word.theme.270=식물
word.theme.280=심리
word.theme.290=약학
word.theme.300=언론
word.theme.310=언어
word.theme.320=역사
word.theme.330=연영
word.theme.340=예술
word.theme.350=운동
word.theme.360=음악
word.theme.370=의학
word.theme.380=인명
word.theme.390=전기
word.theme.400=정치
word.theme.410=종교
word.theme.420=지리
word.theme.430=지명
word.theme.440=책명
word.theme.450=천문
word.theme.460=철학
word.theme.470=출판
word.theme.480=통신
word.theme.490=컴퓨터
word.theme.500=한의학
word.theme.510=항공
word.theme.520=해양
word.theme.530=화학
word.theme.1001=나라 이름과 수도
word.theme.KKT=쿵쿵따
word.theme.ODW=우리말샘
`;

  let startChar = '';
  let missionChar = '';
  let missionCharBuffer = '';
  let isSearchFocused = false;
  let isWaiting = false;
  let wordData = [];
  let sortedWordData = [];
  let sortType = 'short';
  let longestWord = 0;
  let langValue = 'ko';

  onMount(() => {
    themes = injAbles.split('\n').filter(Boolean).map(theme => {
      const [key, value] = theme.split('=');
      return { key, value };
    });

    document.addEventListener('keydown', function(e) {
      if (e.key === 'Tab') {
        e.preventDefault();
      }
      if (e.ctrlKey && e.key === 'f') {
        e.preventDefault();
      }
    });
  });

  function readText(text, lang) {
    const synth = window.speechSynthesis;
    const utterThis = new SpeechSynthesisUtterance(text);
    utterThis.lang = lang;
    synth.speak(utterThis);
  }

  function handleSearch(){
    dialogOpen = false;
    isWaiting = true;
    wordData = [];
    missionCharBuffer = missionChar;

    fetch(`/wordsheet/${langValue}/${startChar}?mission=${missionChar}`)
      .then(res => {
        if (!res.ok) {
          if (res.status === 400) {
            dialogOpen = true;
            dialogTitle = '손님 계정 이용 제한됨';
            dialogContent = '단어장 검색은 로그인 후 이용 가능합니다.';
            dialogType = 'login';
          }
          if (res.status === 402) {
            dialogOpen = true;
            dialogTitle = '단어 토큰 부족';
            dialogContent = '단어 토큰이 부족합니다.';
            dialogType = 'info';
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
            dialogOpen = true;
            dialogTitle = '손님 계정 이용 제한됨';
            dialogContent = '단어장 검색은 로그인 후 이용 가능합니다.';
            dialogType = 'login';
          }
          if (data.error === "402"){
            dialogOpen = true;
            dialogTitle = '단어 토큰 부족';
            dialogContent = '단어 토큰이 부족합니다.';
            dialogType = 'info';
          }
          if (data.error === "404"){
            wordData = [];
          }
        }
        else{
          wordData = data;
          wordData.forEach(word => {
            word.theme = word.theme.split(',').map(theme => {
              const themeObj = themes.find(t => t.key === "word.theme."+theme);
              return themeObj ? themeObj.value : theme;
            }).join(', ');
          });

          longestWord = Math.max(...wordData.map(word => word.word.length));
          handleSort();
        }
        
        isWaiting = false;
      })
      .catch(error => {
        console.error('Fetch error:', error);
        wordData = [{ message: error.message }];
        isWaiting = false;
      });
  }

  function handleSort(){
    if (sortType === 'short') {
      sortedWordData = wordData.sort((a, b) => a.word.length - b.word.length);
    }
    if (sortType === 'long') {
      sortedWordData = wordData.sort((a, b) => b.word.length - a.word.length);
    }
    if (sortType === 'abc') {
      sortedWordData = wordData.sort((a, b) => a.word.localeCompare(b.word));
    }
  }

  function confirmPayment(){
      dialogOpen = true;
      dialogType = 'payment';
      dialogTitle = '단어 토큰 안내';
    if (missionChar.length > 0){
      dialogContent = '단어 토큰 2개를 소모하여 검색하시겠습니까?';
    }
    else{
      dialogContent = '단어 토큰 1개를 소모하여 검색하시겠습니까?';
    }
    dialogContent = '한시적으로 단어 토큰이 소모되지 않습니다.';
  }
</script>

<svelte:head>
  <title>끄투리오 - {title}</title>
</svelte:head>

{#if dialogOpen}
    <!-- Fullscreen dim -->
    <div class="z-50 fixed inset-0 bg-black bg-opacity-50 flex justify-center items-center">
      <div class="bg-gray-800 text-center text-white rounded-xl p-8">
        <h2 class="text-2xl font-bold">{dialogTitle}</h2>
        <p class="mt-4 text-gray-300">{dialogContent}</p>

        {#if dialogType === 'login'}
        <a href="/login.html" class="mt-4 bg-[#55aa55] hover:bg-[#51a351] text-white font-bold py-2 px-4 rounded-lg">로그인하기</a>
        {:else if dialogType === 'payment'}
        <button class="mt-4 bg-[#55aa55] hover:bg-[#51a351] text-white font-bold py-2 px-4 rounded-lg" on:click={handleSearch}>사용하기</button>
        {:else}
        <button class="mt-4 bg-[#55aa55] hover:bg-[#51a351] text-white font-bold py-2 px-4 rounded-lg" on:click={() => dialogOpen = false}>확인</button>
        {/if}

      </div>
  </div>
{/if}

<div class="min-h-screen dark:bg-gray-900 pb-8 dark:text-white">
  <div class="flex flex-col items-center pt-32 px-4">
    <!-- Logo Section -->
    <div class="w-full max-w-2xl mb-8 flex items-center">
      <h1 class="text-4xl font-bold dark:text-white">단어장</h1>
      <span class="text-2xl text-green-600 dark:text-green-400 ml-2">Beta</span>
    </div>

    <!-- Mode Select -->
    <div class="flex w-full max-w-2xl ">
      {#each searchTypes as type}
      <button class={`px-4 py-1 font-bold rounded-t-lg
      ${langValue === type.lang ? "text-white bg-[#55aa55] hover:bg-[#51a351]" : "text-gray-600 bg-gray-200 hover:bg-gray-300"}`}
      on:click={() => langValue = type.lang}>
        {type.name}
      </button>
      {/each}
    </div>
    <!-- Search Box -->
    <div class="w-full max-w-2xl pb-8">
      <div class="relative">
        <div class={`flex items-center w-full border  dark:text-gray-200 
          ${isSearchFocused ? 'shadow-lg border-gray-300' : 'border-gray-200'} 
          dark:border-gray-700 px-5 py-3 bg-white dark:bg-gray-800`}>
          
          <!-- Search Icon -->
           {#if !isWaiting}
          <button class="material-symbols-outlined text-[#55aa55] dark:text-[#51a351] icons-header"
            on:click={confirmPayment}>
            search
          </button>
          {:else}
          <button class="material-symbols-outlined text-gray-500 dark:text-gray-300 icons-header animate-spin">
            cached
          </button>
          {/if}

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
            <span class="mr-2 dark:text-gray-200">(으)로 시작하는</span>
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

      <p class="text-sm mt-4 text-gray-600 dark:text-gray-300 w-full max-w-2xl">
        최대 1자 입력 가능하며, 미션 단어는 필수가 아닙니다.
      </p>

    </div>

    {#if isWaiting}
    <div class="mt-8">
      <div class="flex items-center justify-center dark:text-gray-200">
        끄투리오의 DB를 검색 중입니다...
      </div>
    </div>
    {:else}
    <!-- Info Section -->
     {#if sortedWordData.length > 0}
    <div class="mt-8">
      <div class="flex items-center justify-between dark:text-gray-200 w-full max-w-2xl">
        <h3 class="text-lg font-bold">최장문 {longestWord}자</h3>
        <select class="rounded-lg border dark:border-gray-700 dark:bg-gray-800 dark:text-white" bind:value={sortType} on:change={handleSort}>
          <option value="short">짧은 순</option>
          <option value="long">긴 순</option>
          <option value="abc">가나다 순</option>
        </select>
      </div>
    </div>
     {#each sortedWordData as word}
      <div class="w-full max-w-2xl mt-4">
        <div class="bg-white dark:bg-gray-800 dark:text-white p-4 border-b dark:border-gray-700">
          <div class="flex items-center gap-x-4">
          <h2 class="text-lg font-bold">
            {@html word.word.replace(missionCharBuffer, `<span class="text-green-600">${missionCharBuffer}</span>`)}
          </h2>
          <button class="ml-2" on:click={() => readText(word.word, langValue)}>
            <span class="flex items-center justify-center text-gray-400 dark:text-gray-300 hover:text-gray-500 dark:hover:text-gray-400 hover:bg-gray-200 dark:hover:bg-gray-700 py-1 px-1 rounded-full transform ease-in duration-100 active:scale-95">
              volume_up
            </span>
          </button>
          </div>
          <p class="mt-2 text-sm text-gray-600 dark:text-gray-300">
            글자 길이: {word.word.length}자 |
            주제: {word.theme} |
            <a href={`https://wiki.kkutu.io/w/${word.word}`} class="text-blue-600 dark:text-blue-400">리오위키에서 검색하기</a>
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