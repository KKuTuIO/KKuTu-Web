<script>
    import { t } from "../i18n.svelte.js";
    import Moremi from "./Moremi.svelte";
    import StatusDot from "./StatusDot.svelte";
    import Icon from "./Icon.svelte";
    import EmptyState from "./EmptyState.svelte";
    import Tooltip from "../ui/Tooltip.svelte";
    import { FRIEND_LIMIT } from "../lobbyModel.js";

    /**
     * The friends tab, laid out like Discord's friend list: filters (온라인 / 전체 / 대기 중), 친구 추가, a search box,
     * and one row per friend with their state and quick actions. The panel is narrow, so the actions are icons.
     */
    let { store, onadd } = $props();
    const net = $derived(store.net);
    const parts = $derived(net.meta.moremiParts);
    const BANNER_KEY = "kkutu-play-friends-banner";

    let filter = $state("online");
    let query = $state("");
    let banner = $state(read());
    function read() {
        try {
            return localStorage.getItem(BANNER_KEY) !== "closed";
        } catch {
            return true;
        }
    }
    function closeBanner() {
        banner = false;
        try {
            localStorage.setItem(BANNER_KEY, "closed");
        } catch {
            /* private mode */
        }
    }

    const pending = $derived(store.pendingFriends);
    const waiting = $derived(pending.incoming.length + pending.outgoing.length);
    const q = $derived(query.trim().toLowerCase());
    const match = (name) => !q || String(name).toLowerCase().includes(q);
    const friends = $derived(store.friends.filter((f) => match(f.name)));
    const rows = $derived(
        filter === "online"
            ? friends.filter((f) => f.online)
            : filter === "best"
              ? friends.filter((f) => f.bf)
              : friends,
    );
    const incoming = $derived(pending.incoming.filter((r) => match(r.name)));
    const outgoing = $derived(pending.outgoing.filter((r) => match(r.name)));
    const count = $derived(
        filter === "pending" ? incoming.length + outgoing.length : rows.length,
    );
    const filters = $derived([
        { id: "online", label: t("play.friends.filter.online") },
        { id: "best", label: t("play.friends.filter.best") },
        { id: "all", label: t("play.friends.filter.all") },
        {
            id: "pending",
            label: t("play.friends.filter.pending"),
            badge: pending.incoming.length,
        },
    ]);
    const where = (f) =>
        !f.online
            ? t("play.friend.offline")
            : f.status !== "online"
              ? t(`play.status.${f.status}`)
              : f.place
              ? t("play.friend.inGame", { room: f.place })
              : f.server === store.server
                ? t("play.friend.lobby")
                : t("play.friend.channel", { channel: f.server });
    // "차단한 회원은 {link}에서 확인할 수 있어요.": the link sits inside the sentence.
    const bannerParts = $derived(t("play.friends.banner").split("{link}"));
    const full = $derived(store.friends.length >= FRIEND_LIMIT);
    const whisper = (f) => store.fillChat(`/e ${f.name.replace(/\s/g, "")} `);
</script>

<div class="flex min-h-0 flex-1 flex-col">
    <div
        class="flex items-center gap-1.5 border-b border-(color:--kk-line) px-2 py-2"
    >
        <div
            class="flex min-w-0 flex-1 flex-wrap items-center gap-1"
            role="group"
            aria-label={t("play.friends.filters")}
        >
            {#each filters as f (f.id)}
                <button
                    type="button"
                    aria-pressed={filter === f.id}
                    onclick={() => (filter = f.id)}
                    class="inline-flex min-h-9 items-center gap-1 rounded-lg px-2 text-sm font-bold whitespace-nowrap transition-colors {filter ===
                    f.id
                        ? 'bg-(--kk-surface-2) text-(color:--kk-text)'
                        : 'text-(color:--kk-muted) hover:bg-(--kk-surface-2)'}"
                >
                    {f.label}{#if f.badge}<span
                            class="inline-grid h-[18px] min-w-[18px] place-items-center rounded-full bg-(--kk-danger) px-1 text-[11px] leading-none font-extrabold text-white"
                            >{f.badge}</span
                        >{/if}
                </button>
            {/each}
        </div>
    </div>

    <div class="min-h-0 flex-1 overflow-y-auto p-2">
        {#if banner}
            <div
                class="mb-2 flex items-start gap-2 rounded-xl bg-(--kk-surface-2) p-2.5 text-xs"
            >
                <Icon
                    name="info"
                    size={18}
                    fill
                    class="mt-px shrink-0 text-(color:--kk-muted)"
                />
                <p class="min-w-0 flex-1 leading-snug">
                    {bannerParts[0]}<button
                        type="button"
                        class="font-bold text-(color:--kk-accent-strong) underline underline-offset-2 hover:brightness-90"
                        onclick={() => store.hooks.openSettings?.("block")}
                        >{t("play.friends.bannerLink")}</button
                    >{bannerParts[1] ?? ""}
                </p>
                <button
                    type="button"
                    aria-label={t("play.dialog.close")}
                    onclick={closeBanner}
                    class="grid size-6 shrink-0 place-items-center rounded-md text-(color:--kk-muted) hover:bg-(--kk-surface)"
                    ><Icon name="close" size={16} /></button
                >
            </div>
        {/if}

        <label class="kk-input mb-2 flex h-10 items-center gap-2 px-2.5">
            <Icon
                name="search"
                size={20}
                class="shrink-0 text-(color:--kk-muted)"
            />
            <input
                bind:value={query}
                aria-label={t("play.friends.search")}
                placeholder={t("play.friends.search")}
                autocomplete="off"
                class="min-w-0 flex-1 bg-transparent text-sm outline-none"
            />
        </label>

        <h3 class="px-1 pb-1 text-xs font-bold text-(color:--kk-muted)">
            {t(`play.friends.heading.${filter}`, { n: count })}
        </h3>

        <ul class="flex flex-col">
            {#if filter === "pending"}
                {#each incoming as r (r.id)}
                    <li
                        class="flex min-h-14 items-center gap-2.5 border-t border-(color:--kk-line) px-1 py-1.5"
                    >
                        <button
                            type="button"
                            onclick={() => store.hooks.openProfile?.(r.id)}
                            aria-label="{t('play.profile.windowTitle')}: {r.name}"
                            class="flex min-w-0 flex-1 items-center gap-2.5 rounded-lg text-left hover:bg-(--kk-surface-2)"
                        >
                        <Moremi
                            equip={r.user?.equip}
                            shop={store.shop}
                            {parts}
                            size={38}
                            label={r.name}
                            frame={false}
                        />
                        <span class="min-w-0 flex-1 leading-tight"
                            ><span class="block truncate font-extrabold"
                                >{r.name}</span
                            ><span class="text-xs text-(color:--kk-muted)"
                                >{t("play.friend.pendingFrom")}</span
                            ></span
                        >
                        </button>
                        <Tooltip text={t("play.friend.deny")}
                            ><button
                                type="button"
                                aria-label="{t('play.friend.deny')}: {r.name}"
                                onclick={() =>
                                    store.answerFriendRequest(r.id, false)}
                                class="grid size-9 place-items-center rounded-full bg-(--kk-surface-2) text-(color:--kk-muted) hover:text-(color:--kk-danger)"
                                ><Icon name="close" size={20} /></button
                            ></Tooltip
                        >
                        <Tooltip text={t("play.friend.accept")}
                            ><button
                                type="button"
                                aria-label="{t('play.friend.accept')}: {r.name}"
                                onclick={() =>
                                    store.answerFriendRequest(r.id, true)}
                                class="grid size-9 place-items-center rounded-full bg-(--kk-fill) text-white"
                                ><Icon name="check" size={20} /></button
                            ></Tooltip
                        >
                    </li>
                {/each}
                {#each outgoing as r (r.id)}
                    <li
                        class="flex min-h-14 items-center gap-2.5 border-t border-(color:--kk-line) px-1 py-1.5 opacity-80"
                    >
                        <button
                            type="button"
                            onclick={() => store.hooks.openProfile?.(r.id)}
                            aria-label="{t('play.profile.windowTitle')}: {r.name}"
                            class="flex min-w-0 flex-1 items-center gap-2.5 rounded-lg text-left hover:bg-(--kk-surface-2)"
                        >
                        <Moremi
                            equip={r.user?.equip}
                            shop={store.shop}
                            {parts}
                            size={38}
                            label={r.name}
                            frame={false}
                        />
                        <span class="min-w-0 flex-1 leading-tight"
                            ><span class="block truncate font-extrabold"
                                >{r.name}</span
                            ><span class="text-xs text-(color:--kk-muted)"
                                >{t("play.friend.pendingTo")}</span
                            ></span
                        >
                        </button>
                        <Icon
                            name="hourglass_top"
                            size={20}
                            class="mr-1.5 text-(color:--kk-muted)"
                        />
                    </li>
                {/each}
            {:else}
                {#each rows as f (f.id)}
                    <li
                        class="group flex min-h-14 items-center gap-1 border-t border-(color:--kk-line) hover:bg-(--kk-surface-2)"
                    >
                        <!-- The row opens their profile; the icons are the two things you do most (the ⋮ opens the same menu as everywhere else). -->
                        <button
                            type="button"
                            onclick={() => store.hooks.openProfile?.(f.id)}
                            class="flex min-w-0 flex-1 items-center gap-2.5 rounded-lg px-1 py-1.5 text-left {f.online
                                ? ''
                                : 'opacity-55'}"
                        >
                            <span class="relative shrink-0">
                                <Moremi
                                    equip={f.user?.equip}
                                    shop={store.shop}
                                    {parts}
                                    size={38}
                                    label={f.name}
                                    frame={false}
                                />
                                <!-- The dot's stroke is the panel's own colour, so it cuts the character out instead of sitting on top of it. -->
                                <span
                                    class="absolute -right-1 -bottom-1 grid size-[18px] place-items-center rounded-full bg-(--kk-surface) transition-colors group-hover:bg-(--kk-surface-2)"
                                    aria-hidden="true"
                                    ><StatusDot status={f.status} size={12} /></span>
                            </span>
                            <span class="min-w-0 leading-tight">
                                <span
                                    class="flex items-center gap-1 font-extrabold"
                                    ><Tooltip text={f.name}><span class="truncate">{f.name}</span></Tooltip
                                    >{#if f.bf}<Icon
                                            name="favorite"
                                            size={14}
                                            fill
                                            class="shrink-0 text-(color:--kk-danger)"
                                        />{/if}</span
                                >
                                <span
                                    class="block truncate text-xs text-(color:--kk-muted)"
                                    >{where(f)}</span
                                >
                            </span>
                        </button>
                        {#if f.online}<Tooltip text={t("play.profile.whisper")}
                                ><button
                                    type="button"
                                    aria-label="{t(
                                        'play.profile.whisper',
                                    )}: {f.name}"
                                    onclick={() => whisper(f)}
                                    class="grid size-9 shrink-0 place-items-center rounded-full bg-(--kk-surface-2) text-(color:--kk-muted) hover:text-(color:--kk-text) group-hover:bg-(--kk-surface)"
                                    ><Icon
                                        name="chat_bubble"
                                        size={18}
                                        fill
                                    /></button
                                ></Tooltip
                            >{/if}
                        <Tooltip text={t("play.friends.more")}
                            ><button
                                type="button"
                                aria-label="{t('play.friends.more')}: {f.name}"
                                onclick={(e) =>
                                    store.hooks.openUserMenu?.(
                                        f.id,
                                        e,
                                        "userList",
                                    )}
                                class="grid size-9 shrink-0 place-items-center rounded-full text-(color:--kk-muted) hover:bg-(--kk-surface-2) hover:text-(color:--kk-text)"
                                ><Icon name="more_vert" size={20} /></button
                            ></Tooltip
                        >
                    </li>
                {/each}
            {/if}
        </ul>

        {#if !count}
            <EmptyState
                compact
                size={110}
                title={q
                    ? t("play.friends.empty.search")
                    : t(`play.friends.empty.${filter}`)}
            />
        {/if}
    </div>

    <!-- Adding friends lives at the bottom, where the thumb is; the limit the server enforces is in the tab's label. -->
    <div class="flex items-center gap-2 border-t border-(color:--kk-line) p-2">
        <button
            type="button"
            onclick={onadd}
            class="kk-btn kk-btn-primary inline-flex min-h-11 min-w-0 flex-1 items-center justify-center gap-1.5 text-sm"
            disabled={full}
            ><Icon name="person_add" size={20} fill />{full
                ? t("play.friends.full")
                : t("play.friend.add")}</button
        >
    </div>
</div>
