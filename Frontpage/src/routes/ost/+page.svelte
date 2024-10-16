<script>
  import { onMount } from 'svelte';
  const title = 'OST 아카이브';
  
  const songs = [
    { title: "오리지널 끄투", url: "https://cdn.kkutu.io/media/LobbyBGM.mp3", artist: "" },
    { title: "끄투리오 2017", url: "/bgm/LobbyBGM_2017.mp3", artist: "" },
    { title: "끄투리오 명절", url: "/bgm/LobbyBGM_KKuTuIO_Seol.mp3", artist: "" },
    { title: "새로운 시작", url: "https://cdn.kkutu.io/media/LobbyBGM_NEW.mp3", artist: "Hexacube" },
    { title: "끄투리오 2020 여름", url: "https://cdn.kkutu.io/media/LobbyBGM_2020_Summer.mp3", artist: "Hexacube" },
    { title: "끄투리오 2020 가을", url: "https://cdn.kkutu.io/media/LobbyBGM_2020_Fall.mp3", artist: "Hexacube" },
    { title: "Breakdown Fragment", url: "https://cdn.kkutu.io/media/LobbyBGM_BF.mp3", artist: "Hexacube" },
    { title: "끄투리오 2020 겨울", url: "https://cdn.kkutu.io/media/LobbyBGM_2020_Winter.mp3", artist: "Hexacube" },
    { title: "끄투리오 2022 가을", url: "https://cdn.kkutu.io/media/LobbyBGM_2022_Fall.flac", artist: "Hexacube" },
    { title: "끄투리오 6주년", url: "https://cdn.kkutu.io/media/LobbyBGM_2023_6th.flac", artist: "Hexacube" },
    { title: "끄투리오 2023 여름", url: "https://cdn.kkutu.io/media/LobbyBGM_2023_Summer.flac", artist: "Hexacube" },
    { title: "끄투리오 신년맞이", url: "https://cdn.kkutu.io/media/LobbyBGM_2023_Winter.mp3", artist: "Hexacube" },
    { title: "다함께 끄투리오!", url: "https://cdn.kkutu.io/media/LobbyBGM_Plus_S1.mp3", artist: "Xpolcore" },
    { title: "끄투리오 Lo-fi", url: "https://cdn.kkutu.io/media/LobbyBGM_Plus_S2.mp3", artist: "Roman Rajczyk" },
    { title: "끄투리오 신년맞이", url: "https://cdn.kkutu.io/media/LobbyBGM_2023_Winter.mp3", artist: "Hexacube" },
    { title: "끄투리오 2024 봄", url: "https://cdn.kkutu.io/media/LobbyBGM_2024_Spring.mp3", artist: "Hexacube" },
    { title: "끄투리오 2024 가을", url: "https://cdn.kkutu.io/media/LobbyBGM_2024_Fall.mp3", artist: "BGM Farm" }
  ];

  let currentSong = null;
  
  function playSong(song) {
    currentSong?.pause();
    currentSong = new Audio(song.url);
    currentSong.play();
  }
</script>

<svelte:head>
  <title>끄투리오 - {title}</title>
</svelte:head>
<div class="dark:bg-gray-900">
  <div class="mt-24 mx-auto max-w-screen-xl px-4 py-8 ">
    <h1 class="text-3xl font-bold mb-2">OST 아카이브</h1>
    <p class="text-gray-500 dark:text-gray-300 mb-8"><strong>고음질로 다시 만나는 추억의 OST!</strong>&nbsp;<br class="lg:hidden">끄투리오의 다양한 OST를 감상해보세요.</p>
    <ul>
      {#each songs as song}
        <li class="mb-3 flex items-center justify-between p-4 bg-gray-100 dark:bg-gray-800 rounded-xl">
          <div>
            <h3 class="text-lg font-bold dark:text-white">{song.title}</h3>
            <span class="text-sm text-gray-500 dark:text-gray-300">
              {#if song.url.endsWith('.flac')}
                <span class="bg-[#FF6347] text-white px-1 rounded-md">FLAC</span>
              {/if}
              {song.artist}
            </span>
          </div>
          <button 
            class="bg-[#3553A0] hover:bg-[#213464] text-white w-9 h-9 rounded-full transform ease-in duration-100 active:scale-95 "
            on:click={() => playSong(song)}
          >▶</button>
        </li>
      {/each}
    </ul>
  </div>
</div>