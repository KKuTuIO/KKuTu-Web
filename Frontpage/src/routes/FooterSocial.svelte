<script>
    // 푸터 맨 위의 소셜 띠. 평소엔 중립색, hover/focus 하면 각 서비스의 브랜드색으로 채워진다.
    // 색상: Discord #5865F2(Blurple) · YouTube #FF0000 · 네이버 그린 #03C75A(네이버 브랜드 가이드) · 리오위키 #3553A0(자체 지정).
    import Fa from "svelte-fa";
    import { faDiscord, faYoutube } from "@fortawesome/free-brands-svg-icons";

    const links = [
        { label: "공식카페", href: "https://cafe.naver.com/kkutuio", color: "#03C75A", kind: "cafe" },
        { label: "Discord", href: "https://discord.gg/kkutuio-395143193114705920", color: "#5865F2", kind: "fa", icon: faDiscord },
        { label: "YouTube", href: "https://youtube.com/@KKuTuIO", color: "#FF0000", kind: "fa", icon: faYoutube },
        { label: "리오위키", href: "https://kkutu.wiki", color: "#3553A0", kind: "wiki" },
    ];
</script>

<nav class="border-b border-(color:--kk-line) bg-(--kk-surface-2)" aria-label="끄투리오 공식 채널">
    <ul class="mx-auto flex max-w-screen-xl items-center justify-center gap-3 px-4 py-5 lg:px-8">
        {#each links as l (l.label)}
            <li>
                <a
                    href={l.href}
                    target="_blank"
                    rel="noopener"
                    aria-label={l.label}
                    title={l.label}
                    style:--c={l.color}
                    class="group grid size-12 place-items-center rounded-full border border-(color:--kk-line) bg-(--kk-surface) text-xl text-(color:--kk-muted) transition hover:-translate-y-0.5 hover:border-transparent hover:bg-(--c) hover:text-white focus-visible:border-transparent focus-visible:bg-(--c) focus-visible:text-white"
                >
                    {#if l.kind === "fa"}
                        <Fa icon={l.icon} />
                    {:else if l.kind === "cafe"}
                        <!-- 네이버 카페 로고에서 초록 원을 뺀 컵 모양. 원은 버튼 배경(hover 시 #03C75A)이 대신한다. -->
                        <svg class="size-7" viewBox="0 0 32 32" fill="currentColor" aria-hidden="true">
                            <path fill-rule="evenodd" clip-rule="evenodd" d="M16.16 12.025H11.68c.12-2.476 2.182-4.447 4.708-4.447h4.482c-.12 2.477-2.182 4.447-4.709 4.447z" />
                            <path d="M8.211 14.545c0-.483.393-.876.876-.876h12.439c.484 0 .876.393.876.876V18.4a7.095 7.095 0 11-14.19 0v-3.854z" />
                            <path fill="none" stroke="currentColor" stroke-width="1.263" d="M21.852 14.3h1.34a2.916 2.916 0 010 5.833h-1.34V14.3z" />
                        </svg>
                    {:else}
                        <!-- 다크 모드용 흰색 로고. 밝은 배경에서는 muted 톤으로 눌러 두고 hover 때 원래 흰색으로. -->
                        <img
                            src="https://cdn.kkutu.io/img/bi/bi_white.svg"
                            alt=""
                            aria-hidden="true"
                            class="h-5 w-auto [filter:brightness(0)_opacity(.5)] transition group-hover:[filter:none] group-focus-visible:[filter:none] dark:[filter:opacity(.7)]"
                        />
                    {/if}
                </a>
            </li>
        {/each}
    </ul>
</nav>
