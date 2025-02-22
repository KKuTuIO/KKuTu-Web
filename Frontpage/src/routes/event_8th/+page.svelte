<script nonce="kkutuio">
  import { onMount } from 'svelte';
  const title = '8주년 행사';
  import Snow from '../../lib/snow.svelte';

  let selectedWeek = 2;
</script>

<style>
  .logoJellyEffect {
    animation: jelly 0.5s;
  }

  .textFadeUp{
    opacity: 0;
    animation: fadeInUp 1s;
    animation-delay: 0.5s;
    animation-fill-mode: forwards;
  }

  .countdown {
    opacity: 0;
    animation: fadeInUp 1s;
    animation-delay: 1s;
    animation-fill-mode: forwards;
  }

  @keyframes jelly {
    0%,
    100% {
      transform: scale(1, 1);
    }
    25% {
      transform: scale(1.25, 0.75);
    }
    50% {
      transform: scale(0.75, 1.25);
    }
    75% {
      transform: scale(1.15, 0.85);
    }
  }

  @keyframes fadeInUp {
    from {
      opacity: 0;
      transform: translateY(20px);
    }
    to {
      opacity: 1;
      transform: translateY(0);
    }
  }
</style>

<svelte:head>
    <title>끄투리오 - {title}</title>
</svelte:head>

<!-- Mobile warning -->
<div class="lg:hidden fixed bottom-0 left-0 right-0 bg-red-500 text-white text-center py-2">
  <p class="text-sm">가급적 PC 환경에서 이용해주세요.</p>
</div>
<!-- 8주년 카운트다운 -->
 <div class="h-screen min-h-[900px] flex flex-col items-center justify-center"
 style="
background: linear-gradient(180deg, rgba(186,230,253,1) 0%, rgba(186,230,253,1) 95%, rgba(255,255,255,1) 96%, rgba(255,255,255,1) 100%);">
    <div class="flex flex-col items-center justify-center">
        <img src="https://cdn.kkutu.io/img/front/%EC%97%85%EB%8D%B0%EC%9D%B4%ED%8A%B8%EB%A6%B4%EB%A0%88%EC%9D%B4%20%EB%A1%9C%EA%B3%A0.png" class="logoJellyEffect h-36 lg:h-72" alt="업데이트 릴레이">
        <h3 class="lg:text-3xl text-[#0A87BF] font-semibold mt-8 text-center textFadeUp">
          <span class="text-[#002156] font-bold">8주년이니까, 8주간 이어지는 업데이트와 행사!</span><br>
          8주년을 맞아 끄투리오에서 즐거운 시간을 보내보세요!
        </h3>
    </div>
    <!--<Snow />-->
</div>
<div class="min-h-screen py-12 flex flex-col items-center justify-center"
style="background: linear-gradient(179deg, rgba(255,255,255,1) 0%, rgba(255,255,255,1) 95.5%, rgba(190,18,60,1) 96%, rgba(190,18,60,1) 100%);">
    <h2 class="rounded-full bg-[#002156] text-white px-4 py-2 lg:text-2xl mb-4">
      12개의 새로운 아이템 추가
    </h2>
    <h1 class="text-4xl lg:text-7xl text-rose-950 font-bold my-4 text-center leading-tight">
      <span class="text-[#002156]">8주년</span> 기념,<br>
      <span class="text-[#002156]">8주간</span> 이어지는 패치!
    </h1>
    <h3 class="text-[#0A87BF] text-center lg:text-3xl mt-4 mb-6">
      매주 업데이트되는 새로운 컨텐츠를 확인하세요!
    </h3>
    <!-- Week select -->
     <div class="flex gap-x-4 my-6">
        <button class={selectedWeek === 0 ? 'bg-[#002156] text-white px-6 py-2 rounded-full lg:text-2xl' : 'bg-gray-200 text-[#002156] px-6 py-2 rounded-full lg:text-2xl'}
        on:click={() => selectedWeek = 0}>
          1주차
        </button>
        <button class={selectedWeek === 1 ? 'bg-[#002156] text-white px-6 py-2 rounded-full lg:text-2xl' : 'bg-gray-200 text-[#002156] px-6 py-2 rounded-full lg:text-2xl'}
        on:click={() => selectedWeek = 1}>
          2주차
        </button>
        <button class={selectedWeek === 2 ? 'bg-[#002156] text-white px-6 py-2 rounded-full lg:text-2xl' : 'bg-gray-200 text-[#002156] px-6 py-2 rounded-full lg:text-2xl'}
        on:click={() => selectedWeek = 2}>
          3주차
        </button>
        <!--<button class={selectedWeek === 3 ? 'bg-[#002156] text-white px-6 py-2 rounded-full lg:text-2xl' : 'bg-gray-200 text-[#002156] px-6 py-2 rounded-full lg:text-2xl'}
        on:click={() => selectedWeek = 3}>
          4주차
        </button>
        <button class={selectedWeek === 4 ? 'bg-[#002156] text-white px-6 py-2 rounded-full lg:text-2xl' : 'bg-gray-200 text-[#002156] px-6 py-2 rounded-full lg:text-2xl'}
        on:click={() => selectedWeek = 4}>
          5주차
        </button>
        <button class={selectedWeek === 5 ? 'bg-[#002156] text-white px-6 py-2 rounded-full lg:text-2xl' : 'bg-gray-200 text-[#002156] px-6 py-2 rounded-full lg:text-2xl'}
        on:click={() => selectedWeek = 5}>
          6주차
        </button>
        <button class={selectedWeek === 6 ? 'bg-[#002156] text-white px-6 py-2 rounded-full lg:text-2xl' : 'bg-gray-200 text-[#002156] px-6 py-2 rounded-full lg:text-2xl'}
        on:click={() => selectedWeek = 6}>
          7주차
        </button>
        <button class={selectedWeek === 7 ? 'bg-[#002156] text-white px-6 py-2 rounded-full lg:text-2xl' : 'bg-gray-200 text-[#002156] px-6 py-2 rounded-full lg:text-2xl'}
        on:click={() => selectedWeek = 7}>
          8주차
        </button>-->
     </div>
    <!-- Week contents -->
     <img
     src={`https://cdn.kkutu.io/img/front/relay_week${selectedWeek + 1}.png`}
      alt={`week${selectedWeek + 1}`}
      class="w-full lg:w-[1000px] lg:min-h-[600px]"
    />

    <!-- View full notice -->
      <a
      href="https://cafe.naver.com/kkutuio/1620" 
      class="text-[#002156] my-16 py-2 px-6 lg:py-5 lg:px-16 rounded-xl lg:text-3xl font-bold bg-blue-100 border-2 border-blue-100 hover:border-blue-900 shadow-md lg:shadow-lg"
      target="_blank">
        카페 공지 바로가기
      </a>
</div>
<div class="pt-12 pb-36 flex flex-col items-center justify-center "
style="background: linear-gradient(181deg, rgba(190,18,60,1) 0%, rgba(190,18,60,1) 95.5%, rgba(21, 128, 61 ,1) 96%, rgba(21, 128, 61,1) 100%);">
  <h2 class="rounded-full bg-rose-800 text-white px-4 py-2 lg:text-2xl mb-4">
    고마워요, 사랑해요!
  </h2>
  <h1 class="text-4xl lg:text-7xl text-white font-bold my-4 text-center leading-tight">
    피날레를 장식할<br>
    <span class="text-yellow-400">모레미 굿즈</span> 당첨의 기회!
  </h1>
  <h3 class="text-rose-100 lg:text-3xl mt-4 mb-6">
    8주차에 진행되는 행사를 놓치지 마세요!
  </h3>
  <div class="bg-rose-800 my-8 flex flex-col justify-center items-center gap-y-4 p-8 rounded-xl">
    <span class="material-symbols-outlined text-rose-100 text-5xl">
      lock
    </span>
    <h3 class="text-rose-100 text-3xl font-bold">
      2월 13일 오픈 예정
    </h3>
    <p class="text-rose-100 text-xl">
      점검 후 자세한 내용을 확인하세요!
    </p>
  </div>
  <div class="text-xs lg:text-base text-rose-200 text-center">
  * 별도 판매하지 않는 아이템입니다.<br>
  * 상품 수령을 위해 구글 설문지에 제공한 개인 정보를 활용하며, 이벤트 종료 후 1주일 이내에 파기됩니다.
  </div>
</div>

<!-- Free premium event -->
 <div class="py-12 flex flex-col items-center justify-center bg-green-700">
  <h2 class="rounded-full bg-green-800 text-white px-4 py-2 lg:text-2xl mb-4">
    행사 기간 멤버십 무료 체험
  </h2>
  <h1 class="text-4xl lg:text-7xl text-white font-bold my-4 text-center leading-tight">
    누구나 함께 즐기는<br>
    <span class="text-yellow-400">멤버십 특권!</span>
  </h1>
  <h3 class="text-green-100 lg:text-3xl mt-4 mb-6">
    7주차에 진행되는 행사를 놓치지 마세요!
  </h3>
  <div class="bg-green-800 my-8 flex flex-col justify-center items-center gap-y-4 p-8 rounded-xl">
    <span class="material-symbols-outlined text-green-100 text-5xl">
      lock
    </span>
    <h3 class="text-green-100 text-3xl font-bold">
      2월 6일 오픈 예정
    </h3>
    <p class="text-green-100 text-xl">
      점검 후 자세한 내용을 확인하세요!
    </p>
  </div>
  <div class="text-xs lg:text-base text-green-200 text-center">
  * 8주년 행사가 종료된 후, 멤버십은 자동 해지됩니다.<br>
  * 멤버십 혜택 중 방 인원 증가, 과거 배경음악 듣기를 사용할 수 있습니다.
  </div>
 </div>

 <!-- Footer -->
  <div class="bg-slate-100 text-slate-500 py-8 text-center">
    <p>© 주식회사 큐브시티, 모든 권리 보유</p>
  </div>