<script>
    import { onMount } from 'svelte';
    import Glide from '@glidejs/glide';
    const title = '글자로 놀자! 끄투 온라인';

    var slideData = [
                {
                    "id": 0,
                    "link": "/",
                    "color": "#FFFFFF",
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
    
    var noticeData = "끄투리오에 오신 것을 환영합니다.";
    var patchData = "<p>2021년 10월 20일 업데이트 내용입니다.</p>";

    const serverName = ["감자", "냉이", "다래", "레몬", "망고", "보리", "상추", "아욱", "20세 이상"];
    let jsonDataServers = { list: [3], max: 9 };

    onMount(async () => {
        // Fetch slide data
        try{
        const noticeResponse = await fetch('https://static.kkutu.io/global_notice.html');
        noticeData = await noticeResponse.text();

        const slideResponse = await fetch('https://cdn.kkutu.io/static/slides.json');
        slideData = await slideResponse.json();
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

    onMount(() => {
        const glide = new Glide('.glide', {
            type: 'carousel',
            startAt: 0,
            perView: 1,
            autoplay: 5000,
            hoverpause: true,
            animationDuration: 800,
            animationTimingFunc: 'ease-in-out'
        });

        glide.mount();
    });
</script>
  
<svelte:head>
    <title>끄투리오 - {title}</title>
</svelte:head>


<div class="glide">
    <div class="glide__track" data-glide-el="track">
      <ul class="glide__slides">
        {#each slideData as slide}
        <li class="glide__slide bg-white pt-14 flex justify-center items-center">
            <a href={slide.link}>
            <img src={slide.slides[0].desktop} class="hidden h-72 lg:block object-cover" alt="Desktop UI"/>
            <img src={slide.slides[0].mobile} class="h-54 lg:hidden object-cover" alt="Mobile UI"/>
        </a>
        </li>
        {/each}
      </ul>
    </div>
    <div class="glide__bullets" data-glide-el="controls[nav]">
        {#each slideData as slide}
        <button class="glide__bullet" data-glide-dir={"=" + slide.id}></button>
        {/each}
    </div>
</div>
<div class="max-w-screen-xl mx-auto lg:p-12 p-4">
    <!-- Notice area -->
     <div class="text-blue-600 bg-blue-100 border-blue-200 border p-4 lg:px-8 rounded-xl">
        <i class="fa-solid fa-bell lg:mr-3"></i>
        <strong>공지사항</strong>
        <span class="block lg:inline-block lg:pl-4 lg:ml-4 lg:border-l border-gray-300">
            {@html noticeData}
        </span>
     </div>
    
    <!-- Gridded area -->
     <div class="grid grid-cols-1 lg:grid-cols-2 gap-4 mt-8">
        <div class="bg-white border border-gray-200 rounded-xl p-4 lg:p-6 flex flex-col">
            <h2 class="mb-6 font-bold text-2xl">채널 목록</h2>
            {#each jsonDataServers.list as serverUsers, index}
            <a href={`${serverUsers === null ? "/" : "https://kkutu.io?server="+index}`}>
                <div class="rounded-xl text-gray-900 mb-4">
                    <div class="flex justify-between">
                        <h3 class="text-xl font-bold text-[#3553A0]">{serverName[index]} 채널</h3>
                        <span class="font-normal text-right text-gray-500">{serverUsers === null ? '점검 중' : `${serverUsers} / ${jsonDataServers.max}`}</span>
                    </div>
                    <div class="border-2 h-5 mt-2 rounded-full">
                        <div class={`${serverUsers === null ? "bg-transparent" : "bg-[#3553A0]"} h-full rounded-full`} style={`width: ${(serverUsers / jsonDataServers.max) * 100}%`}>
                    </div>
                    </div>
                </div>
            </a>
            {/each}
        </div>
        <div class="bg-white border border-gray-200 rounded-xl p-4 flex flex-col ">
            <h2 class="mb-6 font-bold text-2xl">업데이트 내역</h2>
            <iframe src="https://static.kkutu.io/kkutu_bulletin" width="100%" height="500" allowtransparency="true" frameborder="0" sandbox="allow-popups allow-popups-to-escape-sandbox allow-same-origin allow-scripts"></iframe>
        </div>
    </div>
</div>