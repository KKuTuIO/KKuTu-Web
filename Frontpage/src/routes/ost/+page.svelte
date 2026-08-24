<script>
  import { onMount } from 'svelte';
  const title = 'OST 아카이브';

  const songs = [
    {
      title: "끄투리오 2026 봄",
      url: "https://cdn.kkutu.io/media/LobbyBGM_2026_Spring.mp3",
      artist: "KKuTuIO",
      cover: "null",
      isNew: "false"
    },
    {
      title: "끄투리오 2025 겨울",
      url: "https://cdn.kkutu.io/media/LobbyBGM_2025_Winter.mp3",
      artist: "KKuTuIO",
      cover: "null",
      isNew: "false"
    },
    {
      title: "끄투리오 8주년",
      url: "https://cdn.kkutu.io/media/LobbyBGM_2025_8th.mp3",
      artist: "BGM Farm",
      cover: "null",
      isNew: "false"
    },
    {
      title: "끄투리오 2024 겨울",
      url: "https://cdn.kkutu.io/media/LobbyBGM_2024_Winter.mp3",
      artist: "KKuTuIO",
      cover: "null",
      isNew: "false"
    },
    {
      title: "끄투리오 2024 가을",
      url: "https://cdn.kkutu.io/media/LobbyBGM_2024_Fall.mp3",
      artist: "BGM Farm",
      cover: "fall2024",
      isNew: "false"
    },
    {
      title: "끄투리오 신년맞이",
      url: "https://cdn.kkutu.io/media/LobbyBGM_2023_Winter.mp3",
      artist: "Hexacube",
      cover: "null",
      isNew: "false"
    },
    {
      title: "끄투리오 2022 가을",
      url: "https://dl.kkutu.io/media/LobbyBGM_2022_Fall.flac",
      artist: "Hexacube",
      cover: "null",
      isNew: "false"
    },
    {
      title: "끄투리오 6주년",
      url: "https://dl.kkutu.io/media/LobbyBGM_2023_6th.flac",
      artist: "Hexacube",
      cover: "null",
      isNew: "false"
    },
    {
      title: "끄투리오 2023 여름",
      url: "https://dl.kkutu.io/media/LobbyBGM_2023_Summer.flac",
      artist: "Hexacube",
      cover: "null",
      isNew: "false"
    },
    {
      title: "끄투리오 2024 봄",
      url: "https://cdn.kkutu.io/media/LobbyBGM_2024_Spring.mp3",
      artist: "Hexacube",
      cover: "null",
      isNew: "false"
    },
    {
      title: "새로운 시작",
      url: "https://cdn.kkutu.io/media/LobbyBGM_NEW.mp3",
      artist: "BerylKing",
      cover: "null",
      isNew: "false"
    },
    {
      title: "끄투리오 2020 가을",
      url: "https://cdn.kkutu.io/media/LobbyBGM_2020_Fall.mp3",
      artist: "Hexacube",
      cover: "null",
      isNew: "false"
    },
    {
      title: "끄투리오 Lo-fi",
      url: "https://cdn.kkutu.io/media/LobbyBGM_Plus_S2.mp3",
      artist: "Roman Rajczyk",
      cover: "plusS2",
      isNew: "false"
    },
    {
      title: "다함께 끄투리오!",
      url: "https://cdn.kkutu.io/media/LobbyBGM_Plus_S1.mp3",
      artist: "Xpolcore",
      cover: "plusS1",
      isNew: "false"
    },
    {
      title: "오리지널 끄투",
      url: "https://cdn.kkutu.io/media/LobbyBGM.mp3",
      artist: "JJoriping",
      cover: "kkutu",
      isNew: "false"
    },
    {
      title: "끄투 명절",
      url: "https://cdn.kkutu.io/media/LobbySeolBGM.mp3",
      artist: "JJoriping",
      cover: "kkutu",
      isNew: "false"
    },
    {
      title: "끄투리오 명절",
      url: "https://cdn.kkutu.io/media/LobbyBGM_KKuTuIO_Seol.mp3",
      artist: "KKuTuIO",
      cover: "null",
      isNew: "false"
    },
    {
      title: "끄투리오 2020 여름",
      url: "https://dl.kkutu.io/media/LobbyBGM_2020_Summer.mp3",
      artist: "Hexacube",
      cover: "null",
      isNew: "false"
    },
    {
      title: "Breakdown Fragment",
      url: "https://dl.kkutu.io/media/LobbyBGM_Breakdown_Fragment.mp3",
      artist: "Hexacube",
      cover: "null",
      isNew: "false"
    },
    {
      title: "끄투리오 2020 겨울",
      url: "https://cdn.kkutu.io/media/LobbyBGM_2020_Winter.mp3",
      artist: "Hexacube",
      cover: "null",
      isNew: "false"
    },
    {
      title: "끄투리오 2017",
      url: "https://dl.kkutu.io/media/LobbyBGM_2017.mp3",
      artist: "Seunghin",
      cover: "dotnet",
      isNew: "false"
    },
    {
      title: "끄투리오 2018",
      url: "https://dl.kkutu.io/media/LobbyBGM_2018.mp3",
      artist: "Seunghin",
      cover: "dotnet",
      isNew: "false"
    },
    {
      title: "끄투리오 2019 Vol. 1",
      url: "https://dl.kkutu.io/media/LobbyBGM_2019_v1.mp3",
      artist: "Sonhun",
      cover: "dotnet",
      isNew: "false"
    },
    {
      title: "끄투리오 2019 Vol. 2",
      url: "https://dl.kkutu.io/media/LobbyBGM_2019_v2.mp3",
      artist: "Sonhun",
      cover: "dotnet",
      isNew: "false"
    }
  ];

  let currentSong = $state(null);
  
  function playSong(song) {
    currentSong?.pause();
    currentSong = new Audio(song.url);
    currentSong.play();
    currentSong.loop = true;
  }
</script>

<svelte:head>
  <title>끄투리오 - {title}</title>
</svelte:head>
<div class="dark:bg-gray-900">
  <div class="pt-32 mx-auto max-w-screen-xl px-4 py-8 ">
    <div class="flex flex-col lg:flex-row lg:items-center lg:justify-between mb-8">
      <div>
        <h1 class="dark:text-white text-3xl font-bold mb-2">OST 아카이브</h1>
        <p class="text-gray-500 dark:text-gray-300 mb-8">끄투리오의 다양한 OST를 감상해보세요.</p>
      </div>
      <div>
        <button class="bg-[#55aa55] hover:bg-[#51a351] text-white flex rounded-full py-2 px-2 transform ease-in duration-100 active:scale-95"
        onclick={() => {
          if (currentSong) {
            currentSong.pause();
            currentSong = null;
          } else {
            playSong(songs[Math.floor(Math.random() * songs.length)]);
          }
        }}
        >
        <span class="material-symbols-outlined icons-header">{ currentSong ? "pause" : "play_arrow"}</span>
        </button>
      </div>
    </div>
    <ul class="grid grid-cols-1 lg:grid-cols-3 gap-4">
      {#each songs as song}
        <button class="text-left flex items-center justify-between p-4 hover:bg-gray-100 dark:hover:bg-gray-800 rounded-lg transform ease-in duration-100 active:scale-95"
            onclick={() => playSong(song)}>
          <div class="flex">
            <div>
              <img src={ song.cover == "null" ? "https://cdn.kkutu.io/img/bgm/default.png" : "https://cdn.kkutu.io/img/bgm/"+song.cover+".png" } alt="OST" class="w-12 h-12 rounded-lg bg-gray-500 mr-4" />
            </div>
            <div>
              <h3 class="text-lg font-bold dark:text-white">{song.title}</h3>
              <span class="text-sm text-gray-500 dark:text-gray-300">
                {#if song.url.endsWith('.flac')}
                  <span class="bg-[#E0582B] text-white px-1 rounded-full">FLAC</span>
                {/if}
                {#if song.isNew == "true"}
                  <span class="bg-[#55aa55] text-white px-1 rounded-full">신규</span>
                {/if}
                {song.artist}
              </span>
            </div>
          </div>
        </button>
      {/each}
    </ul>
  </div>
</div>