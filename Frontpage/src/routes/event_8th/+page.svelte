<script>
  import { onMount } from 'svelte';
  const title = '8주년 행사';
</script>

<svelte:head>
    <title>끄투리오 - {title}</title>
</svelte:head>

<!-- 8주년 카운트다운 -->
 <div class="h-screen flex items-center justify-center"
    style="background-image: url(landing.png); background-size: cover; background-position: center;">
    <div class="text-center">
        <h1 class="text-4xl font-bold eighthTitle">끄투리오 8주년</h1>
        <h1 class="text-6xl font-extrabold text-white mt-2 mb-8">[우리]의 추억을 찾아서</h1>
        <div class="bg-gray-800 rounded-xl p-4 flex items-center justify-center text-white">
            <div>
                <p class="text-4xl font-bold">3</p>
                <p class="text-sm text-gray-200">MONTHS</p>
            </div>
            <div>
              <p class="text-4xl font-bold">3</p>
              <p class="text-sm text-gray-200">DAYS</p>
            </div>
            <div>
              <p class="text-4xl font-bold">3</p>
              <p class="text-sm text-gray-200">DAYS</p>
          </div>
        </div>
    </div>
</div>