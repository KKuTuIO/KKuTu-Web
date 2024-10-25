<script>
    import { onMount } from 'svelte';
    const title = '환영합니다';
  </script>
  
  <svelte:head>
      <title>끄투리오 - {title}</title>
  </svelte:head>
  <div class="dark:bg-gray-900 flex min-h-screen flex-col justify-center items-center px-6 py-12 lg:px-8">
    <div class="sm:mx-auto sm:w-full sm:max-w-sm">
      <h2 class="mb-2 text-center text-3xl font-semibold leading-9 tracking-wider text-gray-900 dark:text-gray-100">
        신규 유저님,
      </h2>
      <h1 class="mt-2 mb-4 text-center text-5xl font-extrabold tracking-tight bg-gradient-to-r from-blue-600 to-purple-500 text-transparent bg-clip-text">
        만나서 반가워요!
      </h1>
    </div>
    <p class="text-xl text-gray-500 dark:text-gray-300 mt-4">
      먼저 게임에서 사용할 별명을 설정해 볼까요?
    </p>
  
    <div class="mt-4 sm:mx-auto sm:w-full ">
      <p class="mt-10 text-center text-sm text-gray-500 dark:text-gray-300">
        회원가입 또는 로그인을 하면 끄투리오의 <a href="https://cs.kkutu.io/terms" target="_blank" rel="noopener" class="link-signin">서비스 이용약관</a>과 <a href="https://cs.kkutu.io/operation" target="_blank" rel="noopener" class="link-signin">운영정책</a>, <a href="https://cs.kkutu.io/privacy-policy" target="_blank" rel="noopener" class="link-signin">개인정보처리방침</a>에 동의하는 것으로 봅니다.
      </p>
    </div>
  </div>