<script>
    import { onMount, tick } from "svelte";
    import GameLoading from "$lib/game/lobby/GameLoading.svelte";
    import { i18n, loadLocale, t as tr } from "$lib/game/i18n.svelte.js";
    import { getLevelImage } from "../lib/getLevelImg.js";
    import { getMoremi } from "../lib/getMoremi.js";
    import { loadAuth, loadBlock, loadUser } from "$lib/session.js";
    import {
        clientPref,
        setClientPref,
        loadClientPref,
        channelUrl,
        recommendedUrl,
    } from "$lib/clientPref.svelte.js";

    let user = $state("Guest User");
    let authVendor = $state("KKuTu");
    let vendorId = $state("0");
    let name = "Moremi";
    let profileImage = "";

    let ingameName = $state("");
    let score = $state(0);
    let data = { status: "Guest user" };

    const title = "글자로 놀자! 끄투 온라인";

    let slideData = $state([
        {
            id: "loading",
            link: "/",
            color: "#fff",
            slides: [],
        },
    ]);

    let patchNoteData = [
        {
            title: "2021년 10월 20일 업데이트",
            link: "/patch/20211020",
            type: "game",
        },
    ];

    let blockData = $state({});

    let patchData = "<p>업데이트 내용입니다.</p>";

    const serverName = [
        "감자",
        "냉이",
        "다래",
        "레몬",
        "망고",
        "보리",
        "상추",
        "아욱",
        "자두",
        "참외",
        "커피",
        "토란",
    ];
    // Guests may only enter these (same set as ServersApi.GUEST_CHANNELS).
    const GUEST_CHANNELS = [0, 1];
    let jsonDataServers = $state({ list: [], max: 9 });
    let swiperEl = $state();
    let slidePage = $state(0);

    const WEB_API_NEWS_BASE =
        import.meta.env.VITE_WEB_API_NEWS_BASE ||
        "https://webapi.kkutu.io/v1/news";
    let selectedNewsTab = $state("all");
    let newsLoading = $state(true);
    let newsError = $state("");
    let newsRequestId = 0;
    let newsTabs = $state([
        {
            slug: "all",
            name: "전체보기",
            apiPath: "/v1/news/all",
            boardUrl: "https://cafe.naver.com/kkutuio",
            enabled: true,
        },
    ]);
    let newsData = $state({
        defaultTab: "all",
        selectedTab: {
            slug: "all",
            name: "전체보기",
            apiPath: "/v1/news/all",
            boardUrl: "https://cafe.naver.com/kkutuio",
        },
        tabs: [
            {
                slug: "all",
                name: "전체보기",
                apiPath: "/v1/news/all",
                boardUrl: "https://cafe.naver.com/kkutuio",
                enabled: true,
            },
        ],
        items: [],
    });

    async function loadNewsTab(slug) {
        const requestId = ++newsRequestId;
        selectedNewsTab = slug;
        newsLoading = true;
        newsError = "";

        try {
            const response = await fetch(
                `${WEB_API_NEWS_BASE}/${encodeURIComponent(slug)}`,
            );
            if (!response.ok)
                throw new Error(`KKuTuIO Web API returned ${response.status}`);

            const payload = await response.json();
            if (requestId === newsRequestId) {
                newsData = payload;
                if (slug === "all" && Array.isArray(payload.tabs))
                    newsTabs = payload.tabs;
            }
        } catch (error) {
            if (requestId === newsRequestId) {
                newsError = "새로운 소식을 불러오지 못했습니다.";
                console.error(error);
            }
        } finally {
            if (requestId === newsRequestId) newsLoading = false;
        }
    }

    async function updateSlides() {
        await tick();
        if (!swiperEl || slideData.length === 0) return;

        // Swiper's web component, registered on the client only.
        const { register } = await import("swiper/element/bundle");
        register();

        const many = slideData.length > 1;
        const reducedMotion = window.matchMedia(
            "(prefers-reduced-motion: reduce)",
        ).matches;
        Object.assign(swiperEl, {
            slidesPerView: 1,
            loop: many,
            speed: reducedMotion ? 0 : 700,
            grabCursor: true,
            keyboard: { enabled: true },
            a11y: {
                prevSlideMessage: "이전 슬라이드",
                nextSlideMessage: "다음 슬라이드",
            },
            autoplay:
                many && !reducedMotion
                    ? {
                          delay: 5000,
                          disableOnInteraction: false,
                          pauseOnMouseEnter: true,
                      }
                    : false,
            on: {
                slideChange(swiper) {
                    slidePage = swiper.realIndex;
                },
            },
        });
        swiperEl.initialize();
    }

    function goLeft() {
        swiperEl?.swiper?.slidePrev();
    }

    function goRight() {
        swiperEl?.swiper?.slideNext();
    }

    function getSlideLink(slide) {
        const mobile =
            typeof window !== "undefined" && window.innerWidth < 1000;
        return mobile && slide.m_link ? slide.m_link : slide.link || "/";
    }

    function getUserId(provider, id) {
        const normalizedProvider =
            typeof provider === "string" ? provider.trim().toLowerCase() : "";
        const normalizedId =
            id === undefined || id === null ? "" : String(id).trim();
        if (!normalizedProvider || !normalizedId) return "";

        // Passkey, recovery, and optional email/password sign-ins keep the
        // legacy game-profile id in vendorId.  LOCAL is the login method, not
        // a prefix belonging to that profile.
        return normalizedProvider === "local"
            ? normalizedId
            : `${normalizedProvider}-${normalizedId}`;
    }

    function processNick(nick) {
        if (typeof nick !== "string" || nick.length === 0)
            return "계정 설정 필요";
        const [title, discriminator] = nick.split("#", 2);
        return (
            title +
            (discriminator
                ? `<small style="color:#bbb">#${discriminator}</small>`
                : "")
        );
    }

    onMount(async () => {
        loadClientPref();

        try {
            const jsonData = await loadAuth();
            data = jsonData;
        } catch (e) {
            data = { status: "Guest user" };
        }

        const userId = getUserId(data?.authVendor, data?.vendorId);
        if (data?.status !== "Guest user" && userId) {
            authVendor = String(data.authVendor);
            vendorId = String(data.vendorId);
            name =
                typeof data.name === "string" && data.name
                    ? data.name
                    : "Moremi";
            profileImage = typeof data.image === "string" ? data.image : "";
            user = name;

            if (authVendor === "DISCORD") {
                profileImage = profileImage.replace(
                    "/avatars/0/",
                    `/avatars/${vendorId}/`,
                );
                profileImage = profileImage + ".webp";
            }

            console.log(`User ${name} is logged in with ${authVendor}`);

            // get user data
            const userData = await loadUser(userId);

            ingameName = processNick(userData?.profile?.title);
            score = Number(userData?.data?.score) || 0;
        } else {
            console.log("User is not logged in");
        }

        loadNewsTab("all");

        // Fetch slide data
        try {
            const slideResponse = await fetch(
                "https://static.kkutu.io/slides.json",
            );
            const loadedSlides = await slideResponse.json();
            if (Array.isArray(loadedSlides)) slideData = loadedSlides;
            await updateSlides();

            blockData = await loadBlock();
        } catch (e) {
            console.error(e);
        }

        reloadList();
    });

    function reloadList() {
        fetch("/servers")
            .then((response) =>
                response.ok
                    ? response.json()
                    : Promise.reject(new Error(`servers ${response.status}`)),
            )
            .then((data) => {
                jsonDataServers = data;
            })
            .catch(console.error);
    }
    // Category colours follow the board banners (행사 green, 업데이트 blue, 공지사항 amber, 점검 pink).
    function catColor(name = "") {
        if (name.includes("행사")) return "#5aa84a";
        if (name.includes("업데이트")) return "#3d9fe0";
        if (name.includes("공지")) return "#cc8a2c";
        if (name.includes("점검")) return "#e0577d";
        return "var(--kk-accent)";
    }
    // Few channels: one wide row each. Many: two columns.
    const manyChannels = $derived(jsonDataServers.list.length > 4);
    /** Crowd label for a channel; `tone` picks the colour token. */
    function crowd(users) {
        const max = jsonDataServers.max || 1;
        if (users === null)
            return { label: "점검 중", tone: "muted", pct: 0, usable: false };
        const pct = Math.min(100, (users / max) * 100);
        if (users >= max)
            return { label: "가득 참", tone: "danger", pct, usable: false };
        if (pct >= 70)
            return { label: "혼잡", tone: "warn", pct, usable: true };
        return { label: "여유", tone: "accent", pct, usable: true };
    }
    let channelDialog = $state();
    let clientDialog = $state();
    const clientOptions = [
        {
            value: "new",
            label: "새로운 클라이언트",
            icon: "rocket_launch",
            recommended: true,
        },
        {
            value: "legacy",
            label: "레거시 클라이언트",
            icon: "history",
            eol: true,
        },
    ];
    function onClientKey(e, i) {
        const to = {
            ArrowRight: i + 1,
            ArrowDown: i + 1,
            ArrowLeft: i - 1,
            ArrowUp: i - 1,
        }[e.key];
        if (to === undefined) return;
        e.preventDefault();
        const next =
            clientOptions[(to + clientOptions.length) % clientOptions.length];
        setClientPref(next.value);
        e.currentTarget.parentElement
            .querySelector(`[data-value="${next.value}"]`)
            ?.focus();
    }
    function openChannels() {
        reloadList();
        channelDialog?.showModal();
    }
    const quickLinks = [
        {
            href: "https://cafe.naver.com/ArticleList.nhn?search.clubid=30131388&search.menuid=22&search.boardtype=L",
            icon: "playlist_add",
            label: "단어 신청",
        },
        {
            href: "/ost",
            icon: "music_note",
            label: "OST 아카이브",
            external: true,
        },
        {
            href: "https://support.kkutu.io/order/main/packages/membership/?group_id=2",
            icon: "store",
            label: "멤버십",
            external: true,
            target: "_blank",
        },
    ];
    function hideIP(target) {
        if (blockData.blockType !== "IP") return target;

        const ip = blockData.target;
        const isIpv6 = ip.includes(":");
        const splitIp = isIpv6 ? ip.split(":") : ip.split(".");
        return isIpv6
            ? `${splitIp[0]}:${splitIp[1]}:*:*:*:*`
            : `${splitIp[0]}.${splitIp[1]}.*.*`;
    }

    function tsconv(timestamp) {
        const date = new Date(timestamp);
        return `${date.getFullYear()}년 ${date.getMonth() + 1}월 ${date.getDate()}일`;
    }

    function handleImgErr(e, category, filename) {
        const img = e.target;
        const baseURL = "https://cdn.kkutu.io/img/kkutu/moremi";
        if (img.src.includes("/event/")) {
            img.src = `${baseURL}/${category}/default.png`;
            img.onerror = null;
        } else {
            if (!filename || filename === "default.png")
                img.src = `${baseURL}/${category}/default.png`;
            else img.src = `${baseURL}/event/${filename}`;
        }
    }

    // 게임 시작: the loading veil's tiles cover this page first, and the client carries on from the covered screen.
    let entering = $state(false);
    function enterGame(e) {
        if (e.defaultPrevented || e.button !== 0 || e.metaKey || e.ctrlKey || e.shiftKey || e.altKey) return;
        e.preventDefault();
        const href = e.currentTarget.href;
        entering = true;
        loadLocale(); // the veil's text and tip are client messages
        setTimeout(() => (location.href = href), 800);
    }
    onMount(() => {
        const back = (ev) => ev.persisted && (entering = false); // coming back from the cache
        window.addEventListener("pageshow", back);
        return () => window.removeEventListener("pageshow", back);
    });
</script>

<svelte:head>
    <title>끄투리오 - {title}</title>
</svelte:head>

{#if blockData.blocked}
    <div
        class="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/55 px-4 pt-20 pb-6 backdrop-blur-md"
    >
        <div
            class="w-full max-w-xl rounded-2xl border border-white/10 bg-slate-900/90 px-6 py-8 text-center text-white shadow-2xl shadow-slate-950/50 backdrop-blur-xl sm:p-8"
        >
            {#if blockData.onlyGuestPunish && blockData.blockType == "IP"}
                <h2 class="text-3xl font-bold">게임 이용 제한됨</h2>
                <p class="mt-4 text-lg text-gray-300">
                    운영정책 위반으로 <strong>손님 상태에서의 게임 이용</strong
                    >이 제한되었습니다.
                </p>

                <div class="mt-2">
                    <table
                        class="w-full text-center text-lg border-separate border-spacing-y-3"
                    >
                        <tbody>
                            <tr>
                                <td class="w-24 font-semibold">IP주소</td>
                                <td class="w-72 text-gray-300"
                                    >{hideIP(blockData.target)}</td
                                >
                            </tr>
                            <tr>
                                <td class="w-24 font-semibold">해제일시</td>
                                <td class="w-72 text-gray-300"
                                    >{blockData.pardonTime}</td
                                >
                            </tr>
                            <tr>
                                <td class="font-semibold">제한기간</td>
                                <td class="text-gray-300"
                                    >{blockData.duration}</td
                                >
                            </tr>
                            <tr>
                                <td class="font-semibold">제한사유</td>
                                <td class="text-gray-300">{blockData.reason}</td
                                >
                            </tr>
                            <tr>
                                <td class="font-semibold">남은시간</td>
                                <td class="text-gray-300">{blockData.remain}</td
                                >
                            </tr>
                        </tbody>
                    </table>

                    <p class="mt-4 text-lg leading-8 text-gray-300">
                        문의가 있으실 경우 고객센터로 문의해주시기 바랍니다.
                        이용제한 기간 중 다른 계정을 이용하여 게임을 플레이할
                        경우,
                        <strong>이용제한 기간이 연장</strong>될 수 있습니다.<br
                        />
                        문의 시
                        <span class="text-xl font-bold text-white"
                            >{blockData.inquiryId}</span
                        > 문구와 함께 문의를 접수해 주세요.
                    </p>
                    <a
                        href="https://support.kkutu.io/plugin/support_manager/knowledgebase/view/1"
                        target="_blank"
                    >
                        <button
                            class="mt-4 kk-btn kk-btn-primary px-5 text-base"
                            >고객센터 문의하기</button
                        >
                    </a>
                    <a href="https://kkutu.io/login" rel="external">
                        <button class="mt-2 text-gray-300"
                            >로그인 (게임 이용 가능)</button
                        >
                    </a>
                </div>
            {:else}
                <h2 class="text-3xl font-bold">게임 이용 제한됨</h2>
                <p class="mt-4 text-lg text-gray-300">
                    운영정책 위반으로 <strong>게임 이용</strong>이
                    제한되었습니다.
                </p>

                <div class="mt-2">
                    <table
                        class="w-full text-center text-lg border-separate border-spacing-y-3"
                    >
                        <tbody>
                            <tr>
                                <td class="w-24 font-semibold">식별번호</td>
                                <td class="w-72 text-gray-300"
                                    >{blockData.target}</td
                                >
                            </tr>
                            <tr>
                                <td class="w-24 font-semibold">해제일시</td>
                                <td class="w-72 text-gray-300"
                                    >{blockData.pardonTime}</td
                                >
                            </tr>
                            <tr>
                                <td class="font-semibold">제한기간</td>
                                <td class="text-gray-300"
                                    >{blockData.duration}</td
                                >
                            </tr>
                            <tr>
                                <td class="font-semibold">제한사유</td>
                                <td class="text-gray-300">{blockData.reason}</td
                                >
                            </tr>
                            <tr>
                                <td class="font-semibold">남은시간</td>
                                <td class="text-gray-300">{blockData.remain}</td
                                >
                            </tr>
                        </tbody>
                    </table>

                    <p class="mt-4 text-lg leading-8 text-gray-300">
                        문의가 있으실 경우 고객센터로 문의해주시기 바랍니다.
                        이용제한 기간 중 다른 계정을 이용하여 게임을 플레이할
                        경우,
                        <strong>이용제한 기간이 연장</strong>될 수 있습니다.<br
                        />
                        문의 시
                        <span class="text-xl font-bold text-white"
                            >{blockData.inquiryId}</span
                        > 문구와 함께 문의를 접수해 주세요.
                    </p>
                    <div class="mt-4 flex flex-wrap justify-center gap-3">
                        <a
                            href="/account/sanctions"
                            class="inline-flex items-center justify-center rounded-full border border-slate-500 px-5 py-2.5 text-base font-bold text-slate-100 transition hover:border-slate-300 hover:bg-white/10"
                            >제재 내역</a
                        >
                        <a
                            href="https://support.kkutu.io/plugin/support_manager/knowledgebase/view/1"
                            target="_blank"
                        >
                            <button class="kk-btn kk-btn-primary px-5 text-base"
                                >고객센터 문의하기</button
                            >
                        </a>
                    </div>
                    <a href="https://kkutu.io/logout" rel="external">
                        <button class="mt-2 text-gray-300">로그아웃</button>
                    </a>
                </div>
            {/if}
        </div>
    </div>
{/if}
<div class="kk bg-(--kk-bg) text-(color:--kk-text)">
    <div class="mx-auto max-w-screen-xl px-4 pt-4 lg:px-8">
        <div class="relative overflow-hidden rounded-3xl">
            <swiper-container bind:this={swiperEl} init="false" class="block">
                {#each slideData as slide, index (slide.id ?? index)}
                    <swiper-slide
                        class="flex min-h-[216px] items-center justify-center lg:min-h-[400px]"
                        style={`background: ${slide.color || "#000"}`}
                    >
                        {#if slide.slides?.[0]?.desktop || slide.slides?.[0]?.mobile}
                            <a
                                href={getSlideLink(slide)}
                                class="block w-full"
                                draggable="false"
                            >
                                {#if slide.slides?.[0]?.desktop}<img
                                        src={slide.slides[0].desktop}
                                        class="hidden h-[400px] w-full select-none object-cover lg:block"
                                        alt="끄투리오 안내"
                                        draggable="false"
                                    />{/if}
                                {#if slide.slides?.[0]?.mobile}<img
                                        src={slide.slides[0].mobile}
                                        class="h-54 w-full select-none object-cover lg:hidden"
                                        alt="끄투리오 안내"
                                        draggable="false"
                                    />{/if}
                            </a>
                        {/if}
                    </swiper-slide>
                {/each}
            </swiper-container>

            <div class="absolute bottom-0 right-0 z-10">
                <div class="flex items-center gap-x-3 p-4 lg:p-6">
                    <!-- slide controls -->
                    <div class="flex gap-x-2">
                        <!-- use material icons -->
                        <button
                            class="kk-btn grid h-10 min-h-0 w-10 place-items-center p-0 text-(color:--kk-text)"
                            onclick={goLeft}
                        >
                            <span class="material-symbols-outlined">
                                chevron_left
                            </span>
                        </button>
                        <button
                            class="kk-btn grid h-10 min-h-0 w-10 place-items-center p-0 text-(color:--kk-text)"
                            onclick={goRight}
                        >
                            <span class="material-symbols-outlined">
                                chevron_right
                            </span>
                        </button>
                    </div>
                    <div
                        class="rounded-xl border border-(color:--kk-line) bg-(--kk-surface) px-4 py-2 text-sm flex items-center"
                    >
                        <span class="font-bold">{slidePage + 1}</span>&nbsp;/ {slideData.length}
                    </div>
                </div>
            </div>
        </div>
    </div>
    <div class="mx-auto max-w-screen-xl px-4 pt-6 pb-5 lg:px-8">
        <div class="grid items-start gap-6 lg:grid-cols-[1fr_380px]">
            <!-- 새로운 소식 -->
            <section
                class="order-2 min-w-0 lg:order-1"
                aria-labelledby="news-title"
            >
                <div class="mb-3 flex items-center justify-between gap-3">
                    <h2
                        id="news-title"
                        class="flex shrink-0 items-center text-xl font-extrabold"
                    >
                        <span
                            class="material-symbols-outlined mr-2 text-(color:--kk-accent-strong)"
                            >notifications</span
                        >새로운 소식
                    </h2>
                    <div
                        class="flex min-w-0 flex-1 items-center justify-end gap-2"
                    >
                        <label class="sr-only" for="news-tab-select"
                            >소식 게시판</label
                        >
                        <select
                            id="news-tab-select"
                            class="kk-input min-w-0 max-w-44 px-3 py-2 text-sm font-bold lg:hidden"
                            value={selectedNewsTab}
                            onchange={(event) =>
                                loadNewsTab(event.currentTarget.value)}
                        >
                            {#each newsTabs as newsTab}
                                <option
                                    value={newsTab.slug}
                                    disabled={newsTab.enabled === false}
                                    >{newsTab.name}</option
                                >
                            {/each}
                        </select>
                        <nav
                            class="hidden min-w-0 gap-1 overflow-x-auto lg:flex"
                            aria-label="소식 게시판"
                        >
                            {#each newsTabs.slice(0, 5) as newsTab}
                                <button
                                    type="button"
                                    style:--cat={catColor(newsTab.name)}
                                    class={`cat-tab shrink-0 whitespace-nowrap rounded-xl px-3 py-1.5 text-sm font-bold transition-colors ${selectedNewsTab === newsTab.slug ? "cat-on" : "text-(color:--kk-muted) hover:bg-(--kk-surface-2) hover:text-(color:--kk-text)"} disabled:cursor-not-allowed disabled:opacity-35`}
                                    aria-pressed={selectedNewsTab ===
                                        newsTab.slug}
                                    disabled={newsTab.enabled === false}
                                    onclick={() => loadNewsTab(newsTab.slug)}
                                    >{newsTab.name}</button
                                >
                            {/each}
                        </nav>
                        <a
                            href={newsData.selectedTab?.boardUrl ||
                                "https://cafe.naver.com/kkutuio"}
                            target="_blank"
                            rel="noopener"
                            class="kk-btn inline-flex h-9 min-h-0 w-9 items-center justify-center p-0 text-(color:--kk-muted)"
                            aria-label={`${newsData.selectedTab?.name || "전체"} 게시판에서 더 보기`}
                            ><span class="material-symbols-outlined">add</span
                            ></a
                        >
                    </div>
                </div>
                <div
                    class="kk-card divide-y divide-(color:--kk-line) overflow-hidden"
                    data-news-list
                >
                    {#if newsLoading && newsData.items.length === 0}
                        <p class="py-12 text-center text-(color:--kk-muted)">
                            새로운 소식을 불러오는 중입니다.
                        </p>
                    {:else if newsError && newsData.items.length === 0}
                        <p class="py-12 text-center text-(color:--kk-muted)">
                            {newsError}
                        </p>
                    {:else}
                        {#each newsData.items.slice(0, 5) as item}
                            <a
                                href={item.articleUrl}
                                target="_blank"
                                rel="noopener"
                                style:--cat={catColor(item.menuName)}
                                class="flex items-center gap-3 px-4 py-3 transition-colors hover:bg-(--kk-surface-2)"
                                data-news-item
                            >
                                <span
                                    class="cat-chip hidden shrink-0 sm:inline-flex"
                                    >{item.menuName}</span
                                >
                                <span class="min-w-0 flex-1 truncate font-bold"
                                    >{item.subject}</span
                                >
                                <span
                                    class="shrink-0 text-sm text-(color:--kk-muted)"
                                    >{item.displayDate}</span
                                >
                            </a>
                        {/each}
                    {/if}
                </div>
                <nav
                    class="mt-3 grid grid-cols-2 gap-3 sm:grid-cols-4"
                    aria-label="바로가기"
                >
                    {#each quickLinks as q}
                        <a
                            href={q.href}
                            rel={q.external ? "external" : undefined}
                            target={q.target}
                            class="kk-card flex h-14 items-center justify-center gap-2 font-bold text-(color:--kk-muted) transition-colors hover:bg-(--kk-accent-soft) hover:text-(color:--kk-text)"
                            ><span
                                class="material-symbols-outlined"
                                aria-hidden="true">{q.icon}</span
                            >{q.label}</a
                        >
                    {/each}
                </nav>
            </section>

            <!-- 게임 시작: the sign stands on its own, login sits underneath. -->
            <section
                class="order-1 flex flex-col items-center gap-4 lg:order-2"
                aria-label="게임 시작"
            >
                <div class="jjo">
                    <img
                        class="jjo-eye jjo-eye-l"
                        src="https://cdn.kkutu.io/img/jjoeyeL.svg"
                        alt=""
                        aria-hidden="true"
                    />
                    <img
                        class="jjo-eye jjo-eye-r"
                        src="https://cdn.kkutu.io/img/jjoeyeR.svg"
                        alt=""
                        aria-hidden="true"
                    />
                    <div class="jjo-bar">
                        <img
                            class="jjo-nose"
                            src="https://cdn.kkutu.io/img/jjonose.svg"
                            alt=""
                            aria-hidden="true"
                        />
                        <a
                            href={recommendedUrl()}
                            rel="external"
                            onclick={enterGame}
                            class="jjo-btn">게임 시작!</a
                        >
                    </div>
                </div>

                <div
                    class="kk-card flex w-full items-center justify-between gap-3 px-4 py-3"
                >
                    {#if user == "Guest User"}
                        <p class="text-sm font-bold">
                            로그인하고 기록을 저장하세요!
                        </p>
                        <a
                            href="/login"
                            rel="external"
                            class="kk-btn kk-btn-primary inline-flex h-9 min-h-0 shrink-0 items-center gap-1.5 px-4 text-sm"
                            ><span
                                class="material-symbols-outlined"
                                style="font-size:20px"
                                aria-hidden="true">login</span
                            >로그인</a
                        >
                    {:else}
                        <!-- Login status -->
                        <div class="flex items-center gap-x-4 w-full">
                            {#await getMoremi(getUserId(authVendor, vendorId)) then equip}
                                {#if equip}
                                    <div
                                        class="relative h-[120px] w-[120px] shrink-0 origin-top-left scale-[0.6] -mb-[48px] -mr-[48px]"
                                    >
                                        <img
                                            src={`https://cdn.kkutu.io/img/kkutu/moremi/back/${equip.Mback || "default.png"}`}
                                            class="absolute object-cover w-[120px] h-[120px]"
                                            alt="BG"
                                            onerror={(e) =>
                                                handleImgErr(
                                                    e,
                                                    "back",
                                                    equip.Mback,
                                                )}
                                        />
                                        <img
                                            src={`https://cdn.kkutu.io/img/kkutu/moremi/body/${equip.Mbody || "default.png"}`}
                                            class="absolute object-cover w-[120px] h-[120px]"
                                            alt="Moremi Body"
                                        />
                                        <img
                                            src={`https://cdn.kkutu.io/img/kkutu/moremi/eye/${equip.Meye || "default.png"}`}
                                            class="absolute object-cover w-[120px] h-[120px]"
                                            alt="Moremi Eye"
                                            onerror={(e) =>
                                                handleImgErr(
                                                    e,
                                                    "eye",
                                                    equip.Meye,
                                                )}
                                        />
                                        <img
                                            src={`https://cdn.kkutu.io/img/kkutu/moremi/mouth/${equip.Mmouth || "default.png"}`}
                                            class="absolute object-cover w-[120px] h-[120px]"
                                            alt="Moremi Mouth"
                                            onerror={(e) =>
                                                handleImgErr(
                                                    e,
                                                    "mouth",
                                                    equip.Mmouth,
                                                )}
                                        />
                                        <img
                                            src={`https://cdn.kkutu.io/img/kkutu/moremi/clothes/${equip.Mclothes || "default.png"}`}
                                            class="absolute object-cover w-[120px] h-[120px]"
                                            alt="Moremi Pants"
                                            onerror={(e) =>
                                                handleImgErr(
                                                    e,
                                                    "clothes",
                                                    equip.Mclothes,
                                                )}
                                        />
                                        <img
                                            src={`https://cdn.kkutu.io/img/kkutu/moremi/shoes/${equip.Mshoes || "default.png"}`}
                                            class="absolute object-cover w-[120px] h-[120px]"
                                            alt="Moremi Shoes"
                                            onerror={(e) =>
                                                handleImgErr(
                                                    e,
                                                    "shoes",
                                                    equip.Mshoes,
                                                )}
                                        />
                                        <img
                                            src={`https://cdn.kkutu.io/img/kkutu/moremi/head/${equip.Mhead || "default.png"}`}
                                            class="absolute object-cover w-[120px] h-[120px]"
                                            alt="Moremi Head"
                                            onerror={(e) =>
                                                handleImgErr(
                                                    e,
                                                    "head",
                                                    equip.Mhead,
                                                )}
                                        />
                                        <img
                                            src={`https://cdn.kkutu.io/img/kkutu/moremi/hand/${equip.Mrhand || "default.png"}`}
                                            class="rightHand absolute object-cover w-[120px] h-[120px]"
                                            alt="Moremi Hand"
                                            onerror={(e) =>
                                                handleImgErr(
                                                    e,
                                                    "hand",
                                                    equip.Mrhand,
                                                )}
                                        />
                                        <img
                                            src={`https://cdn.kkutu.io/img/kkutu/moremi/hand/${equip.Mlhand || "default.png"}`}
                                            class="absolute object-cover w-[120px] h-[120px]"
                                            alt="Moremi Hand"
                                            onerror={(e) =>
                                                handleImgErr(
                                                    e,
                                                    "hand",
                                                    equip.Mlhand,
                                                )}
                                        />
                                        <img
                                            src={`https://cdn.kkutu.io/img/kkutu/moremi/headDeco/${equip.MheadDeco || "default.png"}`}
                                            class="absolute object-cover w-[120px] h-[120px]"
                                            alt="Moremi HeadDeco"
                                            onerror={(e) =>
                                                handleImgErr(
                                                    e,
                                                    "headDeco",
                                                    equip.MheadDeco,
                                                )}
                                        />
                                        <img
                                            src={`https://cdn.kkutu.io/img/kkutu/moremi/dressDeco/${equip.MdressDeco || "default.png"}`}
                                            class="absolute object-cover w-[120px] h-[120px]"
                                            alt="Moremi DressDeco"
                                            onerror={(e) =>
                                                handleImgErr(
                                                    e,
                                                    "dressDeco",
                                                    equip.MdressDeco,
                                                )}
                                        />
                                        <img
                                            src={`https://cdn.kkutu.io/img/kkutu/moremi/badge/${equip.BDG || "default.png"}`}
                                            class="absolute object-cover w-[120px] h-[120px]"
                                            alt="Moremi Badge"
                                            onerror={(e) =>
                                                handleImgErr(
                                                    e,
                                                    "badge",
                                                    equip.BDG,
                                                )}
                                        />
                                    </div>

                                    <div class="flex flex-col">
                                        <div class="flex items-center gap-x-2">
                                            <div
                                                class="level"
                                                style={getLevelImage(
                                                    Number(score),
                                                )}
                                            ></div>
                                            <h3
                                                class="text-lg font-extrabold truncate w-40"
                                            >
                                                {@html ingameName}
                                            </h3>
                                        </div>
                                        <p
                                            class="text-(color:--kk-muted) font-bold"
                                        >
                                            {score.toLocaleString()}점
                                        </p>
                                        <a
                                            href="/account"
                                            class="kk-btn mt-1 inline-flex min-h-0 h-8 w-fit items-center gap-x-1 px-3 text-sm"
                                        >
                                            <span
                                                class="material-symbols-outlined text-base"
                                                >account_circle</span
                                            >
                                            계정 관리
                                        </a>
                                    </div>
                                {:else}
                                    <div class="w-[120px] h-[120px]">
                                        <div
                                            class="flex flex-col items-center justify-center h-full"
                                        >
                                            <p class="text-lg font-bold">
                                                계정 설정 필요
                                            </p>
                                            <a href="/play/0" rel="external">
                                                <button
                                                    class="kk-btn kk-btn-primary mt-4 inline-flex w-36 items-center justify-center gap-x-2 text-lg"
                                                >
                                                    <span
                                                        class="material-symbols-outlined"
                                                        >how_To_reg</span
                                                    >
                                                    설정하기
                                                </button>
                                            </a>
                                        </div>
                                    </div>
                                {/if}
                            {:catch error}
                                <div class="w-[120px] h-[120px]">
                                    <div></div>
                                </div>
                            {/await}
                        </div>
                    {/if}
                </div>
                <div class="grid w-full grid-cols-2 gap-3">
                    <button
                        type="button"
                        class="kk-btn inline-flex items-center justify-center gap-2 px-2 text-sm"
                        onclick={openChannels}
                        ><span
                            class="material-symbols-outlined"
                            style="font-size:20px"
                            aria-hidden="true">dns</span
                        >다른 채널 보기</button
                    >
                    <button
                        type="button"
                        class="kk-btn inline-flex items-center justify-center gap-2 px-2 text-sm"
                        onclick={() => clientDialog?.showModal()}
                        ><span
                            class="material-symbols-outlined"
                            style="font-size:20px"
                            aria-hidden="true">tune</span
                        >클라이언트 설정</button
                    >
                </div>
            </section>
        </div>
    </div>

    <!-- 클라이언트 설정 -->
    <dialog
        bind:this={clientDialog}
        class="kk-bouncy kk-card m-auto p-0"
        style="width: min(92vw, 440px)"
        aria-label="클라이언트 설정"
        onclick={(e) => {
            if (e.target === clientDialog) clientDialog.close();
        }}
    >
        <div
            class="flex items-center justify-between gap-2 border-b border-(color:--kk-line) px-5 py-3"
        >
            <h2 class="text-lg font-extrabold">클라이언트 설정</h2>
            <button
                type="button"
                class="grid h-9 w-9 place-items-center rounded-xl text-(color:--kk-muted) hover:bg-(--kk-surface-2)"
                aria-label="닫기"
                onclick={() => clientDialog.close()}
                ><span class="material-symbols-outlined">close</span></button
            >
        </div>
        <div
            class="flex flex-col gap-3 p-4"
            role="radiogroup"
            aria-label="클라이언트"
        >
            {#each clientOptions as o, i}
                {@const on = clientPref.value === o.value}
                <button
                    type="button"
                    role="radio"
                    aria-checked={on}
                    data-value={o.value}
                    tabindex={on ? 0 : -1}
                    onclick={() => setClientPref(o.value)}
                    onkeydown={(e) => onClientKey(e, i)}
                    class="flex items-center gap-3 rounded-xl border-[1.5px] p-4 text-left transition-colors {on
                        ? 'border-(color:--kk-accent) bg-(--kk-accent-soft)'
                        : 'border-(color:--kk-line) bg-(--kk-surface-2) hover:border-(color:--kk-line-strong)'}"
                >
                    <span
                        class="material-symbols-outlined {on
                            ? 'text-(color:--kk-accent-strong)'
                            : 'text-(color:--kk-muted)'}"
                        aria-hidden="true">{o.icon}</span
                    >
                    <span class="flex min-w-0 flex-1 items-center gap-2">
                        <span class="font-extrabold">{o.label}</span>
                        {#if o.recommended}<span class="rec-badge">추천</span
                            >{/if}
                        {#if o.eol}<span class="eol-badge">종료 예정</span>{/if}
                    </span>
                    <span
                        class="material-symbols-outlined text-(color:--kk-accent-strong) {on
                            ? ''
                            : 'invisible'}"
                        style="font-variation-settings:'FILL' 1"
                        aria-hidden="true">check_circle</span
                    >
                </button>
            {/each}
        </div>
    </dialog>

    <!-- 채널 선택 -->
    <dialog
        bind:this={channelDialog}
        class="kk-bouncy kk-card m-auto p-0"
        style:width={manyChannels ? "min(92vw, 560px)" : "min(92vw, 440px)"}
        aria-label="채널 선택"
        onclick={(e) => {
            if (e.target === channelDialog) channelDialog.close();
        }}
    >
        <div
            class="flex items-center justify-between gap-2 border-b border-(color:--kk-line) px-5 py-3"
        >
            <h2 class="text-lg font-extrabold">채널 선택</h2>
            <div class="flex items-center gap-1">
                <button
                    type="button"
                    class="grid h-9 w-9 place-items-center rounded-xl text-(color:--kk-muted) hover:bg-(--kk-surface-2)"
                    aria-label="새로고침"
                    onclick={reloadList}
                    ><span class="material-symbols-outlined">refresh</span
                    ></button
                >
                <button
                    type="button"
                    class="grid h-9 w-9 place-items-center rounded-xl text-(color:--kk-muted) hover:bg-(--kk-surface-2)"
                    aria-label="닫기"
                    onclick={() => channelDialog.close()}
                    ><span class="material-symbols-outlined">close</span
                    ></button
                >
            </div>
        </div>
        <div
            class="grid max-h-[80vh] gap-3 overflow-y-auto p-4 {manyChannels
                ? 'sm:grid-cols-2'
                : ''}"
        >
            <a
                href={recommendedUrl()}
                rel="external"
                class="kk-btn kk-btn-primary flex items-center justify-center gap-2 {manyChannels
                    ? 'sm:col-span-2'
                    : ''}"
                ><span class="material-symbols-outlined" aria-hidden="true"
                    >auto_awesome</span
                >추천 채널로 시작</a
            >
            {#each jsonDataServers.list as serverUsers, index}
                {@const c = crowd(serverUsers)}
                {@const locked =
                    user == "Guest User" && !GUEST_CHANNELS.includes(index)}
                {@const open = c.usable && !locked}
                <svelte:element
                    this={open ? "a" : "div"}
                    href={open ? channelUrl(index) : undefined}
                    rel={open ? "external" : undefined}
                    aria-disabled={open ? undefined : true}
                    class="block rounded-xl border border-(color:--kk-line) bg-(--kk-surface-2) p-4 transition-colors {open
                        ? 'hover:border-(color:--kk-accent) hover:bg-(--kk-accent-soft)'
                        : 'opacity-60'}"
                >
                    <div class="flex items-start justify-between gap-3">
                        <div class="min-w-0">
                            <div class="text-lg font-extrabold">
                                {serverName[index] ?? `${index + 1}번`} 채널
                            </div>
                            <div
                                class="mt-0.5 flex items-center gap-1 text-xs font-bold text-(color:--kk-muted)"
                            >
                                {#if locked}
                                    <span
                                        class="material-symbols-outlined"
                                        style="font-size:14px"
                                        aria-hidden="true">lock</span
                                    >로그인 후 입장 가능
                                {/if}
                            </div>
                        </div>
                        <div class="shrink-0 text-right">
                            <div
                                class="text-sm font-extrabold"
                                style:color={c.tone === "accent"
                                    ? "var(--kk-accent-strong)"
                                    : c.tone === "warn"
                                      ? "var(--kk-warn)"
                                      : c.tone === "danger"
                                        ? "var(--kk-danger)"
                                        : "var(--kk-muted)"}
                            >
                                {c.label}
                            </div>
                            {#if serverUsers !== null}<div
                                    class="font-bold tabular-nums text-(color:--kk-muted)"
                                >
                                    {serverUsers} / {jsonDataServers.max}
                                </div>{/if}
                        </div>
                    </div>
                    <div class="kk-bar mt-3">
                        <span
                            style:width="{c.pct}%"
                            style:background={c.tone === "warn"
                                ? "var(--kk-warn)"
                                : c.tone === "danger"
                                  ? "var(--kk-danger)"
                                  : undefined}
                        ></span>
                    </div>
                </svelte:element>
            {:else}
                <p
                    class="py-6 text-center text-(color:--kk-muted) {manyChannels
                        ? 'sm:col-span-2'
                        : ''}"
                >
                    채널 정보를 불러오는 중입니다.
                </p>
            {/each}
        </div>
    </dialog>
</div>

<style>
    .rec-badge {
        padding: 0.1rem 0.5rem;
        border-radius: var(--kk-r-sm);
        font-size: 0.75rem;
        font-weight: 800;
        line-height: 1.25;
        background-image: linear-gradient(
            to right,
            var(--kk-accent),
            var(--kk-accent-strong)
        );
        color: #fff;
    }
    .eol-badge {
        padding: 0.1rem 0.5rem;
        border-radius: var(--kk-r-sm);
        font-size: 0.75rem;
        font-weight: 800;
        line-height: 1.25;
        background: #4b5563; /* gray-600 */
        color: #fff;
    }
    .cat-chip {
        align-items: center;
        padding: 0.05rem 0.55rem;
        border-radius: var(--kk-r-sm);
        font-size: 0.75rem;
        font-weight: 800;
        background: color-mix(in srgb, var(--cat) 18%, transparent);
        color: color-mix(in srgb, var(--cat) 65%, var(--kk-text));
    }
    .cat-tab.cat-on {
        background: color-mix(in srgb, var(--cat) 18%, transparent);
        color: color-mix(in srgb, var(--cat) 65%, var(--kk-text));
    }
    /* Legacy 쪼리핑 bar (style: in_m_kkutu.css .jjoDisplayBar), scaled by --s. Ears are 58x46 and the bar starts 25 units below their top. */
    .jjo {
        --s: 1;
        --u: calc(var(--s) * 1px);
        position: relative;
        width: 100%;
        padding-top: calc(25 * var(--u));
    }
    @media (min-width: 400px) {
        .jjo {
            --s: 1.2;
        }
    }
    .jjo-eye {
        position: absolute;
        top: 0;
        width: calc(58 * var(--u));
        height: calc(46 * var(--u));
        z-index: 1;
    }
    .jjo-eye-l {
        left: 0;
    }
    .jjo-eye-r {
        right: 0;
    }
    .jjo-bar {
        position: relative;
        padding: calc(24 * var(--u)) calc(9 * var(--u)) calc(9 * var(--u));
        border: 2px solid #000;
        border-radius: 0 0 calc(10 * var(--u)) calc(10 * var(--u));
        background: #deaf56;
        box-shadow: 0 4px 10px rgb(0 0 0 / 0.2);
    }
    .jjo-nose {
        position: absolute;
        top: calc(4 * var(--u));
        left: 50%;
        width: calc(23 * var(--u));
        transform: translateX(-50%);
    }
    .jjo-btn {
        display: grid;
        place-items: center;
        height: calc(80 * var(--u));
        border-radius: calc(8 * var(--u));
        color: #fff;
        font-size: calc(24 * var(--u));
        font-weight: 800;
        text-shadow: 0 2px 3px rgb(0 0 0 / 0.5);
        background: linear-gradient(#6b5530, #4a3820);
        position: relative;
        overflow: hidden;
        transition:
            transform 0.12s,
            filter 0.12s,
            font-size 0.12s;
    }
    /* The shine: a slanted band of light that sweeps across once each time the pointer (or keyboard focus) arrives on the button. */
    .jjo-btn::after {
        content: "";
        position: absolute;
        inset: 0;
        /* Softness: a wide band whose edge fades out slowly, a low peak opacity (--shine) and a blur (--shine-blur). */
        --shine: 0.28;
        --shine-blur: 7px;
        background: linear-gradient(
            105deg,
            transparent 28%,
            rgb(255 255 255 / var(--shine)) 50%,
            transparent 72%
        );
        filter: blur(var(--shine-blur));
        transform: translateX(-120%);
        pointer-events: none;
    }
    .jjo-btn:hover,
    .jjo-btn:focus-visible {
        filter: brightness(1.25);
        font-size: calc(27 * var(--u));
    }
    .jjo-btn:hover::after,
    .jjo-btn:focus-visible::after {
        animation: jjo-shine 0.75s ease-in-out forwards;
    }
    @keyframes jjo-shine {
        0% {
            transform: translateX(-120%);
        }
        100% {
            transform: translateX(120%);
        }
    }
    @media (prefers-reduced-motion: reduce) {
        .jjo-btn:hover::after,
        .jjo-btn:focus-visible::after {
            animation: none;
            transform: none;
            opacity: 0.25;
        }
    }
    .jjo-btn:active {
        transform: translateY(2px);
    }
</style>

{#if entering}
    <GameLoading loading label={tr("play.loading.label")} message={i18n.ready ? tr("play.loading.label") : ""} progress={12} minShown={0} handoff="write" />
{/if}
