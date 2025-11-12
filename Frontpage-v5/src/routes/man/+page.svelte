<script>
  import { onMount } from 'svelte';
    import Glide from '@glidejs/glide';
    import { getLevelImage } from '../../lib/getLevelImg.js';
    import { getMoremi } from '../../lib/getMoremi.js';


    const title = '글자로 놀자! 끄투 온라인';

    var blockData = {
    };
    
    var patchData = "";

    const serverName = ["감자", "냉이", "다래", "레몬", "망고", "보리", "상추", "아욱", "20세 이상"];
    let jsonDataServers = $state({ list: [], max: 9 });

    let finalData = [];


    onMount(async () => {

        // Fetch server list
        const responseServers = await fetch('https://kkutu.io/servers');
        
        if (!responseServers.ok) {
          throw new Error('Failed to fetch data');
        }

        jsonDataServers = await responseServers.json();
        

    });

    function reloadList() { 
        fetch('https://kkutu.io/servers')
            .then(response => response.json())
            .then(data => {
                jsonDataServers = data;
            });
    }
</script>
  
<svelte:head>
    <title>끄투리오 - {title}</title>
</svelte:head>

<div class="dark:bg-gray-900">
    <div class="max-w-screen-xl mx-auto lg:py-12 p-4 lg:px-8 gap-y-8 lg:gap-y-12 flex flex-col">
        <!-- Patch note area -->
        <div class="dark:text-white rounded-full p-2 flex flex-col mt-12">
            
            <h1 class="text-3xl font-bold py-4">
                긴급 조치 중
            </h1>
            <p>
                현재 원활한 서비스 이용 환경을 제공해 드리기 위해 <b>긴급 조치</b>가 진행되고 있습니다.
                긴급 조치 중에는 일부 서비스 이용이 제한될 수 있습니다.
            </p>
            
        </div>
        
        <!-- Notice area -->
        <div class="dark:border-green-700 dark:text-green-300 dark:bg-green-950 text-green-600 bg-green-100 border-green-200 border p-4 lg:px-8">
            <strong>공지사항</strong>
            <span class="block lg:inline-block lg:pl-4 lg:ml-4 lg:border-l dark:border-gray-700 border-gray-300">
                긴급 조치 기간 중에는 손님 계정의 서비스 이용이 제한될 수 있습니다.
                서비스 이용 시 참고해 주시길 바랍니다.
            </span>
        </div>
        
        <!-- Gridded area -->
        <div class="grid grid-cols-1 lg:grid-cols-2 gap-4">
            <div class="dark:text-white rounded-full p-2 flex flex-col" id="serverList">
                <div class="flex justify-between mb-6 items-center">
                    <h2 class="font-semibold text-2xl items-center flex justify-center">
                        <span class="material-symbols-outlined mr-2">
                            list_alt
                        </span>
                        채널 목록</h2>
                    <button 
                    onclick={() => reloadList()}
                    class="flex items-center justify-center text-gray-400 dark:text-gray-300 hover:text-gray-500 dark:hover:text-gray-400 hover:bg-gray-200 dark:hover:bg-gray-700 py-1 px-1 rounded-full transform ease-in duration-100 active:scale-95">
                    <span class="material-symbols-outlined">
                        refresh
                    </span>
                </button>
                </div>
                {#each jsonDataServers.list as serverUsers, index}
                <a rel="external" href={`${serverUsers === null ? "/" : "https://kkutu.io/game/server/"+index}`}>
                    <div class="rounded-full text-gray-900 mb-8">
                        <div class="flex justify-between">
                            <h3 class="text-xl font-bold dark:text-green-300 text-[#55aa55]">{serverName[index]} 채널</h3>
                            <span class="font-normal text-right dark:text-gray-300 text-gray-500">{serverUsers === null ? '점검 중' : `${serverUsers} / ${jsonDataServers.max}`}</span>
                        </div>
                        <div class="dark:bg-gray-800 bg-gray-100 h-2 mt-3">
                            <div class={`${serverUsers === null ? "bg-transparent" : "dark:bg-green-300 bg-[#55aa55]"} h-full`} style={`width: ${(serverUsers / jsonDataServers.max) * 100}%`}>
                        </div>
                        </div>
                    </div>
                </a>
                {/each}
            </div>
        </div>

        <div class="hidden lg:block bg-gray-50 text-gray-500 dark:text-gray-300 dark:bg-gray-950 rounded-xl py-1">
            <div class="grid grid-cols-4">
              <a class="flex items-center justify-center gap-x-2 h-16" href="https://cafe.naver.com/kkutuio/273">
                <span class="material-symbols-outlined icons-header">group</span>
                <p class="text-lg">운영진 모집</p>
              </a>
              <a class="flex items-center justify-center gap-x-2 h-16" href="https://cafe.naver.com/ArticleList.nhn?search.clubid=30131388&search.menuid=22&search.boardtype=L">
                <span class="material-symbols-outlined icons-header">playlist_add</span>
                <p class="text-lg">단어 신청</p>
              </a>
              <a class="flex items-center justify-center gap-x-2 h-16" rel="external" href="https://support.kkutu.io/order/main/packages/membership/?group_id=2" target="_blank">
                <span class="material-symbols-outlined icons-header">store</span>
                <p class="text-lg">멤버십</p>
              </a>
              <a class="flex items-center justify-center gap-x-2 h-16" rel="external" href="https://support.kkutu.io/" target="_blank">
                <span class="material-symbols-outlined icons-header">help</span>
                <p class="text-lg">고객지원</p>
              </a>
            </div>
          </div>
    </div>
</div>
