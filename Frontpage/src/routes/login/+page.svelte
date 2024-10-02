<script>
    import { onMount } from 'svelte';
    const title = '로그인';
  </script>
  
  <svelte:head>
      <title>끄투리오 - {title}</title>
  </svelte:head>
  <div class="dark:bg-gray-900 flex min-h-screen flex-col justify-center px-6 py-12 lg:px-8">
    <div class="sm:mx-auto sm:w-full sm:max-w-sm">
      <h2 class="mt-10 text-center text-3xl font-bold leading-9 tracking-tight text-gray-900 dark:text-gray-100">
        로그인
      </h2>
    </div>
  
    <div class="mt-4 sm:mx-auto sm:w-full ">
        <div class="sm:mx-auto sm:sm:max-w-sm">
          <a href="https://kkutu.io/login/naver">
            <button class="text-lg bg-[#03C75A] text-white mt-4 flex w-full justify-center p-3 items-center font-semibold leading-6 shadow-md transform ease-in duration-100 active:scale-95">
              <img src="https://cdn.kkutu.io/logo/fusion/naver.svg" class="h-6 mr-2" alt="NAVER"/>
              네이버로 로그인
            </button>
          </a>
          <a href="https://kkutu.io/login/google">
          <button class="text-lg bg-gray-100 hover:bg-gray-200 text-black mt-4 flex w-full justify-center p-3 items-center font-semibold leading-6 shadow-md transform ease-in duration-100 active:scale-95">
            <img src="https://cdn.kkutu.io/logo/fusion/google.svg" class="h-6 mr-2" alt="Google"/>
            Google로 로그인
          </button>
          </a>
          <a href="https://kkutu.io/login/kakao">
            <button class="text-lg bg-[#FFDE00] text-[#3C1E1E] mt-4 flex w-full justify-center p-3 items-center font-semibold leading-6 shadow-md transform ease-in duration-100 active:scale-95">
              <img src="https://cdn.kkutu.io/logo/fusion/kakao.svg" class="h-6 mr-2" alt="Kakao"/>
              카카오로 로그인
            </button>
          </a>
          <a href="https://kkutu.io/login/discord">
            <button class="text-lg bg-[#5865F2] text-white mt-4 flex w-full justify-center p-3 items-center font-semibold leading-6 shadow-md transform ease-in duration-100 active:scale-95">
              <img src="https://cdn.kkutu.io/logo/fusion/discord.svg" class="h-6 mr-2" alt="Discord"/>
              디스코드로 로그인
            </button>
          </a>
          <a href="https://kkutu.io/login/daldalso">
            <button class="text-lg bg-[#20318D] text-white mt-4 flex w-full justify-center p-3 items-center font-semibold leading-6 shadow-md transform ease-in duration-100 active:scale-95">
              <img src="https://cdn.kkutu.io/logo/fusion/daldalso.png" class="h-6 mr-2" alt="Daldalso"/>
              <span class="text-[#FFE101]">달달소</span>로 로그인 (신규 가입 불가)
            </button>
            </a>
        </div>
      <p class="mt-10 text-center text-sm text-gray-500 dark:text-gray-300">
        회원가입 또는 로그인을 하면 끄투리오의 <a href="https://cs.kkutu.io/terms" target="_blank" rel="noopener" class="link-signin">서비스 이용약관</a>과 <a href="https://cs.kkutu.io/operation" target="_blank" rel="noopener" class="link-signin">운영정책</a>, <a href="https://cs.kkutu.io/privacy-policy" target="_blank" rel="noopener" class="link-signin">개인정보처리방침</a>에 동의하는 것으로 봅니다.
      </p>
    </div>
  </div>