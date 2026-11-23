<script>
    import { tick } from "svelte";
    import { t } from "../i18n.svelte.js";
    import { reducedMotion } from "../motion.js";
    import Dialog from "./Dialog.svelte";
    import TierBadge from "./TierBadge.svelte";
    import { tierParts } from "../leagueModel.js";

    /**
     * 등급표: 티어마다 올라가는 조건과, 시즌이 끝났을 때의 최종 티어에 따른 보상(골드와 칭호). 표는 서버가 보내 준다
     * (`rewards`: [{id, gold, title, minTrophies, topRatio}], 낮은 티어부터). 트로피 티어는 필요한 트로피, 비율 티어는 상위 몇 %다.
     * 프리시즌에는 보상을 지급하지 않는다. 열리면 목록이 내 등급(`current`)까지 부드럽게 스크롤되고, 내 등급 줄이 강조된다.
     */
    let {
        open = $bindable(false),
        rewards = [],
        preseason = false,
        current = "",
    } = $props();
    let list = $state();
    $effect(() => {
        if (!open || !list) return;
        const box = list;
        // 대화상자가 자리를 잡은 뒤에 스크롤을 시작해서, 위에서부터 내 등급까지 미끄러지는 것이 보이게 한다
        const timer = setTimeout(
            () =>
                tick().then(() => {
                    const row = box.querySelector("[data-current]");
                    if (!row) return;
                    box.scrollTo({
                        top: Math.max(
                            0,
                            row.offsetTop -
                                box.clientHeight / 2 +
                                row.offsetHeight / 2,
                        ),
                        behavior: reducedMotion() ? "auto" : "smooth",
                    });
                }),
            280,
        );
        return () => clearTimeout(timer);
    });
    const rows = $derived([...rewards].reverse());
    const name = (id) => {
        const { group, step } = tierParts(id);
        return group ? `${t(`play.league.tier.${group}`)} ${step}` : id;
    };
    // 비율 등급(크리스탈 1 이상)은 "트로피로 오를 수 있는 가장 높은 등급(크리스탈 2)" 이상인 참가자 가운데 상위 몇 %다.
    // 그 등급의 필요 트로피는 등급표에서 직접 구한다(목록이 높은 등급부터 그려지므로 그리는 순서에 기대지 않는다).
    const crystal2MinTrophies = $derived(
        Math.max(0, ...rewards.filter((r) => r.minTrophies != null).map((r) => r.minTrophies)),
    );
    const condition = (r) => {
        if (r.topRatio != null)
            return t("play.league.tiers.cond.top", {
                p: Math.round(r.topRatio * 100),
                n: crystal2MinTrophies.toLocaleString(),
            });
        if (!r.minTrophies) return t("play.league.tiers.cond.start");
        return t("play.league.tiers.cond.min", {
            n: r.minTrophies.toLocaleString(),
        });
    };
</script>

<Dialog bind:open title={t("play.league.reward.heading")} size="md">
    <div class="flex flex-col gap-3">
        <p class="text-sm font-semibold text-(color:--kk-muted)">
            {t("play.league.reward.desc")}
        </p>
        {#if preseason}<p
                class="rounded-xl bg-(--kk-surface-2) px-3 py-2 text-sm font-extrabold"
            >
                {t("play.league.reward.pre")}
            </p>{/if}
        <ul
            bind:this={list}
            class="relative flex max-h-[min(52vh,26rem)] flex-col gap-1.5 overflow-y-auto pr-1"
        >
            {#each rows as r (r.id)}
                {@const mine = r.id === current}
                <li
                    data-current={mine ? "" : undefined}
                    aria-current={mine ? "true" : undefined}
                    class="flex items-center gap-3 rounded-xl border px-3 py-1.5 {mine
                        ? 'border-(color:--kk-fill) bg-(--kk-accent-soft) shadow-[0_2px_0_var(--kk-fill-edge)]'
                        : 'border-(color:--kk-line)'}"
                >
                    <TierBadge tier={r.id} size={36} />
                    <span class="flex min-w-0 flex-1 flex-col">
                        <span
                            class="flex items-center gap-1.5 truncate font-extrabold"
                        >
                            {name(r.id)}{#if mine}<span
                                    class="shrink-0 rounded-full bg-(--kk-fill) px-2 py-px text-[11px] font-black text-white"
                                >
                                    {t("play.league.tiers.mine")}
                                </span>{/if}
                        </span>
                        <span
                            class="truncate text-xs font-semibold text-(color:--kk-muted)"
                        >
                            {condition(r)}
                        </span>
                    </span>
                    {#if r.title}<span
                            class="shrink-0 rounded-full bg-(--kk-surface-2) px-2.5 py-0.5 text-xs font-black"
                        >
                            {t("play.league.reward.titleOf", {
                                group: t(
                                    `play.league.tier.${r.title.toUpperCase()}`,
                                ),
                            })}
                        </span>{/if}
                    <span class="shrink-0 font-black tabular-nums">
                        {t("play.league.reward.gold", {
                            n: r.gold.toLocaleString(),
                        })}
                    </span>
                </li>
            {/each}
        </ul>
        <p class="text-xs leading-relaxed text-(color:--kk-muted)">
            {t("play.league.tiers.note")}
        </p>
    </div>
</Dialog>
