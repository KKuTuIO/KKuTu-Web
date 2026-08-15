<script>
    import { onMount, tick } from 'svelte';
    import Glide from '@glidejs/glide';
    import { getLevelImage } from '../lib/getLevelImg.js';
    import { getMoremi } from '../lib/getMoremi.js';
	import { showDialog } from './dialogStore';

    let yymmdd = new Date().toISOString().slice(0, 10).replace(/-/g, "");

	let user = $state("Guest User");
    let authVendor = $state("KKuTu");
    let vendorId = $state("0");
    let name = $state("Moremi");
    let profileImage = $state("");

	let ingameName = $state("");
    let score = $state(0);
    let server = $state(0);
    let data = $state("");

    const title = '글자로 놀자! 끄투 온라인';

    let slideData = $state([
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
    ]);
    
    let patchNoteData = $state([
        {
            "title": "2021년 10월 20일 업데이트",
            "link": "/patch/20211020",
            "type": "game"
        }
    ]);

    let maintenanceData = $state({
        "date": "250301",
        "type": "정기 점검",
        "reason": "서비스 안정화",
        "article": "2249"
    });

    let rankData = $state({
        "data": {
            "page": 0,
            "data": []
        }
    });

    let blockData = $state({});
    
    const patchData = "<p>2021년 10월 20일 업데이트 내용입니다.</p>";

    const serverName = ["감자", "냉이", "다래", "레몬", "망고", "보리", "상추", "아욱", "20세 이상"];
    let jsonDataServers = $state({ list: [], max: 9 });
    let glide;
    let gl;
    let filteredData = $derived(rankData?.data?.data ? rankData.data.data.slice(0, 10) : []);
    let slidePage = $state(0);

    let finalData = $state([
        {
            "articleId": "",
            "menuId": "",
            "subject": "",
            "content": ""
        },
        {
            "articleId": "",
            "menuId": "",
            "subject": "",
            "content": ""
        },
        {
            "articleId": "",
            "menuId": "",
            "subject": "",
            "content": ""
        },
        {
            "articleId": "",
            "menuId": "",
            "subject": "",
            "content": ""
        },
        {
            "bannerName": "",
            "articleId": "",
            "subject": "",
            "content": ""
        },
        {
            "thumbnailUri": "",
            "articleId": "",
            "subject": "",
            "content": ""
        },
        {
            "thumbnailUri": "",
            "articleId": "",
            "subject": "",
            "content": ""
        },
        {
            "thumbnailUri": "",
            "articleId": "",
            "subject": "",
            "content": ""
        },
        {
            "thumbnailUri": "",
            "articleId": "",
            "subject": "",
            "content": ""
        }
    ]);

    async function initGlide() {
        await tick();
        if (glide) glide.destroy();

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

        glide.on('run', () => {
            slidePage = glide.index;
        });

        gl = glide.mount();
    }

    function goLeft() {
        glide.go('<');
    }

    function goRight() {
        glide.go('>');
    }
    
    onMount(async () => {
        try {
            const res = await fetch('/user/oauth');
            const jsonData = await res.json();
            data = jsonData;
        } catch (e) {
            data = { status: "Guest user" };
        }

        if (localStorage.getItem('server')) {
            server = localStorage.getItem('server');
        } else if (data.status !== "Guest user") {
            server = 2;
        } else {
            server = 0;
        }

        if (data.status !== "Guest user") {
            authVendor = data.authVendor;
            vendorId = data.vendorId;
            name = data.name;
            profileImage = data.image;
            user = name;

            if (authVendor === "DISCORD") {
                profileImage = profileImage.replace("/avatars/0/", `/avatars/${vendorId}/`);
                profileImage = profileImage + ".webp";
            }

            console.log(`User ${name} is logged in with ${authVendor}`);

            const userRes = await fetch(`/user/${authVendor.toLowerCase()}-${vendorId}`);
            const userData = await userRes.json();

            ingameName = userData.profile.title;
            score = userData.data.score;
        } else console.log("User is not logged in");

        try {
            const cafeResponse_events = await fetch('https://static.kkutu.io/cafe.json');
            finalData = await cafeResponse_events.json();

            const slideResponse = await fetch('https://static.kkutu.io/slides.json');
            slideData = await slideResponse.json();
            initGlide();

            const maintenanceResponse = await fetch('https://static.kkutu.io/maintenance.json');
            maintenanceData = await maintenanceResponse.json();

            const rankResponse = await fetch('/ranking?p=0');
            rankData = await rankResponse.json();

            const blockResponse = await fetch('/api/block');
            blockData = await blockResponse.json();
        } catch(e) {
            console.error(e);
        }

        const responseServers = await fetch('/servers');
        if (!responseServers.ok) {
          throw new Error('Failed to fetch data');
        }
        jsonDataServers = await responseServers.json();
    });

    function reloadList() { 
        fetch('/servers')
            .then(response => response.json())
            .then(data => {
                jsonDataServers = data;
            });
    }

    function hideIP(target) {
        if (blockData.blockType !== 'IP') return target;

        const ip = blockData.target;
        const isIpv6 = ip.includes(':');
        const splitIp = isIpv6 ? ip.split(':') : ip.split('.');
        return isIpv6 ? `${splitIp[0]}:${splitIp[1]}:*:*:*:*` : `${splitIp[0]}.${splitIp[1]}.*.*`;
    }

    function tsconv(timestamp) {
        const date = new Date(timestamp);
        return `${date.getFullYear()}년 ${date.getMonth() + 1}월 ${date.getDate()}일`;
    }

    function handleImgErr(e, category, filename) {
        const img = e.target;
        const baseURL = "https://cdn.kkutu.io/img/kkutu/moremi";
        if (img.src.includes('/event/')) {
            img.src = `${baseURL}/${category}/default.png`;
            img.onerror = null; 
        } else {
            if (!filename || filename === "default.png") img.src = `${baseURL}/${category}/default.png`;
            else img.src = `${baseURL}/event/${filename}`;
        }
    }
</script>
  
<svelte:head>
    <title>끄투리오 - {title}</title>
</svelte:head>

{#if blockData.blocked}
    <!-- Fullscreen dim -->
    <div class="z-50 fixed inset-0 bg-black/50 flex justify-center items-center">
        <div class="bg-gray-800 text-center text-white rounded-xl p-8">
            {#if blockData.onlyGuestPunish && blockData.blockType == "IP"}
                <h2 class="text-2xl font-bold">손님 계정 이용 제한됨</h2>
                <p class="mt-4 text-gray-300">운영정책 위반으로 <strong>손님 상태에서의 게임 이용</strong>이 제한되었습니다.</p>

                <div class="mt-4">
                    <table class="w-full text-left border-separate border-spacing-y-3">
                        <tbody>
                            <tr>
                                <th scope="row" class="w-24 font-semibold">IP 주소</th>
                                <td class="w-72 text-gray-300">{hideIP(blockData.target)}</td>
                            </tr>
                            <tr>
                                <th scope="row" class="w-24 font-semibold">해제 일시</th>
                                <td class="w-72 text-gray-300">{blockData.pardonTime}</td>
                            </tr>
                            <tr>
                                <th scope="row" class="font-semibold">제한 기간</th>
                                <td class="text-gray-300">{blockData.duration}</td>
                            </tr>
                            <tr>
                                <th scope="row" class="font-semibold">제한 사유</th>
                                <td class="text-gray-300">{blockData.reason}</td>
                            </tr>
                            <tr>
                                <th scope="row" class="font-semibold">남은 시간</th>
                                <td class="text-gray-300">{blockData.remain}</td>
                            </tr>
                        </tbody>
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
                        {blockData.inquiryId} 문구와 함께 자세한 문의 내용을 작성해주시기 바랍니다.
                    </p>
                </div>
            {:else}
                <h2 class="text-2xl font-bold">계정 이용 제한됨</h2>
                <p class="mt-4 text-gray-300">운영정책 위반으로 <strong>게임 이용</strong>이 제한되었습니다.</p>

                <div class="mt-4"><table class="w-full text-left border-separate border-spacing-y-3">
                    <tbody>
                        <tr>
                            <th scope="row" class="w-24 font-semibold">식별 번호</th>
                            <td class="w-72 text-gray-300">{blockData.target}</td>
                        </tr>
                        <tr>
                            <th scope="row" class="w-24 font-semibold">해제 일시</th>
                            <td class="w-72 text-gray-300">{blockData.pardonTime}</td>
                        </tr>
                        <tr>
                            <th scope="row" class="font-semibold">제한 기간</th>
                            <td class="text-gray-300">{blockData.duration}</td>
                        </tr>
                        <tr>
                            <th scope="row" class="font-semibold">제한 사유</th>
                            <td class="text-gray-300">{blockData.reason}</td>
                        </tr>
                        <tr>
                            <th scope="row" class="font-semibold">남은 시간</th>
                            <td class="text-gray-300">{blockData.remain}</td>
                        </tr>
                    </tbody>
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
                        {blockData.inquiryId} 문구와 함께 자세한 문의 내용을 작성해주시기 바랍니다.
                    </p>
                </div>
            {/if}
        </div>
    </div>
{/if}
<div class="dark:bg-gray-900">
    <div class="glide">
        <!-- Slide Left/right btn -->
         <div class="glide__arrows hidden" data-glide-el="controls">
            <button id="slideLeft" class="glide__arrow glide__arrow--left  hidden lg:block bg-white h-9 w-9 text-black" data-glide-dir="<">
                <i class="fa-solid fa-chevron-left"></i>
            </button>
            <button id="slideRight" class="glide__arrow glide__arrow--right  hidden lg:block bg-white h-9 w-9 text-black" data-glide-dir=">">
                <i class="fa-solid fa-chevron-right"></i>
            </button>
        </div>

        <div class="glide__track" data-glide-el="track">
            <ul class="glide__slides lg:min-h-[456px] min-h-[280px]">
            </ul>
        </div>
        <div class="hidden glide__bullets opacity-0 " data-glide-el="controls[nav]">
        </div>
        
        <div class="-mt-[400px] h-[400px] hidden lg:flex items-center min-w-screen-lg max-w-screen-xl mx-auto justify-end pr-4 z-50">
            <div class="w-[260px] mx-4">
                <a href={`https://kkutu.io/game/server/${server}`} rel="external" class="shadow-lg w-full rounded-t-xl membershipBGScroll text-4xl border-[#51a351] border-b bg-[#55aa55] hover:bg-[#51a351] font-bold text-white flex flex-col py-6 px-12 transform ease-in duration-100 items-center justify-center">
                    게임 시작<br>
                    <span class="text-2xl mt-1">
                        ({serverName[server]} 채널)
                    </span>
                </a>
                <button
                on:click={() => showDialog.set(true)}
                class="text-gray-900 flex items-center justify-center shadow-lg w-full rounded-b-xl p-3.5 hover:bg-gray-100 bg-white backdrop-filter backdrop-blur-lg transform ease-in duration-100">
                    <h2 class="text-xl font-semibold">다른 채널 선택</h2>
                    <span class="material-symbols-outlined text-2xl">
                        chevron_right
                    </span>
                </button>
            </div>
        </div>
        <div class="w-full -mt-12 lg:-mt-14 absolute">

            <div class="max-w-screen-xl mx-auto flex justify-between items-center px-4 py-2 lg:px-8">
                <!-- slide controls -->
                <div class="flex gap-x-2">
                    <!-- use material icons -->
                    <button class="text-black rounded-full bg-white/70 backdrop-filter-blur-lg p-1 flex justify-center items-center" on:click={goLeft}>
                        <span class="material-symbols-outlined">
                            chevron_left
                        </span>
                    </button>
                    <button class="text-black rounded-full bg-white/70 backdrop-filter-blur-lg p-1 flex justify-center items-center" on:click={goRight}>
                        <span class="material-symbols-outlined">
                            chevron_right
                        </span>
                    </button>
                </div>
                <div class="text-black rounded-full bg-white/70 backdrop-filter-blur-lg p-1 px-4 flex items-center">
                    <span class="font-bold">{slidePage + 1}</span>&nbsp;/ {slideData.length}
                </div>
            </div>
    </div>
    </div>
    <!-- PC전용 : 로그인 / 게임시작 영역 -->
    <div class="hidden lg:block bg-gray-800 border-t border-b border-gray-700">
        <div class="max-w-screen-xl mx-auto grid grid-cols-4 py-4 px-8 min-h-[170px]">
            <!-- Login -->
             <div class="col-span-2 gap-x-4 w-full">
            {#if user == "Guest User"}
                <!-- Logout status -->
                <div class="flex flex-col items-center justify-center h-full">
                <p class="text-white text-lg">로그인하고 플레이 기록을 저장하세요!</p>
                <a href="/login" rel="external">
                    <button class="bg-[#55aa55] hover:bg-[#51a351] font-bold text-white flex justify-center items-center gap-x-2 text-lg mt-4 w-36 py-2 px-3 transform ease-in duration-100 active:scale-95">
                        <span class="material-symbols-outlined">
                            login
                        </span>
                        로그인
                    </button>
                </a>
                </div>
            {:else}

                <!-- Login status -->
                 <div class="flex justify-center items-center gap-x-4 h-full w-full">
                    <div class="w-[120px] h-[120px]">
                      {#await getMoremi(authVendor.toLowerCase()+"-"+vendorId) then equip}
                        <img src={`https://cdn.kkutu.io/img/kkutu/moremi/back/${equip.Mback || "default.png"}`} class="absolute object-cover w-[120px] h-[120px]" alt="BG" on:error={(e) => handleImgErr(e, 'back', equip.Mback)} />
                        <img src={`https://cdn.kkutu.io/img/kkutu/moremi/body/${equip.Mbody || "default.png"}`} class="absolute object-cover w-[120px] h-[120px]" alt="Moremi Body" />
                        <img src={`https://cdn.kkutu.io/img/kkutu/moremi/eye/${equip.Meye || "default.png"}`} class="absolute object-cover w-[120px] h-[120px]" alt="Moremi Eye" on:error={(e) => handleImgErr(e, 'eye', equip.Meye)} />
                        <img src={`https://cdn.kkutu.io/img/kkutu/moremi/mouth/${equip.Mmouth || "default.png"}`} class="absolute object-cover w-[120px] h-[120px]" alt="Moremi Mouth" on:error={(e) => handleImgErr(e, 'mouth', equip.Mmouth)} />
                        <img src={`https://cdn.kkutu.io/img/kkutu/moremi/clothes/${equip.Mclothes || "default.png"}`} class="absolute object-cover w-[120px] h-[120px]" alt="Moremi Pants" on:error={(e) => handleImgErr(e, 'clothes', equip.Mclothes)} />
                        <img src={`https://cdn.kkutu.io/img/kkutu/moremi/shoes/${equip.Mshoes || "default.png"}`} class="absolute object-cover w-[120px] h-[120px]" alt="Moremi Shoes" on:error={(e) => handleImgErr(e, 'shoes', equip.Mshoes)} />
                        <img src={`https://cdn.kkutu.io/img/kkutu/moremi/head/${equip.Mhead || "default.png"}`} class="absolute object-cover w-[120px] h-[120px]" alt="Moremi Head" on:error={(e) => handleImgErr(e, 'head', equip.Mhead)} />
                        <img src={`https://cdn.kkutu.io/img/kkutu/moremi/hand/${equip.Mrhand || "default.png"}`} class="rightHand absolute object-cover w-[120px] h-[120px]" alt="Moremi Hand" on:error={(e) => handleImgErr(e, 'hand', equip.Mrhand)} />
                        <img src={`https://cdn.kkutu.io/img/kkutu/moremi/hand/${equip.Mlhand || "default.png"}`} class="absolute object-cover w-[120px] h-[120px]" alt="Moremi Hand" on:error={(e) => handleImgErr(e, 'hand', equip.Mlhand)} />
                        <img src={`https://cdn.kkutu.io/img/kkutu/moremi/headDeco/${equip.MheadDeco || "default.png"}`} class="absolute object-cover w-[120px] h-[120px]" alt="Moremi HeadDeco" on:error={(e) => handleImgErr(e, 'headDeco', equip.MheadDeco)} />
                        <img src={`https://cdn.kkutu.io/img/kkutu/moremi/dressDeco/${equip.MdressDeco || "default.png"}`} class="absolute object-cover w-[120px] h-[120px]" alt="Moremi DressDeco" on:error={(e) => handleImgErr(e, 'dressDeco', equip.MdressDeco)} />
                        <img src={`https://cdn.kkutu.io/img/kkutu/moremi/badge/${equip.BDG || "default.png"}`} class="absolute object-cover w-[120px] h-[120px]" alt="Moremi Badge" on:error={(e) => handleImgErr(e, 'badge', equip.BDG)} />
                      {:catch error}
                        <div></div>
                      {/await}
                    </div>

                    <div class="flex flex-col">
                            <div class="flex items-center gap-x-2">
                            <div class="level" style={getLevelImage(Number(score))}></div>
                            <h3 class="text-2xl font-bold text-white truncate w-48">{ingameName}</h3>
                        </div>
                        <p class="text-gray-300">{score.toLocaleString()}점</p>
                        <p class="text-gray-300">{authVendor} 계정</p>
                    </div>

                 </div>
            {/if}
            </div>

        </div>
    </div>

    <div class="max-w-screen-xl mx-auto lg:py-12 p-4 lg:px-8 gap-y-8 flex flex-col">
        <!-- Notice area --->
        {#if Number(maintenanceData.date) == Number(yymmdd) || Number(maintenanceData.date) == Number(yymmdd) + 1}
        <div class="dark:border-green-700 dark:text-green-300 dark:bg-green-950 text-green-600 bg-green-100 border-green-200 border p-4 lg:px-8">
            <strong>{maintenanceData.type} 안내</strong>
            <span class="block lg:inline-block lg:pl-4 lg:ml-4 lg:border-l dark:border-gray-700 border-gray-300">
                {maintenanceData.reason}
                <a href={`https://cafe.naver.com/kkutuio/${maintenanceData.article}`} class="ml-2 underline text-green-600 hover:text-green-700 dark:text-green-300 dark:hover:text-green-400">자세히 보기</a>
            </span>
        </div>
        {/if}
        
        <!-- Patch note area -->
        <div class="dark:text-white rounded-full p-2 flex flex-col">
            <div class="mb-6 justify-between flex items-center">
                <h2 class="font-semibold text-2xl items-center flex justify-center">
                    <span class="material-symbols-outlined mr-2">
                        notifications
                    </span>
                    새로운 소식</h2>
                <a href="https://cafe.naver.com/ArticleList.nhn?search.clubid=30131388&search.menuid=8&search.boardtype=L" target="_blank">
                    <button 
                    class="flex items-center justify-center text-gray-400 dark:text-gray-300 hover:text-gray-500 dark:hover:text-gray-400 hover:bg-gray-200 dark:hover:bg-gray-700 py-1 px-1 rounded-full transform ease-in duration-100 active:scale-95">
                    <span class="material-symbols-outlined">
                       add
                    </span>
                </button>
            </a>
            </div>
            <div class="min-h-36 lg:min-h-48 grid grid-cols-1 lg:grid-cols-4 lg:gap-4">
                {#each finalData.slice(0, 4) as cafeNotice}
                <!-- Card -->
                <a href={`https://cafe.naver.com/kkutuio/${cafeNotice.articleId}`} class="dark:text-gray-200 lg:dark:text-white text-gray-800 lg:text-black lg:border border-gray-200 dark:border-gray-700 flex flex-col" target="_blank">
                    <img src={`https://cdn.kkutu.io/img/front/${cafeNotice.menuId}.png`} class="hidden lg:block h-32 w-full object-cover" alt="Patch note"/>
                    <h3 class="lg:px-3 pb-2 lg:pb-0 pt-2 truncate">{cafeNotice.subject}</h3>
                    <p class="hidden lg:block text-gray-400 text-sm px-3 pb-2">{cafeNotice.content}</p>
                </a>
                {/each}
            </div>
        </div>

        <!-- Gridded area -->
        <div class="grid grid-cols-1 lg:grid-cols-2 gap-4">
            <div class="">
                <div class="dark:text-white rounded-full p-2 flex flex-col">
                    <div class="mb-6 justify-between flex items-center">
                        <h2 class="font-semibold text-2xl items-center flex justify-center">
                            <span class="material-symbols-outlined mr-2">
                                manga
                            </span>
                            팬아트</h2>
                        <a href="https://cafe.naver.com/ArticleList.nhn?search.clubid=30131388&search.menuid=8&search.boardtype=L" target="_blank">
                            <button 
                            class="flex items-center justify-center text-gray-400 dark:text-gray-300 hover:text-gray-500 dark:hover:text-gray-400 hover:bg-gray-200 dark:hover:bg-gray-700 py-1 px-1 rounded-full transform ease-in duration-100 active:scale-95">
                            <span class="material-symbols-outlined">
                            add
                            </span>
                        </button>
                    </a>
                    </div>
                    <div class="min-h-36 lg:min-h-48 grid grid-cols-1 lg:grid-cols-2 lg:gap-4">
                        {#each finalData.slice(5, 7) as cafeNotice}
                        <!-- Card -->
                        <a href={`https://cafe.naver.com/kkutuio/${cafeNotice.articleId}`} class="dark:text-gray-200 lg:dark:text-white text-gray-800 lg:text-black lg:border border-gray-200 dark:border-gray-700 flex flex-col" target="_blank">
                            <img src={`${cafeNotice.thumbnailUri}`} class="hidden lg:block h-32 w-full object-cover" alt="Fanart"/>
                            <h3 class="lg:px-3 pb-2 lg:pb-0 pt-2 truncate">{cafeNotice.subject}</h3>
                            <p class="hidden lg:block text-gray-400 text-sm px-3 pb-2">{cafeNotice.content}</p>
                        </a>
                        {/each}
                    </div>
                </div>
            <div class="dark:text-white rounded-full p-2 flex flex-col lg:mt-6">
                <div class="mb-6 justify-between flex items-center">
                    <h2 class="font-semibold text-2xl items-center flex justify-center">
                        <span class="material-symbols-outlined mr-2">
                            video_library
                        </span>
                        동영상</h2>
                </div>
                <div class="min-h-36 lg:min-h-48 grid grid-cols-1 lg:grid-cols-2 lg:gap-4">
                    {#each finalData.slice(7, 9) as cafeNotice}
                    {#if cafeNotice.articleId}
                        <!-- Card -->
                        <a href={`https://youtube.com/watch?v=${cafeNotice.articleId}`} class="dark:text-gray-200 lg:dark:text-white text-gray-800 lg:text-black lg:border border-gray-200 dark:border-gray-700 flex flex-col" target="_blank">
                            <img src={`${cafeNotice.thumbnailUri}`} class="hidden lg:block h-32 w-full object-cover" alt="Video"/>
                            <h3 class="lg:px-3 pb-2 lg:pb-0 pt-2 truncate">{cafeNotice.subject}</h3>
                            <p class="hidden lg:block text-gray-400 text-sm px-3 pb-2">{cafeNotice.content}</p>
                        </a>
                    {/if}
                    {/each}
                </div>
            </div>
        </div>
            <div class="dark:text-white rounded-full p-2 flex flex-col ">
            <div class="flex justify-between mb-6 items-center">
                <h2 class="font-semibold text-2xl items-center flex justify-center">
                    <span class="material-symbols-outlined mr-2">
                        emoji_events
                    </span>
                    랭킹</h2>
                <a href="/rank" rel="external">
                    <button 
                    class="flex items-center justify-center text-gray-400 dark:text-gray-300 hover:text-gray-500 dark:hover:text-gray-400 hover:bg-gray-200 dark:hover:bg-gray-700 py-1 px-1 rounded-full transform ease-in duration-100 active:scale-95">
                    <span class="material-symbols-outlined">
                       add
                    </span>
                </button>
                </a>
            </div>
                {#each filteredData as rank, index}
                    <div class="rounded-full text-gray-900 mb-4">
                        <div class="flex justify-between">
                            <div class="text-xl dark:text-green-300 text-[#55aa55] flex gap-x-2 justify-center items-center">
                                <span class="w-12">{rank.rank + 1}위</span>
                                <div class="level mr-2" style={getLevelImage(Number(rank.score))}></div><span class="font-bold">
                                    {#if rank.name.includes('#')}
                                        {rank.name.split('#')[0]}<small>#{rank.name.split('#')[1]}</small>
                                    {:else}
                                        {rank.name}
                                    {/if}
                                </span></div>
                            <span class="font-normal text-right dark:text-gray-300 text-gray-500">{Number(rank.score).toLocaleString()}점</span>
                        </div>
                    </div>
                {/each}
            </div>
        </div>

        
        <div class="bg-gray-50 text-gray-500 dark:text-gray-300 dark:bg-gray-950 rounded-xl py-1">
            <div class="grid grid-cols-3 lg:grid-cols-4 gap-y-3 py-2 lg:py-0">
              <a class="flex flex-col lg:flex-row items-center justify-center gap-x-2 h-16" href="https://cafe.naver.com/ArticleList.nhn?search.clubid=30131388&search.menuid=22&search.boardtype=L">
                <span class="material-symbols-outlined icons-header">playlist_add</span>
                <p class="text-sm lg:text-lg">단어 신청</p>
              </a>
              <a class="flex flex-col lg:flex-row items-center justify-center gap-x-2 h-16" rel="external" href="/ost">
                <span class="material-symbols-outlined icons-header">music_note</span>
                <p class="text-sm lg:text-lg">OST 아카이브</p>
              </a>
              <a class="flex flex-col lg:flex-row items-center justify-center gap-x-2 h-16" rel="external" href="https://support.kkutu.io/order/main/packages/membership/?group_id=2" target="_blank">
                <span class="material-symbols-outlined icons-header">store</span>
                <p class="text-sm lg:text-lg">멤버십</p>
              </a>
              <a class="flex flex-col lg:hidden items-center justify-center gap-x-2 h-16" rel="external" href="https://cs.kkutu.io/faq" target="_blank">
                <span class="material-symbols-outlined icons-header">help</span>
                <p class="text-sm lg:text-lg">고객지원</p>
              </a>
            <a class="flex flex-col lg:flex-row items-center justify-center gap-x-2 h-16" rel="external" href="/wordsheet">
                <span class="material-symbols-outlined icons-header">collections_bookmark</span>
                <p class="text-sm lg:text-lg">단어장</p>
            </a>
            <a class="flex flex-col lg:hidden items-center justify-center gap-x-2 h-16" rel="external" href="/rank">
                <span class="material-symbols-outlined icons-header">emoji_events</span>
                <p class="text-sm lg:text-lg">랭킹</p>
            </a>
            </div>
          </div>
    </div>
</div>
