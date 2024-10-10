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
    
    var noticeData = "";
    var patchData = "<p>2021년 10월 20일 업데이트 내용입니다.</p>";

    const serverName = ["감자", "냉이", "다래", "레몬", "망고", "보리", "상추", "아욱", "20세 이상"];
    let jsonDataServers = { list: [], max: 9 };
    let glide;
    let filteredData = [];
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
        const noticeResponse = await fetch('https://static.kkutu.io/static_notice.html');
        noticeData = await noticeResponse.text();

        const slideResponse = await fetch('https://static.kkutu.io/slides.json');
        slideData = await slideResponse.json();
        updateSlides();

        const rankResponse = await fetch('https://kkutu.io/ranking?p=0');
        rankData = await rankResponse.json();
        filteredData = rankData.data.data.slice(0, 10);
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
    });
</script>
  
<svelte:head>
    <title>끄투리오 - {title}</title>
</svelte:head>

<div class="dark:bg-gray-900">
    <div class="glide">
        <!-- Slide Left/right btn -->
        <div class="glide__arrows" data-glide-el="controls">
            <button class="glide__arrow glide__arrow--left rounded-full bg-white h-8 w-8" data-glide-dir="<">
                <i class="fa-solid fa-chevron-left"></i>
            </button>
            <button class="glide__arrow glide__arrow--right rounded-full bg-white h-8 w-8" data-glide-dir=">">
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
    <div class="max-w-screen-xl mx-auto lg:p-12 p-4">
        <!-- Notice area -->
        <div class="dark:border-blue-700 dark:text-blue-300 dark:bg-blue-950 text-blue-600 bg-blue-100 border-blue-200 border p-4 lg:px-8 rounded-xl">
            <i class="fa-solid fa-bell lg:mr-3"></i>
            <strong>공지사항</strong>
            <span class="block lg:inline-block lg:pl-4 lg:ml-4 lg:border-l dark:border-gray-700 border-gray-300">
                {@html noticeData}
            </span>
        </div>
        
        <!-- Gridded area -->
        <div class="grid grid-cols-1 lg:grid-cols-2 gap-4 mt-8">
            <div class="dark:bg-gray-800 dark:text-white bg-white lg:border dark:border-gray-700  border-gray-200 rounded-xl p-4 lg:p-6 flex flex-col">
                <div class="flex justify-between mb-6 items-center">
                    <h2 class="font-bold text-2xl">서버 목록</h2>
                    <a href={`/?server=0`} class="bg-gray-200 dark:bg-gray-700 py-2 px-3 rounded-xl transform ease-in duration-100 active:scale-95">빠른 시작</a>
                </div>
                {#each jsonDataServers.list as serverUsers, index}
                <a rel="external" href={`${serverUsers === null ? "/" : "https://kkutu.io/?server="+index}`}>
                    <div class="rounded-xl text-gray-900 mb-8">
                        <div class="flex justify-between">
                            <h3 class="text-xl font-bold dark:text-blue-300 text-[#3553A0]">{serverName[index]} 채널</h3>
                            <span class="font-normal text-right dark:text-gray-300 text-gray-500">{serverUsers === null ? '점검 중' : `${serverUsers} / ${jsonDataServers.max}`}</span>
                        </div>
                        <div class="dark:bg-gray-900 bg-gray-100 h-2 mt-3">
                            <div class={`${serverUsers === null ? "bg-transparent" : "dark:bg-blue-300 bg-[#3553A0]"} h-full`} style={`width: ${(serverUsers / jsonDataServers.max) * 100}%`}>
                        </div>
                        </div>
                    </div>
                </a>
                {/each}
            </div>
            <div class="dark:bg-gray-800 dark:text-white bg-white lg:border dark:border-gray-700  border-gray-200 rounded-xl p-4 lg:p-6 flex flex-col ">
                <h2 class="mb-6 font-bold text-2xl">랭킹</h2>
                {#each filteredData as rank, index}
                    <div class="rounded-xl text-gray-900 mb-4">
                        <div class="flex justify-between">
                            <h3 class="text-xl font-bold dark:text-blue-300 text-[#3553A0]">{rank.rank + 1}위 {rank.name}</h3>
                            <span class="font-normal text-right dark:text-gray-300 text-gray-500">{rank.score}점</span>
                        </div>
                    </div>
                {/each}
            </div>
        </div>
    </div>
</div>