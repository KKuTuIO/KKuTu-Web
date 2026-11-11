<script>
    // "끄투리오가 처음인가요?" 띠 배너 (초록 → Teal). 버튼은 사이트 공용 .kk-btn 을 그대로 쓴다.
    // 로그인한 사용자에게는 메인 페이지가 `hidden`으로 숨긴다. prerender 결과에는 들어간다.
    import { NEW_CLIENT_URL } from "$lib/clientPref.svelte.js";

    let { onstart, hidden = false } = $props();
</script>

<section class="first-cta text-white" class:hidden aria-labelledby="first-cta-title">
    <div class="mx-auto flex max-w-screen-xl flex-col gap-5 px-4 py-8 lg:flex-row lg:items-center lg:justify-between lg:px-8">
        <div>
            <h2 id="first-cta-title" class="text-2xl font-black lg:text-3xl">끄투리오가 처음인가요?</h2>
            <p class="mt-2 font-bold text-white/90">설치 없이 브라우저에서 바로, 로그인하지 않아도 체험해 볼 수 있는 온라인 끝말잇기예요.</p>
        </div>
        <div class="flex shrink-0 flex-wrap gap-3">
            <a href={NEW_CLIENT_URL} rel="external" onclick={onstart} class="kk-btn inline-flex items-center gap-2 text-(color:--kk-accent-strong)"
                ><span class="material-symbols-outlined" aria-hidden="true">play_arrow</span>지금 시작하기</a
            >
            <a href="/guide" class="kk-btn inline-flex items-center gap-2 border-white/70! bg-transparent! text-white shadow-none! hover:bg-white/15!"
                ><span class="material-symbols-outlined" aria-hidden="true">menu_book</span>게임 소개</a
            >
        </div>
    </div>
</section>

<style>
    /* 사이트 CTA 토큰(초록 → Teal)을 쓰므로 테마·다크 모드를 따라간다. */
    .first-cta {
        background: linear-gradient(100deg, var(--kk-cta-from), var(--kk-cta-to));
        text-shadow: 0 1px 2px rgb(0 0 0 / 0.28);
    }
    .first-cta a {
        text-shadow: none;
    }
</style>
