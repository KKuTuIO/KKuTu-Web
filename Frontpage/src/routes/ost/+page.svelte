<script>
  import { onMount } from 'svelte';
  const title = 'OST 아카이브';
  
  const songs = [
    { title: "끄투리오 2024 가을", url: "https://cdn.kkutu.io/media/LobbyBGM_2024_Fall.mp3", artist: "BGM Farm", cover: "fall2024" },
    { title: "끄투리오 신년맞이", url: "https://cdn.kkutu.io/media/LobbyBGM_2023_Winter.mp3", artist: "Hexacube", cover: "null" },
    { title: "끄투리오 2022 가을", url: "https://pub-e336c74b058c476bb58f9267c51fbab4.r2.dev/2022Fall.flac", artist: "Hexacube", cover: "null" },
    { title: "끄투리오 6주년", url: "https://pub-e336c74b058c476bb58f9267c51fbab4.r2.dev/20236th.flac", artist: "Hexacube", cover: "null" },
    { title: "끄투리오 2017", url: "https://cdn.kkutu.io/media/LobbyBGM_2017.mp3", artist: "KKuTuIO", cover: "null" },
    { title: "끄투리오 2023 여름", url: "https://pub-e336c74b058c476bb58f9267c51fbab4.r2.dev/2023Summer.flac", artist: "Hexacube", cover: "null" },
    { title: "끄투리오 2024 봄", url: "https://cdn.kkutu.io/media/LobbyBGM_2024_Spring.mp3", artist: "Hexacube", cover: "null" },
    { title: "새로운 시작", url: "https://cdn.kkutu.io/media/LobbyBGM_NEW.mp3", artist: "Hexacube", cover: "null" },
    { title: "끄투리오 2020 가을", url: "https://cdn.kkutu.io/media/LobbyBGM_2020_Fall.mp3", artist: "Hexacube", cover: "null" },
    { title: "끄투리오 Lo-fi", url: "https://cdn.kkutu.io/media/LobbyBGM_Plus_S2.mp3", artist: "Roman Rajczyk", cover: "plusS2" },
    { title: "다함께 끄투리오!", url: "https://cdn.kkutu.io/media/LobbyBGM_Plus_S1.mp3", artist: "Xpolcore", cover: "plusS1" },
    { title: "오리지널 끄투", url: "https://cdn.kkutu.io/media/LobbyBGM.mp3", artist: "JJoriping", cover: "kkutu" },
    { title: "끄투 명절", url: "https://cdn.kkutu.io/media/LobbySeolBGM.mp3", artist: "JJoriping", cover: "kkutu" },
    { title: "끄투리오 명절", url: "https://cdn.kkutu.io/media/LobbyBGM_KKuTuIO_Seol.mp3", artist: "KKuTuIO", cover: "null" },
    { title: "끄투리오 2020 여름", url: "https://cdn.kkutu.io/media/LobbyBGM_2020_Summer.mp3", artist: "Hexacube", cover: "null" },
    { title: "Breakdown Fragment", url: "https://pub-e336c74b058c476bb58f9267c51fbab4.r2.dev/Breakdown_Fragment.mp3", artist: "Hexacube", cover: "null" },
    { title: "끄투리오 2020 겨울", url: "https://cdn.kkutu.io/media/LobbyBGM_2020_Winter.mp3", artist: "Hexacube", cover: "null" }
  ];

  let currentSong = null;
  
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
  <div class="pt-24 mx-auto max-w-screen-xl px-4 py-8 ">
    <div class="flex items-center justify-between mb-8">
      <div>
        <h1 class="dark:text-white text-3xl font-bold mb-2">OST 아카이브</h1>
        <p class="text-gray-500 dark:text-gray-300 mb-8"><strong>여러분의 손으로 꼽은 최고의 OST!</strong> 설문조사의 인기도를 반영했습니다.<br>끄투리오의 다양한 OST를 감상해보세요.</p>
      </div>
      <div>
        <button class="bg-[#55aa55] hover:bg-[#51a351] text-white flex rounded-lg py-1 px-3 transform ease-in duration-100 active:scale-95"
        on:click={() => {
          if (currentSong) {
            currentSong.pause();
            currentSong = null;
          } else {
            playSong(songs[Math.floor(Math.random() * songs.length)]);
          }
        }}
        >
            { currentSong ? "❚❚ 일시 정지" : "🔀 랜덤 재생" }
        </button>
      </div>
    </div>
    <ul class="grid grid-cols-1 lg:grid-cols-3 gap-4">
      {#each songs as song}
        <button class="text-left flex items-center justify-between p-4 hover:bg-gray-100 dark:hover:bg-gray-800 rounded-xl transform ease-in duration-100 active:scale-95"
            on:click={() => playSong(song)}>
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
                {song.artist}
              </span>
            </div>
          </div>
        </button>
      {/each}
    </ul>
  </div>
</div>