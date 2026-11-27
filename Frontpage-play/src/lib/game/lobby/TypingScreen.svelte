<script>
    import { onMount, tick as nextTick } from "svelte";
    import Icon from "./Icon.svelte";
    import Dialog from "./Dialog.svelte";
    import RewardChip from "./RewardChip.svelte";
    import ObtainScreen from "./ObtainScreen.svelte";
    import PriceChip from "./PriceChip.svelte";
    import GameIcon from "./GameIcon.svelte";
    import JjoFrame from "./JjoFrame.svelte";
    import ShopQty from "./ShopQty.svelte";
    import TypingChart from "./TypingChart.svelte";
    import { confirm } from "../ui/confirm.svelte.js";
    import { toast } from "../ui/toast.svelte.js";
    import {
        playGameBgm,
        playLobbyBgm,
        playResultBgm,
        playSound,
    } from "../audio.svelte.js";
    import { haptic } from "../haptics.js";
    import { levelUp } from "../levelUp.svelte.js";
    import { t } from "../i18n.svelte.js";
    import {
        ERROR_KEYS,
        UNPAID_KEYS,
        clock,
        isInjected,
        live,
        marks,
    } from "../typing.js";

    /**
     * 타자 연습 (the arcade): the player sets a goal (strokes a minute; the first setting of a day is free, a change after it costs 핑) and types today's
     * text (one a day) line by line in JJo's mouth, as the 타자 대결 shows its words. The server holds the text and the clock and judges every line (this
     * only draws and sends what was typed); a text typed at the goal's speed with enough accuracy pays.
     * @type {{store:any, onback:()=>void}}
     */
    let { store, onback } = $props();

    const typing = $derived(store.net.typing);
    const summary = $derived(typing.summary);
    const session = $derived(typing.session);
    const money = $derived(store.net.users[store.net.id]?.money ?? 0);
    const range = $derived(summary?.goal ?? { min: 100, max: 1200, step: 50 });
    const current = $derived(summary?.current ?? null);
    const cost = $derived(summary?.goalCost ?? 0);

    /** 'pick' (the goal and today's text), 'count' (3, 2, 1), 'play', 'over' */
    let phase = $state("pick");
    /** the goal being chosen (what the account has set, or 200 the first time) until it is changed with the + and − */
    let draft = $state(/** @type {number|null} */ (null));
    const goal = $derived(draft ?? current ?? 200);
    let count = $state(3);
    let typed = $state("");
    let composing = $state(false);
    let waiting = $state(false);
    let now = $state(Date.now());
    let right = $state(0);
    let total = $state(0);
    let points = $state(/** @type {{t:number, v:number}[]} */ ([]));
    let warnOpen = $state(false);
    let helpOpen = $state(false);
    let rewardOpen = $state(false);
    let inputEl = $state(/** @type {HTMLInputElement|null} */ (null));
    let waitTimer;
    let countTimer;
    let endTimer;
    /** what was typed before this key: a key that takes letters away is silent, and text that was not typed is taken back */
    let prevValue = "";
    let composedData = "";

    const lines = $derived(session?.lines ?? []);
    const index = $derived(session?.index ?? 0);
    const over = $derived(phase === "over");
    const elapsed = $derived(
        over
            ? Math.round((typing.last?.result?.seconds ?? 0) * 1000)
            : session
              ? session.elapsed + (now - session.at)
              : 0,
    );
    const stats = $derived(
        over && typing.last?.result
            ? {
                  speed: typing.last.result.speed,
                  accuracy: typing.last.result.accuracy,
                  wrong: typing.last.result.wrong,
              }
            : live({
                  right,
                  total,
                  expected: lines[index] ?? "",
                  typed,
                  elapsed,
                  composing,
              }),
    );
    const result = $derived(over ? typing.last?.result : null);
    const reward = $derived(over ? typing.last?.reward : null);
    const reached = $derived(
        !!result &&
            current !== null &&
            result.speed >= current &&
            result.accuracy >= (summary?.minAccuracy ?? 95),
    );
    const marked = $derived(marks(lines[index] ?? "", typed, composing));
    const mult = $derived(goal / (summary?.reward.reference ?? 200));
    const noTries = $derived(
        !!summary?.reward.pay && (summary?.left ?? 1) <= 0,
    );

    // What the server answered.
    let seen = 0;
    $effect(() => {
        const last = typing.last;
        if (!last || last.at === seen) return;
        seen = last.at;
        waiting = false;
        if (last.error) {
            toast(t(ERROR_KEYS[last.error] ?? ERROR_KEYS.over), {
                kind: "info",
                icon: "keyboard",
            });
            if (last.error !== "same") playSound("fail");
            // the goal could not be set, or the day's tries are used up: the text is called off
            if (
                (phase === "count" || phase === "play") &&
                (last.error === "poor" ||
                    last.error === "same" ||
                    last.error === "cap")
            ) {
                clearInterval(countTimer);
                phase = "pick";
            }
            return;
        }
        if (last.line) {
            total += last.line.strokes;
            right += last.line.strokes - last.line.wrong;
            typed = "";
            prevValue = "";
            if (last.result) {
                // the text is over: the whistle first, then how it went
                phase = "over";
                playSound("horr");
                clearTimeout(endTimer);
                endTimer = setTimeout(
                    () => playSound(reward || reached ? "success" : "timeout"),
                    900,
                );
                playResultBgm();
                if (reward)
                    setTimeout(
                        () => levelUp.whenIdle(() => (rewardOpen = true)),
                        1000,
                    );
            } else {
                playSound(last.line.wrong ? "fail" : "mission");
                nextTick().then(() => inputEl?.focus());
            }
        }
    });

    // the music of a text is 자퀴's (as in the quiz rounds)
    $effect(() => {
        if (phase === "count" || phase === "play") playGameBgm("JaqwiBGM");
    });

    onMount(() => {
        store.typingOpen();
        store.ui.hideFab = true; // the chat button would sit over the input
        try {
            if (!localStorage.getItem("kk-typing-help")) {
                helpOpen = true;
                localStorage.setItem("kk-typing-help", "1");
            }
        } catch {
            /* no storage: the help is only a button then */
        }
        const timer = setInterval(() => {
            now = Date.now();
            // the speed over the time, a point a second
            if (phase === "play" && session) {
                const sec = Math.floor(elapsed / 1000);
                if (sec > 0 && points.at(-1)?.t !== sec)
                    points = [...points, { t: sec, v: stats.speed }];
            }
        }, 250);
        return () => {
            clearInterval(timer);
            clearTimeout(waitTimer);
            clearTimeout(endTimer);
            clearInterval(countTimer);
            store.ui.hideFab = false;
            playLobbyBgm({ fromResult: phase === "over" }); // the lobby's music comes back (crossfaded from the result's if the text had ended)
        };
    });

    // ---- start: a goal that is not the one set yet is set first (and said what it costs), then the count begins ----
    function start() {
        if (!summary?.today || noTries) return;
        if (goal !== current) warnOpen = true;
        else begin();
    }
    function setAndBegin() {
        if (goal !== current && !summary?.goalFree && money < cost) return;
        warnOpen = false;
        store.typingGoal(goal);
        begin();
    }
    function begin() {
        right = 0;
        total = 0;
        points = [];
        typed = "";
        phase = "count";
        count = 3;
        prevValue = "";
        playSound("round_start"); // once, not a beat for each number
        clearInterval(countTimer);
        countTimer = setInterval(async () => {
            count -= 1;
            if (count > 0) return;
            clearInterval(countTimer);
            store.typingStart();
            phase = "play";
            await nextTick();
            inputEl?.focus();
        }, 800);
    }
    async function leave() {
        if (phase === "play" || phase === "count") {
            if (
                !(await confirm({
                    message: t("play.typing.leave.confirm"),
                    ok: t("play.typing.leave.ok"),
                    cancel: t("play.typing.leave.cancel"),
                }))
            )
                return;
            clearInterval(countTimer);
            phase = "pick";
            draft = null;
            playLobbyBgm({ fromResult: false });
            return;
        }
        if (phase === "over") {
            phase = "pick";
            draft = null;
            store.typingOpen();
            playLobbyBgm({ fromResult: true });
            return;
        }
        onback();
    }
    function send() {
        if (phase !== "play" || waiting || !typed.length) return;
        waiting = true;
        haptic(8);
        store.typingSubmit(typed);
        // an error packet (the server was busy) says nothing about the text: do not stay waiting for it
        clearTimeout(waitTimer);
        waitTimer = setTimeout(() => (waiting = false), 6000);
    }
    function onkey(e) {
        if (e.key === "Enter" && !e.isComposing) {
            e.preventDefault();
            send();
        }
    }
    // a key: Al when what is typed is right so far, fail when it is not (a key that only takes letters away is silent)
    let warnedAt = 0;
    function noPaste(e) {
        e?.preventDefault();
        if (Date.now() - warnedAt < 2000) return;
        warnedAt = Date.now();
        toast(t("play.typing.noPaste"), { kind: "info", icon: "keyboard" });
        playSound("fail");
    }
    function onbeforeinput(e) {
        if (isInjected(e, prevValue, prevValue, composedData)) noPaste(e);
    }
    function ontype(e) {
        if (phase !== "play") return;
        // what was not typed (a paste, a drop, a suggestion, several characters at once) is taken back
        if (isInjected(e, prevValue, typed, composedData)) {
            typed = prevValue;
            e.currentTarget.value = prevValue;
            composedData = "";
            noPaste();
            return;
        }
        composedData =
            e.inputType === "insertCompositionText" ? (e.data ?? "") : "";
        const grew = typed.length >= prevValue.length;
        prevValue = typed;
        if (!grew || !typed.length) return;
        playSound(
            marks(lines[index] ?? "", typed, composing).some(
                (m) => m.ok === false,
            )
                ? "fail"
                : "Al",
        );
    }
</script>

{#if reward}
    <ObtainScreen
        bind:open={rewardOpen}
        title={t("play.obtain.acquired")}
        items={[
            { key: "exp", q: reward.exp },
            { key: "ping", q: reward.ping },
        ]}
        shop={store.shop}
        details={{}}
        ribbon
        ambient="shine"
        palette="teal"
    />
{/if}

<Dialog bind:open={warnOpen} title={t("play.typing.confirm.title")}>
    <div class="flex flex-col gap-3">
        <p class="text-base leading-relaxed font-medium">
            {t("play.typing.confirm.head")}
            <b class="font-extrabold"
                >{goal.toLocaleString()}{t("play.typing.unit")}</b
            >{t("play.typing.confirm.tail")}
        </p>
        <p class="text-sm leading-relaxed font-medium text-(color:--kk-muted)">
            {summary?.goalFree
                ? t("play.typing.confirm.after")
                : t("play.typing.confirm.now")}
            <span
                class="inline-flex items-center gap-1 align-middle font-extrabold text-(color:--kk-warn) tabular-nums"
                ><GameIcon name="ping" size={16} />{cost.toLocaleString()}</span
            >{t("play.typing.confirm.need")}
        </p>
        <button
            type="button"
            onclick={setAndBegin}
            disabled={!summary?.goalFree && money < cost}
            class="hw kk-btn kk-btn-primary inline-flex min-h-12 items-center justify-center gap-2.5 px-5 font-extrabold"
        >
            {t("play.typing.confirm.ok")}{#if !summary?.goalFree}<PriceChip
                    price={cost}
                    lacking={money < cost}
                />{/if}
        </button>
        {#if !summary?.goalFree && money < cost}<p
                class="text-center text-xs font-bold text-(color:--kk-muted)"
            >
                {t("play.typing.error.poor")}
            </p>{/if}
    </div>
</Dialog>

<Dialog bind:open={helpOpen} title={t("play.typing.help.title")}>
    <div class="flex flex-col gap-3">
        <p class="text-sm leading-relaxed font-medium">
            {t("play.typing.help.intro")}
        </p>
        <ul
            class="flex list-disc flex-col gap-1 pl-5 text-xs leading-relaxed font-medium text-(color:--kk-muted)"
        >
            <li>{t("play.typing.help.rule.text")}</li>
            <li>
                {t("play.typing.help.rule.goal", {
                    acc: summary?.minAccuracy ?? 95,
                })}
            </li>
            <li>
                {t("play.typing.help.rule.reward", {
                    ref: summary?.reward.reference ?? 200,
                    exp: summary?.reward.exp ?? 50,
                    ping: summary?.reward.ping ?? 5,
                })}
            </li>
            <li>{t("play.typing.help.rule.typo")}</li>
            <li>
                {t("play.typing.help.rule.tries", { n: summary?.perDay ?? 5 })}
            </li>
            <li>{t("play.typing.help.rule.cost", { cost })}</li>
        </ul>
        <button
            type="button"
            onclick={() => (helpOpen = false)}
            class="hw kk-btn kk-btn-primary min-h-11 font-extrabold"
            >{t("play.typing.help.ok")}</button
        >
    </div>
</Dialog>

<div class="flex min-h-0 flex-1 flex-col">
    <div class="flex items-center gap-3 px-3 py-2">
        <button
            type="button"
            aria-label={t("play.typing.back")}
            onclick={leave}
            class="round"><Icon name="arrow_back" size={24} /></button
        >
        <h2 class="title text-xl font-extrabold">{t("play.typing.title")}</h2>
        <button
            type="button"
            aria-label={t("play.typing.help.title")}
            onclick={() => (helpOpen = true)}
            class="round ml-auto"><Icon name="help" size={24} /></button
        >
    </div>

    <div
        class="flex min-h-0 flex-1 flex-col items-center gap-3 overflow-y-auto px-3 pb-3"
    >
        <JjoFrame
            label={t("play.typing.board")}
            reserve={phase === "pick" ? 420 : 300}
            top={phase === "pick" || !lines.length
                ? null
                : {
                      pct: (Math.min(index, lines.length) / lines.length) * 100,
                      text: t("play.typing.progress", {
                          n: Math.min(index + (over ? 0 : 1), lines.length),
                          total: lines.length,
                      }),
                  }}
            bottom={phase === "pick" || !lines.length
                ? null
                : {
                      pct: Math.min(100, (stats.speed / goal) * 100),
                      text: t("play.typing.pace", { speed: stats.speed, goal }),
                      extreme: !over && elapsed > 8000 && stats.speed < goal,
                  }}
        >
            <!-- the text to type cannot be selected or copied (a long press on a phone would offer it) -->
            <div
                class="mouth"
                lang="ko"
                role="presentation"
                oncopy={(e) => e.preventDefault()}
                oncontextmenu={(e) => e.preventDefault()}
            >
                {#if phase === "pick"}
                    <span class="hint">{t("play.typing.today")}</span>
                    <b class="today">{summary?.today?.title ?? "…"}</b>
                    {#if summary?.today}<span class="hint"
                            >{t("play.typing.today.lines", {
                                n: summary.today.lines,
                            })}</span
                        >{/if}
                {:else if phase === "count"}
                    <span class="count" aria-live="polite">{count}</span>
                {:else}
                    {#each lines as line, i (i)}
                        {#if i === index && !over}
                            <div class="cur">
                                {#each marked as m, k (k)}<span
                                        class:wrong={m.ok === false}
                                        class:ok={m.ok === true}>{m.ch}</span
                                    >{/each}
                            </div>
                        {:else if i > index && i < index + 4}
                            <div class="next">{line}</div>
                        {/if}
                    {/each}
                    {#if over}<b class="today">{t("play.typing.done")}</b>{/if}
                {/if}
            </div>
        </JjoFrame>

        {#if phase === "pick"}
            <div class="panel flex w-full max-w-md flex-col gap-3 p-3">
                <div class="flex items-center justify-between gap-2">
                    <b class="text-sm font-extrabold"
                        >{t("play.typing.goal.title")}</b
                    >
                    <ShopQty
                        value={goal}
                        min={range.min}
                        max={range.max}
                        step={range.step}
                        unit={t("play.typing.unit")}
                        name={t("play.typing.goal.title")}
                        onchange={(v) => (draft = v)}
                    />
                </div>
                {#if summary?.reward.pay}
                    <div
                        class="flex items-center justify-between gap-2 text-xs font-bold text-(color:--kk-muted)"
                    >
                        <span
                            >{t("play.typing.leftToday", {
                                n: summary.left,
                            })}</span
                        >
                        <span class="flex items-center gap-1"
                            ><RewardChip
                                reward={{
                                    kind: "xp",
                                    q: Math.round(summary.reward.exp * mult),
                                }}
                                size={28}
                            /><RewardChip
                                reward={{
                                    kind: "ping",
                                    q: Math.round(summary.reward.ping * mult),
                                }}
                                size={28}
                            /></span
                        >
                    </div>
                {/if}
                <button
                    type="button"
                    disabled={!summary?.today || noTries}
                    onclick={start}
                    class="hw kk-btn kk-btn-hero kk-btn-lg w-full"
                    ><span class="kk-btn-icon"
                        ><Icon name="play_arrow" size={28} fill /></span
                    ><span class="kk-btn-label">{t("play.arcade.start")}</span
                    ></button
                >
            </div>
        {:else if phase === "play"}
            <div class="panel flex w-full max-w-md flex-col gap-1 p-3">
                <input
                    bind:this={inputEl}
                    bind:value={typed}
                    oncompositionstart={() => (composing = true)}
                    oncompositionend={() => (composing = false)}
                    oninput={ontype}
                    {onbeforeinput}
                    onpaste={noPaste}
                    ondrop={noPaste}
                    oncontextmenu={(e) => e.preventDefault()}
                    onkeydown={onkey}
                    class="entry"
                    lang="ko"
                    autocomplete="off"
                    autocapitalize="off"
                    spellcheck="false"
                    autocorrect="off"
                    aria-label={t("play.typing.input")}
                    placeholder={t("play.typing.input.empty")}
                    readonly={waiting}
                />
                <div
                    class="flex items-center justify-between text-xs font-bold tabular-nums text-(color:--kk-muted)"
                >
                    <span>{clock(elapsed)}</span>
                    <span
                        >{t("play.typing.stat.accuracy", {
                            acc: stats.accuracy.toFixed(1),
                        })}</span
                    >
                    <span class:typo={stats.wrong > 0}
                        >{t("play.typing.stat.wrong", { n: stats.wrong })}</span
                    >
                </div>
            </div>
        {:else if phase === "over" && result}
            <div class="panel flex w-full max-w-md flex-col gap-3 p-3">
                <b class="text-center text-lg font-extrabold"
                    >{reached
                        ? t("play.typing.result.success", {
                              goal: current ?? 0,
                          })
                        : t("play.typing.result.fail")}</b
                >
                <div>
                    <b class="text-xs font-extrabold text-(color:--kk-muted)"
                        >{t("play.typing.chart.title")}</b
                    >
                    <TypingChart
                        {points}
                        goal={current}
                        label={t("play.typing.chart.title")}
                    />
                </div>
                <p
                    class="flex justify-center gap-4 text-xs font-bold tabular-nums text-(color:--kk-muted)"
                >
                    <span
                        >{t("play.typing.stat.accuracy", {
                            acc: result.accuracy.toFixed(1),
                        })}</span
                    >
                    <span
                        >{t("play.typing.stat.wrong", {
                            n: result.wrong,
                        })}</span
                    >
                </p>
                {#if reward}
                    <div
                        class="flex flex-wrap items-center justify-center gap-1.5"
                    >
                        <RewardChip
                            reward={{ kind: "xp", q: reward.exp }}
                            size={36}
                        /><RewardChip
                            reward={{ kind: "ping", q: reward.ping }}
                            size={36}
                        />
                    </div>
                {:else if typing.last?.unpaid && UNPAID_KEYS[typing.last.unpaid]}
                    <p
                        class="text-center text-sm font-bold text-(color:--kk-muted)"
                    >
                        {t(UNPAID_KEYS[typing.last.unpaid], {
                            goal: current ?? 0,
                            acc: summary?.minAccuracy ?? 95,
                        })}
                    </p>
                {/if}
                <div class="flex gap-2">
                    <button
                        type="button"
                        onclick={leave}
                        class="hw kk-btn min-h-12 flex-1 font-extrabold"
                        >{t("play.typing.stop")}</button
                    >
                    <button
                        type="button"
                        onclick={begin}
                        disabled={noTries}
                        class="hw kk-btn kk-btn-primary min-h-12 flex-1 font-extrabold"
                        >{t("play.typing.again")}</button
                    >
                </div>
            </div>
        {/if}
    </div>
</div>

<style>
    .title {
        color: #fff;
        text-shadow: 0 1px 4px rgb(10 14 50 / 0.65);
    }
    .round {
        display: grid;
        place-items: center;
        flex: none;
        width: 2.75rem;
        height: 2.75rem;
        border-radius: 999px;
        background: rgb(255 255 255 / 0.9);
        color: #334155;
        box-shadow: 0 4px 6px rgb(20 40 80 / 0.2);
        transition:
            transform 0.15s,
            background 0.15s;
    }
    .round:hover {
        transform: scale(1.05);
        background: #fff;
    }
    .panel {
        flex: none;
        border-radius: 20px;
        color: var(--kk-text);
        background: color-mix(in srgb, var(--kk-surface) 94%, transparent);
        box-shadow: 0 3px 6px rgb(20 40 80 / 0.22);
    }
    /* what the frog's mouth shows (as the 타자 대결 does): the line being typed in yellow, the wrong letters struck out in red, and the lines after it in white */
    .mouth {
        user-select: none;
        -webkit-user-select: none;
        -webkit-touch-callout: none;
        display: flex;
        flex-direction: column;
        justify-content: center;
        align-items: center;
        gap: 10px;
        height: 100%;
        padding: 10px 12px;
        text-align: center;
        overflow: hidden;
        color: #fff;
    }
    .mouth .cur {
        color: #ffff44;
        font-size: 26px;
        font-weight: 800;
        line-height: 1.35;
        overflow-wrap: anywhere;
    }
    .mouth .cur .wrong {
        color: #ff5a4d;
        text-decoration: underline;
        text-decoration-thickness: 3px;
    }
    .mouth .next {
        font-size: 17px;
        font-weight: 700;
        line-height: 1.3;
        opacity: 0.85;
        overflow-wrap: anywhere;
    }
    .mouth .today {
        color: #ffff44;
        font-size: 28px;
        font-weight: 800;
    }
    .mouth .hint {
        font-size: 14px;
        font-weight: 700;
        opacity: 0.8;
    }
    .mouth .count {
        color: #ffff44;
        font-size: 96px;
        font-weight: 800;
        line-height: 1;
    }
    .typo {
        color: #d33b3b;
    }
    @media (prefers-color-scheme: dark) {
        :global(:root:not([data-theme="light"])) .typo {
            color: #ff8a8a;
        }
    }
    :global(:root[data-theme="dark"]) .typo {
        color: #ff8a8a;
    }
    .entry {
        width: 100%;
        border: 0;
        border-bottom: 2px solid var(--kk-line-strong);
        background: transparent;
        padding: 0.35rem 0.1rem;
        font-size: 1.15rem;
        font-weight: 700;
        color: var(--kk-text);
        outline: none;
    }
    .entry:focus {
        border-bottom-color: var(--kk-accent);
    }
</style>
