<script>
    import { onMount } from 'svelte';
    import Glide from '@glidejs/glide';
    const title = '글자로 놀자! 끄투 온라인';

    var slideData = [
                {
                    "id": 0,
                    "link": "/",
                    "color": "#000",
                    "slides": [
                        {
                            "desktop": "/slide/d.png",
                            "mobile": "/slide/m.png"
                        }
                    ]
                }
            ];
    
    var patchNoteData = [
        {
            "title": "2021년 10월 20일 업데이트",
            "link": "/patch/20211020",
            "type": "game"
        }
    ]

    var rankData = {
        "data": {
            "page": 0,
            "data": [
            ]
        }
    };

    var blockData = {
    };
    
    var patchData = "<p>2021년 10월 20일 업데이트 내용입니다.</p>";

    const serverName = ["감자", "냉이", "다래", "레몬", "망고", "보리", "상추", "아욱", "20세 이상"];
    let jsonDataServers = { list: [], max: 9 };
    let glide;
    let filteredData = [];

    // Cafe Parsing
    let cafeNoticeData = [];
    let cafeUpdateData = [];
    let cafeMaintenanceData = [];
    let cafeEventData = [];

    let finalData = [];

    filteredData = rankData.data.data.slice(0, 10);

    function updateSlides() {
        const slideContainer = document.querySelector('.glide__slides');
        const glideBullets = document.querySelector('.glide__bullets');
        slideContainer.innerHTML = ''; // 기존 슬라이드 초기화
        glideBullets.innerHTML = ''; // 기존 버튼 초기화

        slideData.forEach((slide) => {
            const slideElement = document.createElement('li');
            slideElement.className = 'glide__slide pt-14 flex justify-center items-center';
            slideElement.style.backgroundColor = slide.color;

            const linkElement = document.createElement('a');
            linkElement.href = slide.link;

            const desktopImage = document.createElement('img');
            desktopImage.src = slide.slides[0].desktop;
            desktopImage.className = 'hidden h-72 lg:block object-cover';
            desktopImage.alt = 'Desktop UI';

            const mobileImage = document.createElement('img');
            mobileImage.src = slide.slides[0].mobile;
            mobileImage.className = 'h-54 lg:hidden object-cover';
            mobileImage.alt = 'Mobile UI';

            linkElement.appendChild(desktopImage);
            linkElement.appendChild(mobileImage);
            slideElement.appendChild(linkElement);
            slideContainer.appendChild(slideElement);

            const bulletElement = document.createElement('button');
            bulletElement.className = 'glide__bullet';
            bulletElement.setAttribute('data-glide-dir', `=${slide.id}`);
            glideBullets.appendChild(bulletElement);
        });

        if (glide) {
            glide.destroy();
        }

        glide = new Glide('.glide', {
            type: 'carousel',
            startAt: 0,
            gap: 0,
            perView: 1,
            autoplay: 5000,
            hoverpause: true,
            animationDuration: 800,
            animationTimingFunc: 'ease-in-out'
        });

        glide.mount();
    }

    onMount(async () => {
        // Fetch slide data
        try{
        const slideResponse = await fetch('https://static.kkutu.io/slides.json');
        slideData = await slideResponse.json();
        updateSlides();

        const rankResponse = await fetch('https://kkutu.io/ranking?p=0');
        rankData = await rankResponse.json();
        filteredData = rankData.data.data.slice(0, 10);

        const blockResponse = await fetch('https://kkutu.io/api/block');
        blockData = await blockResponse.json();
        }
        catch(e){
            console.error(e);
        }
    
        // Fetch server list
        const responseServers = await fetch('https://kkutu.io/servers');
        
        if (!responseServers.ok) {
          throw new Error('Failed to fetch data');
        }

        jsonDataServers = await responseServers.json();
        
        const cafeResponse_events = await fetch('https://cafeproxy.kkutuio.workers.dev?cafeId=30131388&boardId=9&listCnt=1');
        const cafeData_events = await cafeResponse_events.json();
        cafeEventData = cafeData_events.message.result.articleList;

        const cafeResponse_notices = await fetch('https://cafeproxy.kkutuio.workers.dev?cafeId=30131388&boardId=8&listCnt=1');
        const cafeData_notices = await cafeResponse_notices.json();
        cafeNoticeData = cafeData_notices.message.result.articleList;

        const cafeResponse_updates = await fetch('https://cafeproxy.kkutuio.workers.dev?cafeId=30131388&boardId=11&listCnt=1');
        const cafeData_updates = await cafeResponse_updates.json();
        cafeUpdateData = cafeData_updates.message.result.articleList;

        const cafeResponse_maintenances = await fetch('https://cafeproxy.kkutuio.workers.dev?cafeId=30131388&boardId=10&listCnt=1');
        const cafeData_maintenances = await cafeResponse_maintenances.json();
        cafeMaintenanceData = cafeData_maintenances.message.result.articleList;

        finalData = [...cafeNoticeData, ...cafeUpdateData, ...cafeMaintenanceData, ...cafeEventData];

    });

    function reloadList() { 
        fetch('https://kkutu.io/servers')
            .then(response => response.json())
            .then(data => {
                jsonDataServers = data;
            });
    }
    function getInquireId() {
        const date = new Date();
        return `BLK-${blockData.blockType}-${blockData.id}-${date.getMonth() + 1}.${date.getDate()}.${date.getHours()}.${date.getMinutes()}`;
    }

    function hideIP(target){
        if (blockData.blockType !== 'IP') return target;

        const ip = blockData.target;
        const splitIp = ip.split('.');
        return `${splitIp[0]}.${splitIp[1]}.*.*`;
    }

    function tsconv(timestamp){
        const date = new Date(timestamp);
        return `${date.getFullYear()}년 ${date.getMonth() + 1}월 ${date.getDate()}일`;
    }

    const MAX_LEVEL = 720;
    let EXP = [];
    var i = 0;

    function getRequiredScore(lv) {
        if (lv <= 240) return Math.round(
            (!(lv % 5) * 0.3 + 1) * (!(lv % 15) * 0.4 + 1) * (!(lv % 45) * 0.5 + 1) * (
                120 + Math.floor(lv / 5) * 60 + Math.floor(lv * lv / 225) * 120 + Math.floor(lv * lv / 2025) * 180
            )
        ); else if (lv <= 480) return Math.round(
            (!(lv % 5) * 0.3 + 1) * (!(lv % 15) * 0.4 + 1) * (!(lv % 45) * 0.5 + 1) * (
                120 + Math.floor(lv / 5) * 100 + Math.floor(lv * lv / 225) * 170 + Math.floor(lv * lv / 2025) * 240
            )
        ); else return Math.round(
            (!(lv % 5) * 0.3 + 1) * (!(lv % 15) * 0.4 + 1) * (!(lv % 45) * 0.5 + 1) * (
                120 + Math.floor(lv / 5) * 140 + Math.floor(lv * lv / 225) * 220 + Math.floor(lv * lv / 2025) * 300
            )
        );
    }

    EXP.push(getRequiredScore(1));
        for (i = 2; i < MAX_LEVEL; i++) {
            EXP.push(EXP[i - 2] + getRequiredScore(i));
        }
        EXP[MAX_LEVEL - 1] = Infinity;
        EXP.push(Infinity);
    
    function getLevel(score) {
        var i, l = EXP.length;

        for (i = 0; i < l; i++) if (score < EXP[i]) break;
        return i + 1;
    }

    function getLevelImage(score) {
        var lv = getLevel(score) - 1;
        var lX = (lv % 25) * -100;
        var lY = Math.floor(lv * 0.04) * -100;
        return `background-position: ${lX}% ${lY}%;`;
    }
</script>
  
<svelte:head>
    <title>끄투리오 - {title}</title>
</svelte:head>

{#if blockData.blocked}
    <!-- Fullscreen dim -->
    <div class="z-50 fixed inset-0 bg-black bg-opacity-50 flex justify-center items-center">
        <div class="bg-gray-800 text-center text-white rounded-full p-8">
            {#if blockData.onlyGuestPunish && blockData.blockType == "IP"}
                <h2 class="text-2xl font-bold">손님 계정 이용 제한됨</h2>
                <p class="mt-4 text-gray-300">운영정책 위반으로 <strong>손님 상태에서의 게임 이용</strong>이 제한되었습니다.</p>

                <div class="mt-4">
                    <table class="w-full text-left border-separate border-spacing-y-3">
                        <tr>
                            <td class="w-24 font-semibold">IP 주소</td>
                            <td class="w-72 text-gray-300">{hideIP(blockData.target)}</td>
                        </tr>
                        <tr>
                            <td class="w-24 font-semibold">해제 일시</td>
                            <td class="w-72 text-gray-300">{blockData.pardonTime}</td>
                        </tr>
                        <tr>
                            <td class="font-semibold">제한 기간</td>
                            <td class="text-gray-300">{blockData.duration}</td>
                        </tr>
                        <tr>
                            <td class="font-semibold">제한 사유</td>
                            <td class="text-gray-300">{blockData.reason}</td>
                        </tr>
                        <tr>
                            <td class="font-semibold">남은 시간</td>
                            <td class="text-gray-300">{blockData.remain}</td>
                        </tr>
                    </table>

                    <p class="mt-4 text-gray-300">문의가 있으실 경우 고객센터로 문의해주시기 바랍니다.<br>이용제한 기간 중 다른 계정을 이용하여 게임을 플레이할 경우,<br><strong>이용제한 기간이 연장</strong>될 수 있습니다.</p>
                    <a href="https://support.kkutu.io/plugin/support_manager/knowledgebase/view/1" target="_blank">
                        <button class="mt-4 mb-2 font-bold rounded-full bg-green-600 hover:bg-green-700 px-3 py-2">고객센터 문의하기</button>
                    </a>
                    <br>
                    <a href="https://kkutu.io/login" rel="external">
                        <button class="text-gray-300 mb-4">로그인 (게임 이용 가능)</button>
                    </a>
                    <p class="text-xs text-gray-400">
                        {getInquireId()} 문구와 함께 자세한 문의 내용을 작성해주시기 바랍니다.
                    </p>
                </div>
            {:else}
                <h2 class="text-2xl font-bold">계정 이용 제한됨</h2>
                <p class="mt-4 text-gray-300">운영정책 위반으로 <strong>게임 이용</strong>이 제한되었습니다.</p>

                <div class="mt-4">
                    <table class="w-full text-left border-separate border-spacing-y-3">
                        <tr>
                            <td class="w-24 font-semibold">식별 번호</td>
                            <td class="w-72 text-gray-300">{blockData.target}</td>
                        </tr>
                        <tr>
                            <td class="w-24 font-semibold">해제 일시</td>
                            <td class="w-72 text-gray-300">{blockData.pardonTime}</td>
                        </tr>
                        <tr>
                            <td class="font-semibold">제한 기간</td>
                            <td class="text-gray-300">{blockData.duration}</td>
                        </tr>
                        <tr>
                            <td class="font-semibold">제한 사유</td>
                            <td class="text-gray-300">{blockData.reason}</td>
                        </tr>
                        <tr>
                            <td class="font-semibold">남은 시간</td>
                            <td class="text-gray-300">{blockData.remain}</td>
                        </tr>
                    </table>

                    <p class="mt-4 text-gray-300">문의가 있으실 경우 고객센터로 문의해주시기 바랍니다.<br>이용제한 기간 중 다른 계정을 이용하여 게임을 플레이할 경우,<br><strong>이용제한 기간이 연장</strong>될 수 있습니다.</p>
                    <a href="https://support.kkutu.io/plugin/support_manager/knowledgebase/view/1" target="_blank">
                        <button class="mt-4 mb-2 font-bold rounded-full bg-green-600 hover:bg-green-700 px-3 py-2">고객센터 문의하기</button>
                    </a>
                    <br>
                    <a href="https://kkutu.io/logout" rel="external">
                        <button class="text-gray-300 mb-4">로그아웃</button>
                    </a>
                    <p class="text-xs text-gray-400">
                        {getInquireId()} 문구와 함께 자세한 문의 내용을 작성해주시기 바랍니다.
                    </p>
                </div>
            {/if}
        </div>
    </div>
{/if}
<div class="dark:bg-gray-900">
    <div class="glide">
        <!-- Slide Left/right btn -->
         <div class="glide__arrows" data-glide-el="controls">
            <button class="glide__arrow glide__arrow--left rounded-full hidden lg:block bg-white h-9 w-9 text-black" data-glide-dir="<">
                <i class="fa-solid fa-chevron-left"></i>
            </button>
            <button class="glide__arrow glide__arrow--right rounded-full hidden lg:block bg-white h-9 w-9 text-black" data-glide-dir=">">
                <i class="fa-solid fa-chevron-right"></i>
            </button>
        </div>

        <div class="glide__track" data-glide-el="track">
            <ul class="glide__slides">
            </ul>
        </div>
        <div class="glide__bullets" data-glide-el="controls[nav]">
        </div>
    </div>
    <div class="max-w-screen-xl mx-auto lg:py-12 p-4 lg:px-8 gap-y-12 flex flex-col">
        <!-- Notice area 
        <div class="dark:border-green-700 dark:text-green-300 dark:bg-green-950 text-green-600 bg-green-100 border-green-200 border p-4 lg:px-8 rounded-full">
            <i class="fa-solid fa-bell lg:mr-3"></i>
            <strong>공지사항</strong>
            <span class="block lg:inline-block lg:pl-4 lg:ml-4 lg:border-l dark:border-gray-700 border-gray-300">
                
            </span>
        </div>--->
        
        <!-- Patch note area -->
        <div class="dark:text-white rounded-full p-2 flex flex-col">
            <div class="mb-6 justify-between flex items-center">
                <h2 class="font-semibold text-2xl">새로운 소식</h2>
                <a href="https://cafe.naver.com/ArticleList.nhn?search.clubid=30131388&search.menuid=8&search.boardtype=L" target="_blank">
                    <button 
                    class="flex items-center justify-center text-gray-400 dark:text-gray-300 hover:text-gray-500 dark:hover:text-gray-400 hover:bg-gray-200 dark:hover:bg-gray-700 py-1 px-1 rounded-full transform ease-in duration-100 active:scale-95">
                    <span class="material-symbols-outlined">
                       add
                    </span>
                </button>
            </a>
            </div>
            <div class="min-h-48 grid grid-cols-1 lg:grid-cols-4 lg:gap-4">
                {#each finalData as cafeNotice, index}
                <!-- Card -->
                <a href={`https://cafe.naver.com/kkutuio/${cafeNotice.refArticleId}`} class="dark:text-white lg:border flex flex-col" target="_blank">
                    <img src={`https://cdn.kkutu.io/img/front/${cafeNotice.menuId}.png`} class="hidden lg:block h-32 w-full object-cover" alt="Patch note"/>
                    <h3 class="lg:px-3 pb-2 lg:pb-0 pt-2 truncate">{cafeNotice.subject}</h3>
                    <p class="hidden lg:block text-gray-400 text-sm px-3 pb-2">{tsconv(cafeNotice.writeDateTimestamp)}</p>
                </a>
                {/each}
            </div>
        </div>
        <!-- Gridded area -->
        <div class="grid grid-cols-1 lg:grid-cols-2 gap-4">
            <div class="dark:text-white rounded-full p-2 flex flex-col">
                <div class="flex justify-between mb-6 items-center">
                    <h2 class="font-semibold text-2xl">채널 목록</h2>
                    <button 
                    on:click={() => reloadList()}
                    class="flex items-center justify-center text-gray-400 dark:text-gray-300 hover:text-gray-500 dark:hover:text-gray-400 hover:bg-gray-200 dark:hover:bg-gray-700 py-1 px-1 rounded-full transform ease-in duration-100 active:scale-95">
                    <span class="material-symbols-outlined">
                        refresh
                    </span>
                </button>
                </div>
                {#each jsonDataServers.list as serverUsers, index}
                <a rel="external" href={`${serverUsers === null ? "/" : "https://kkutu.io/?server="+index}`}>
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
            <div class="dark:text-white rounded-full p-2 flex flex-col ">
                <h2 class="mb-6 font-semibold text-2xl">랭킹</h2>
                {#each filteredData as rank, index}
                    <div class="rounded-full text-gray-900 mb-4">
                        <div class="flex justify-between">
                            <div class="text-xl dark:text-green-300 text-[#55aa55] flex gap-x-2 justify-center items-center">
                                <span class="w-12 text-center">{rank.rank + 1}위</span>
                                <div class="level mr-2" style={getLevelImage(Number(rank.score))}></div><span class="font-bold">{rank.name}</span></div>
                            <span class="font-normal text-right dark:text-gray-300 text-gray-500">{Number(rank.score).toLocaleString()}점</span>
                        </div>
                    </div>
                {/each}
            </div>
        </div>

        <div class="hidden lg:block bg-gray-50 text-gray-500 dark:text-gray-300 dark:bg-gray-950 rounded-full py-1">
            <div class="grid grid-cols-5">
              <a class="flex items-center justify-center gap-x-2 h-16" href="https://cafe.naver.com/kkutuio/273">
                <span class="material-symbols-outlined icons-header">group</span>
                <p class="text-lg">운영진 모집</p>
              </a>
              <a class="flex items-center justify-center gap-x-2 h-16" href="https://cafe.naver.com/ArticleList.nhn?search.clubid=30131388&search.menuid=22&search.boardtype=L">
                <span class="material-symbols-outlined icons-header">playlist_add</span>
                <p class="text-lg">단어 신청</p>
              </a>
              <a class="flex items-center justify-center gap-x-2 h-16" rel="external" href="/ost.html">
                <span class="material-symbols-outlined icons-header">music_note</span>
                <p class="text-lg">OST 아카이브</p>
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