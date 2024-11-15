<script>
  import { onMount } from 'svelte';
  const title = '랭킹';
  const rankColor = ["yellow-500", "green-500", "blue-500", "purple-500"];

  import { getLevelImage } from '../../lib/getLevelImg.js';

</script>

<svelte:head>
  <title>끄투리오 - {title}</title>
</svelte:head>
<div class="dark:bg-gray-900">
  <div class="pt-32 px-4 pb-24 flex flex-col items-center rankBg">
      <h1 class="text-white text-5xl font-bold mb-2">TBD 님의 전적</h1>
  </div>
  <div class="lg:shadow-md mx-2 lg:mx-auto max-w-screen-xl -mt-16 mb-24 p-2 lg:p-4 bg-gray-100 dark:bg-gray-800 dark:text-white rounded-lg">
   TBD
    </div>
  </div>