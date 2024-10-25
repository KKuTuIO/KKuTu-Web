<script>
    import { onMount } from 'svelte';
    const title = '로그인';

  </script>
  
  <svelte:head>
      <title>끄투리오 - {title}</title>
  </svelte:head>
  <div class="dark:bg-gray-900 flex min-h-screen flex-col justify-center items-center px-6 py-12 lg:px-8">
    <img src="https://cdn.kkutu.io/logo/fusion/daldalso.png" class="h-12" alt="Daldalso">
    <div class="sm:mx-auto sm:w-full">
      <h2 class="mt-10 text-center text-5xl font-bold tracking-tight text-gray-900 dark:text-gray-100">
        달달소를 통한<br>
        신규 가입은 불가능합니다.
      </h2>
    </div>
  
      <p class="mt-10 text-center text-sm text-gray-500 dark:text-gray-300">
        <a href="/login.html" rel="external" class="font-semibold leading-6 text-green-600 hover:text-green-500 dark:text-green-300 dark:hover:text-green-500">
          다른 방법 사용하기
        </a><br><br>
        &copy; 끄투리오운영, 모든 권리 보유.
      </p>
  </div>