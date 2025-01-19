<script>
    import { onMount } from 'svelte';
    const title = '로그인';

  </script>
  
  <svelte:head>
      <title>끄투리오 - {title}</title>
  </svelte:head>
  <div class="dark:bg-gray-900 flex min-h-screen flex-col justify-center px-6 py-12 lg:px-8">
    <div class="sm:mx-auto sm:w-full">
      <h2 class="mt-10 text-center text-5xl font-bold leading-9 tracking-tight text-gray-900 dark:text-gray-100">
        로그인에 실패했습니다.
      </h2>
    </div>
  
      <p class="mt-10 text-center text-sm text-gray-500 dark:text-gray-300">
        <a href="/login.html" rel="external" class="font-semibold leading-6 text-green-600 hover:text-green-500 dark:text-green-300 dark:hover:text-green-500">
          다시 시도하기
        </a><br><br>
        &copy; 주식회사 큐브시티, 모든 권리 보유.
      </p>
  </div>