<script>
    import "katex/dist/katex.min.css";
    import { onMount, tick as nextTick } from "svelte";
    import Icon from "./Icon.svelte";
    import Dialog from "./Dialog.svelte";
    import RewardChip from "./RewardChip.svelte";
    import ObtainScreen from "./ObtainScreen.svelte";
    import JjoFrame from "./JjoFrame.svelte";
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
    import { REMI } from "../remi.js";
    import {
        CIRCLED,
        ERROR_KEYS,
        LEVEL_KEYS,
        clock,
        formulaHtml,
        mathHtml,
        mathHtmlPts,
        numberHtml,
    } from "../mathText.js";

    /**
     * 수학 연습 (the arcade): the player picks a level and gets a set of questions, as in a mock exam. One question is on the screen at a time: it is
     * answered and graded at once (the answer and the explanation are shown), then the next. The server draws the set, keeps the answers and grades each
     * question (this only draws the question and sends what was chosen or typed); a set that gets enough right pays, in proportion to its points.
     * @type {{store:any, onback:()=>void}}
     */
    let { store, onback } = $props();

    const math = $derived(store.net.math);
    const summary = $derived(math.summary);
    const session = $derived(math.session);
    const result = $derived(math.result);
    const questions = $derived(session?.questions ?? []);
    const checked = $derived(session?.checked ?? []);
    const playing = $derived(session?.status === "playing");
    const over = $derived(session?.status === "over" && !!result);
    const reward = $derived(over ? math.last?.reward : null);
    const levels = $derived(summary?.levels ?? []);
    const cur = $derived(levels.find((l) => l.id === session?.level) ?? null);
    const minCorrect = $derived(cur?.minCorrect ?? 0);
    const pays = $derived(!!summary?.pay);
    const noTries = $derived(pays && (summary?.left ?? 1) <= 0);

    /** the question on the screen */
    let idx = $state(0);
    /** what is chosen (a number, 0 for none) or typed (text) for it, until it is graded */
    let answer = $state(/** @type {number|string} */ (0));
    let now = $state(Date.now());
    let helpOpen = $state(false);
    let rewardOpen = $state(false);
    /** a question has been sent and the grading has not come back */
    let waiting = $state(false);
    /** the result is shown (a set whose last question was just graded waits for the player to ask for it) */
    let showResult = $state(false);
    let wasPlaying = null; // null until a set is seen: one that was already over when the screen opened is not "ended" now
    let lastFinish = 0;
    let waitTimer;
    let endTimer;
    let inputEl = $state(/** @type {HTMLInputElement|null} */ (null));
    let scroller = $state(/** @type {HTMLElement|null} */ (null));
    /** the level whose card is open (one at a time) */
    let openLevel = $state(/** @type {string|null} */ (null));
    const q = $derived(questions[idx] ?? null);
    const mark = $derived(checked[idx] ?? null);
    const left = $derived(
        session ? Math.max(0, session.left - (now - session.at)) : 0,
    );
    const last = $derived(idx >= questions.length - 1);
    const checkedCount = $derived(checked.filter(Boolean).length);
    /** the frog's bars, as the other board games have them: how far the set is, and how much of the time is left */
    const topBar = $derived(
        session && !(over && showResult)
            ? {
                  pct: (checkedCount / Math.max(1, questions.length)) * 100,
                  text: t("play.math.progress", {
                      n: Math.min(idx + 1, questions.length),
                      total: questions.length,
                  }),
              }
            : null,
    );
    const bottomBar = $derived(
        session && playing
            ? {
                  pct: cur ? Math.min(100, (left / (cur.time * 1000)) * 100) : 0,
                  text: clock(left),
                  extreme: left <= 60_000,
              }
            : null,
    );
    const blank = $derived(
        typeof answer === "string" ? answer.trim() === "" : answer === 0,
    );

    // What the server answered.
    let seen = 0;
    $effect(() => {
        const l = math.last;
        if (!l || l.at === seen) return;
        seen = l.at;
        waiting = false;
        if (l.error) {
            toast(t(ERROR_KEYS[l.error] ?? ERROR_KEYS.over), {
                kind: "info",
                icon: "calculate",
            });
            if (l.error !== "none") playSound("fail");
        }
    });
    // the set begins (a clean first question, or the first one that is not graded yet when it was picked up again), is graded question by question, and ends
    $effect(() => {
        const isPlaying = session ? session.status === "playing" : null;
        if (isPlaying === true && wasPlaying !== true) {
            const first = checked.findIndex((c) => !c);
            idx = first < 0 ? questions.length - 1 : first;
            answer = q?.kind === "input" ? "" : 0;
            showResult = false;
        }
        if (wasPlaying === true && isPlaying === false && result) show(); // the set ended (the player asked for the result, or the time was up)
        wasPlaying = isPlaying;
    });
    // each grading is heard
    let heard = 0;
    $effect(() => {
        const n = checked.filter(Boolean).length;
        if (n === heard) return;
        const fresh = n > heard;
        heard = n;
        if (!fresh || !mark) return;
        playSound(mark.right ? "mission" : "fail");
        haptic(mark.right ? 10 : 20);
    });
    // the music of a set is 자퀴's (as in the quiz rounds)
    $effect(() => {
        if (playing) playGameBgm("JaqwiBGM");
    });

    /** the question is set as large as the mouth lets it be, and smaller (down to 11 px) until it fits; one that is still longer scrolls */
    function fit(node, key) {
        const run = () => {
            let size = 18;
            node.style.fontSize = `${size}px`;
            while (size > 11 && node.scrollHeight > node.clientHeight + 1) {
                size -= 1;
                node.style.fontSize = `${size}px`;
            }
        };
        run();
        document.fonts?.ready?.then(run);
        return { update: () => queueMicrotask(run) };
    }

    // a new screen (the list, a question, the result) starts at its top, not where the one before was scrolled to
    $effect(() => {
        void [session?.level, idx, showResult, !!session];
        scroller?.scrollTo(0, 0);
    });
    // a question that was just graded: the answer and its explanation, and the button for the next, are brought into view
    $effect(() => {
        if (!mark) return;
        // after the red pen has been drawn on the number (it takes about half a second), so that it is seen
        const timer = setTimeout(
            () => scroller?.scrollTo({ top: scroller.scrollHeight, behavior: "smooth" }),
            900,
        );
        return () => clearTimeout(timer);
    });

    function show() {
        if (showResult) return;
        showResult = true;
        // the whistle first, then how it went
        playSound("horr");
        clearTimeout(endTimer);
        endTimer = setTimeout(
            () =>
                playSound(
                    reward || (result && result.correct >= minCorrect)
                        ? "success"
                        : "timeout",
                ),
            900,
        );
        playResultBgm();
        if (reward)
            setTimeout(() => levelUp.whenIdle(() => (rewardOpen = true)), 1000);
    }

    onMount(() => {
        store.mathOpen(true);
        store.ui.hideFab = true;
        try {
            if (!localStorage.getItem("kk-math-help")) {
                helpOpen = true;
                localStorage.setItem("kk-math-help", "1");
            }
        } catch {
            /* no storage: the help is only a button then */
        }
        const tick = setInterval(() => {
            now = Date.now();
            // the time is up: the set is ended (again every two seconds until the server has closed it)
            if (playing && left === 0 && now - lastFinish > 2000) {
                lastFinish = now;
                store.mathFinish();
            }
        }, 250);
        return () => {
            clearInterval(tick);
            clearTimeout(waitTimer);
            clearTimeout(endTimer);
            store.ui.hideFab = false;
            playLobbyBgm({ fromResult: over });
        };
    });

    function start(level) {
        if (noTries) return;
        showResult = false;
        store.mathStart(level);
    }
    function choose(n) {
        if (!playing || mark || waiting) return;
        answer = answer === n ? 0 : n;
        playSound("Al");
        haptic(6);
    }
    /** a number is typed: only what a number is made of is taken (a minus sign, digits, a point) */
    function type(e) {
        const v = e.currentTarget.value.replace(/[^0-9.\-−]/g, "").slice(0, 12);
        e.currentTarget.value = v;
        answer = v;
    }
    async function grade() {
        if (!playing || mark || waiting) return;
        if (
            blank &&
            !(await confirm({
                message: t("play.math.blank.confirm"),
                ok: t("play.math.blank.ok"),
                cancel: t("play.math.blank.cancel"),
            }))
        )
            return;
        waiting = true;
        store.mathCheck(idx, answer);
        // an error packet (the server was busy) says nothing about the question: do not stay waiting for it
        clearTimeout(waitTimer);
        waitTimer = setTimeout(() => (waiting = false), 6000);
    }
    async function next() {
        if (!mark) return;
        if (last) {
            // the last question was graded: the set is ended (what the player has not done is nothing), and its result comes
            if (waiting) return;
            waiting = true;
            store.mathFinish();
            clearTimeout(waitTimer);
            waitTimer = setTimeout(() => (waiting = false), 6000);
            return;
        }
        idx += 1;
        answer = questions[idx]?.kind === "input" ? "" : 0;
        await nextTick();
        inputEl?.focus();
    }
    async function leave() {
        if (playing) {
            if (
                !(await confirm({
                    message: t("play.math.leave.confirm"),
                    ok: t("play.math.leave.ok"),
                    cancel: t("play.math.leave.cancel"),
                }))
            )
                return;
            // the set goes on on the server (the time runs; coming back picks it up again) and the try it took stays taken
            store.net.math.session = null;
            store.net.math.result = null;
            playLobbyBgm({ fromResult: false });
            onback();
            return;
        }
        if (over) {
            store.net.math.session = null;
            store.net.math.result = null;
            showResult = false;
            store.mathOpen();
            playLobbyBgm({ fromResult: true });
            return;
        }
        onback();
    }
    function again() {
        const level = session?.level;
        store.net.math.session = null;
        store.net.math.result = null;
        showResult = false;
        if (level) start(level);
    }
    function onkey(e) {
        if (e.key !== "Enter" || e.isComposing) return;
        const el = /** @type {HTMLElement|null} */ (e.target);
        if (el?.tagName === "BUTTON") return;
        if (helpOpen || !playing) return;
        e.preventDefault();
        if (mark) next();
        else grade();
    }
</script>

<svelte:window onkeydown={onkey} />

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

<Dialog bind:open={helpOpen} title={t("play.math.help.title")}>
    <div class="flex flex-col gap-3">
        <p class="text-sm leading-relaxed font-medium">
            {t("play.math.help.intro")}
        </p>
        <ul
            class="flex list-disc flex-col gap-1 pl-5 text-xs leading-relaxed font-medium text-(color:--kk-muted)"
        >
            <li>{t("play.math.help.rule.each")}</li>
            <li>{t("play.math.help.rule.pass")}</li>
            <li>{t("play.math.help.rule.points")}</li>
            <li>{t("play.math.help.rule.input")}</li>
            <li>
                {t("play.math.help.rule.tries", { n: summary?.perDay ?? 5 })}
            </li>
            <li>{t("play.math.help.rule.leave")}</li>
            <li>{t("play.math.help.rule.pool")}</li>
        </ul>
        <button
            type="button"
            onclick={() => (helpOpen = false)}
            class="hw kk-btn kk-btn-primary min-h-11 font-extrabold"
            >{t("play.math.help.ok")}</button
        >
    </div>
</Dialog>

<div class="flex min-h-0 flex-1 flex-col">
    <div class="flex items-center gap-3 px-3 py-2">
        <button
            type="button"
            aria-label={t("play.math.back")}
            onclick={leave}
            class="round"><Icon name="arrow_back" size={24} /></button
        >
        <h2 class="title text-xl font-extrabold">{t("play.math.title")}</h2>
        <button
            type="button"
            aria-label={t("play.math.help.title")}
            onclick={() => (helpOpen = true)}
            class="round ml-auto"><Icon name="help" size={24} /></button
        >
    </div>

    <div
        bind:this={scroller}
        class="flex min-h-0 flex-1 flex-col items-center gap-3 overflow-y-auto px-3 pb-3"
    >
        {#if !session}
            <div class="panel flex w-full max-w-md flex-col gap-3 p-3">
                <div class="flex items-center justify-between gap-2">
                    <b class="text-sm font-extrabold"
                        >{t("play.math.level.title")}</b
                    >
                    {#if pays}<span
                            class="text-xs font-bold text-(color:--kk-muted)"
                            >{t("play.math.leftToday", {
                                n: summary?.left ?? 0,
                            })}</span
                        >{/if}
                </div>
                <ul class="flex flex-col gap-2">
                    {#each levels as l (l.id)}
                        {@const full = l.slots.reduce((a, b) => a + b, 0)}
                        {@const on = openLevel === l.id}
                        <li class="row {on ? 'row-on' : ''}">
                            <button
                                type="button"
                                class="head"
                                aria-expanded={on}
                                onclick={() => (openLevel = on ? null : l.id)}
                            >
                                <span class="thumb"
                                    ><img src={REMI[l.id]} alt="" /></span
                                >
                                <span class="flex min-w-0 flex-1 flex-col gap-1">
                                    <b class="text-base font-extrabold"
                                        >{t(LEVEL_KEYS[l.id])}</b
                                    >
                                    {#if pays}
                                        <span class="flex items-center gap-1.5">
                                            <RewardChip
                                                reward={{
                                                    kind: "xp",
                                                    q: Math.round(l.expPerPoint * full),
                                                }}
                                                size={24}
                                            /><RewardChip
                                                reward={{
                                                    kind: "ping",
                                                    q: Math.round(l.pingPerPoint * full),
                                                }}
                                                size={24}
                                            />
                                        </span>
                                    {/if}
                                </span>
                                <Icon
                                    name="expand_more"
                                    size={24}
                                    class="chev shrink-0 text-(color:--kk-muted) transition-transform {on ? 'rotate-180' : ''}"
                                />
                            </button>
                            <div class="acc" class:acc-open={on} inert={!on}>
                                <div class="min-h-0 overflow-hidden">
                                    <div class="flex flex-col gap-3 px-3 pb-3 pt-1">
                                        <div class="meta">
                                            <div class="line">
                                                <span class="chip">{t("play.math.level.range")}</span>
                                                <span class="val">{t(`${LEVEL_KEYS[l.id]}.desc`)}</span>
                                            </div>
                                            <div class="line">
                                                <span class="chip">{t("play.math.level.pass")}</span>
                                                <span class="val"
                                                    >{t("play.math.level.info", {
                                                        n: l.slots.length,
                                                        min: Math.round(l.time / 60),
                                                        pass: l.minCorrect,
                                                    })}</span
                                                >
                                            </div>
                                        </div>
                                        <button
                                            type="button"
                                            disabled={noTries}
                                            onclick={() => start(l.id)}
                                            class="hw kk-btn kk-btn-hero kk-btn-lg w-full"
                                            ><span class="kk-btn-label"
                                                >{t("play.arcade.start")}</span
                                            ></button
                                        >
                                    </div>
                                </div>
                            </div>
                        </li>
                    {/each}
                </ul>
                {#if pays}<p
                        class="text-center text-xs font-medium text-(color:--kk-muted)"
                    >
                        {t("play.math.fullReward")}
                    </p>{/if}
            </div>
        {:else if over && showResult}
            <div
                class="panel flex w-full max-w-md flex-col items-start gap-3 p-4"
            >
                <div class="crop">
                    <img src={REMI[session.level]} alt="" />
                </div>
                <b class="text-lg font-extrabold"
                    >{t(
                        reward || result.correct >= minCorrect
                            ? "play.math.result.success"
                            : "play.math.result.fail",
                        { n: result.correct, points: result.points },
                    )}</b
                >
                {#if reward}
                    <div class="rewards">
                        <span class="label">{t("play.arcade.reward")}</span>
                        <RewardChip
                            reward={{ kind: "xp", q: reward.exp }}
                            size={34}
                        /><RewardChip
                            reward={{ kind: "ping", q: reward.ping }}
                            size={34}
                        />
                    </div>
                {:else if math.last?.unpaid === "few"}
                    <p
                        class="text-sm font-bold text-(color:--kk-muted)"
                    >
                        {t("play.math.unpaid.few", { min: minCorrect })}
                    </p>
                {/if}
            </div>


            <div class="panel flex w-full max-w-md flex-col gap-1.5 p-3">
                <b class="text-sm font-extrabold"
                    >{t("play.math.review.title")}</b
                >
                {#each questions as rq, i (i)}
                    {@const m = result.marks[i]}
                    <details class="review">
                        <summary>
                            <span class="qno">{@html numberHtml(i + 1)}</span>
                            <span class="mark {m.right ? 'right' : 'wrong'}"
                                ><Icon name={m.right ? "check_circle" : "cancel"} size={20} fill />{m.right
                                    ? t("play.math.right")
                                    : t("play.math.wrong")}</span
                            >
                        </summary>
                        <div class="stem">
                            <span class="qno">{@html numberHtml(i + 1)}</span
                            >{@html mathHtmlPts(rq.stem, rq.points)}
                        </div>
                        <div class="answerline">
                            <span
                                >{t("play.math.mine", {
                                    v:
                                        m.mine === "" || m.mine === 0
                                            ? t("play.math.none")
                                            : rq.kind === "choice"
                                              ? CIRCLED[m.mine - 1]
                                              : m.mine,
                                })}</span
                            >
                            <span class="right"
                                >{t("play.math.correctAnswer", {
                                    v:
                                        rq.kind === "choice"
                                            ? CIRCLED[m.answer - 1]
                                            : m.answer,
                                })}</span
                            >
                        </div>
                        {#if result.explanations[i]}
                            <div class="why">
                                <b>{t("play.math.explanation")}</b>
                                {@html mathHtml(result.explanations[i])}
                            </div>
                        {/if}
                    </details>
                {/each}
                <div class="mt-1 flex gap-2">
                    <button
                        type="button"
                        onclick={leave}
                        class="hw kk-btn min-h-12 flex-1 font-extrabold"
                        >{t("play.math.stop")}</button
                    >
                    <button
                        type="button"
                        onclick={again}
                        disabled={noTries}
                        class="hw kk-btn kk-btn-primary min-h-12 flex-1 font-extrabold"
                        >{t("play.math.again")}</button
                    >
                </div>
            </div>
        {:else if q}
            <!-- the question is in the frog's mouth: the yellow bar is how far the set is, the blue one the time that is left -->
            <JjoFrame
                label={t("play.math.board")}
                reserve={0}
                top={topBar}
                bottom={bottomBar}
            >
                <div class="mouth" lang="ko" use:fit={idx + q.stem}>
                    <div class="stemwrap">
                        <span class="qno marked"
                            >{@html numberHtml(idx + 1)}{#if mark}
                                <!-- the red pen, small, over the number: a circle round a right answer, a stroke through a wrong one -->
                                <svg
                                    class="pen"
                                    viewBox="0 0 100 60"
                                    preserveAspectRatio="none"
                                    aria-hidden="true"
                                >
                                    {#if mark.right}
                                        <path class="stroke" pathLength="1" d="M22 7 C62 -3 99 14 96 32 C93 52 48 60 20 54 C1 48 0 22 26 10 C37 5 50 5 60 8" />
                                    {:else}
                                        <path class="stroke" pathLength="1" d="M6 56 C32 36 64 18 96 4" />
                                        <path class="stroke s2" pathLength="1" d="M14 58 C38 42 66 26 92 12" />
                                    {/if}
                                </svg>
                            {/if}</span
                        >{@html mathHtmlPts(q.stem, q.points)}
                    </div>
                </div>
            </JjoFrame>

            <div class="panel flex w-full max-w-md flex-col gap-3 p-3">
                {#if q.kind === "input"}
                    {#if mark}
                        <div
                            class="flex items-center justify-between gap-2 rounded-xl border-[1.5px] px-3 py-2 text-sm font-extrabold tabular-nums {mark.right
                                ? 'border-[#3f8a4a]'
                                : 'border-[#d33b3b]'}"
                        >
                            <span
                                >{t("play.math.mine", {
                                    v:
                                        mark.mine === ""
                                            ? t("play.math.none")
                                            : mark.mine,
                                })}</span
                            >
                            <span class="right"
                                >{t("play.math.correctAnswer", {
                                    v: mark.answer,
                                })}</span
                            >
                        </div>
                    {:else}
                        <input
                            bind:this={inputEl}
                            class="entry"
                            inputmode="decimal"
                            lang="en"
                            autocomplete="off"
                            autocapitalize="off"
                            spellcheck="false"
                            autocorrect="off"
                            aria-label={t("play.math.input.label", {
                                n: idx + 1,
                            })}
                            placeholder={t("play.math.input.placeholder")}
                            value={answer}
                            readonly={waiting}
                            oninput={type}
                        />
                    {/if}
                {:else}
                    <div class="flex flex-col gap-1.5">
                        {#each q.choices as c, n (n)}
                            {@const picked =
                                (mark ? mark.mine : answer) === n + 1}
                            <button
                                type="button"
                                class="choice"
                                class:on={picked && !mark}
                                class:good={!!mark && mark.answer === n + 1}
                                class:bad={!!mark && picked && !mark.right}
                                disabled={!!mark || waiting}
                                onclick={() => choose(n + 1)}
                            >
                                <span class="num">{CIRCLED[n]}</span><span
                                    class="formula"
                                    >{@html formulaHtml(c)}</span
                                >
                            </button>
                        {/each}
                    </div>
                {/if}
                {#if mark}
                    <div class="verdict {mark.right ? 'right' : 'wrong'}">
                        <Icon name={mark.right ? "check_circle" : "cancel"} size={26} fill />
                        {mark.right
                            ? t("play.math.correct")
                            : t("play.math.incorrect")}
                    </div>
                    {#if mark.explanation}
                        <div class="why">
                            <b>{t("play.math.explanation")}</b>
                            {@html mathHtml(mark.explanation)}
                        </div>
                    {/if}
                {/if}
            </div>

            {#if !mark}
                <button
                    type="button"
                    onclick={grade}
                    disabled={waiting || !playing}
                    class="hw kk-btn kk-btn-hero kk-btn-lg w-full max-w-md"
                    ><span class="kk-btn-label">{t("play.math.check")}</span
                    ></button
                >
            {:else}
                <button
                    type="button"
                    onclick={next}
                    class="hw kk-btn kk-btn-hero kk-btn-lg w-full max-w-md"
                    ><span class="kk-btn-label"
                        >{last
                            ? t("play.math.seeResult")
                            : t("play.math.next")}</span
                    ></button
                >
            {/if}
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
    /* the container is glass (the stage shows through it); what is in it is solid and has a shadow */
    .panel {
        flex: none;
        border-radius: 20px;
        color: var(--kk-text);
        background: color-mix(in srgb, var(--kk-surface) 55%, transparent);
        -webkit-backdrop-filter: blur(8px);
        backdrop-filter: blur(8px);
        box-shadow: 0 3px 6px rgb(20 40 80 / 0.18);
    }
    /* a card of a level: 레미 in the outfit of the level, cropped at the bottom (the upper two thirds show), the grade, the range and the pass mark in grey chips, the pay in a row */
    /* a level as a row of the arcade list: solid, a shadow, and it opens in place; 레미 is cropped by the bottom edge of the row itself */
    .row {
        border-radius: 16px;
        background: color-mix(in srgb, var(--kk-surface) 94%, transparent);
        box-shadow: 0 2px 6px rgb(20 40 80 / 0.22);
        overflow: hidden;
        transition: background 0.15s, box-shadow 0.15s;
    }
    .row:hover,
    .row-on {
        background: var(--kk-surface);
    }
    .row-on {
        box-shadow: 0 2px 6px rgb(20 40 80 / 0.22), inset 0 0 0 2px var(--kk-accent);
    }
    .head {
        display: flex;
        align-items: center;
        gap: 0.8rem;
        width: 100%;
        min-height: 4.75rem;
        padding: 0.5rem 0.75rem 0 0.5rem;
        text-align: left;
    }
    .thumb {
        flex: none;
        align-self: flex-end;
        display: flex;
        justify-content: center;
        align-items: flex-start;
        width: 4.5rem;
        height: 3.75rem;
        overflow: hidden;
    }
    .thumb img {
        flex: none;
        width: 4.5rem;
        height: 5.6rem;
        object-fit: contain;
        object-position: top;
    }
    .head :global(.chev) {
        align-self: center;
        margin-bottom: 0.5rem;
    }
    .head > span.flex {
        align-self: center;
        padding-bottom: 0.5rem;
    }
    .acc {
        display: grid;
        grid-template-rows: 0fr;
        transition: grid-template-rows 0.25s ease;
    }
    .acc-open {
        grid-template-rows: 1fr;
    }
    @media (prefers-reduced-motion: reduce) {
        .acc {
            transition: none;
        }
    }
    .crop {
        display: flex;
        justify-content: flex-start;
        align-items: flex-start;
        height: 84px;
        overflow: hidden;
    }
    .crop img {
        flex: none;
        width: 126px;
        height: 126px;
        object-fit: contain;
    }
    .meta {
        display: flex;
        flex-direction: column;
        gap: 0.3rem;
        width: 100%;
    }
    .line {
        display: flex;
        align-items: flex-start;
        justify-content: flex-start;
        gap: 0.5rem;
    }
    .chip {
        flex: none;
        min-width: 4.8rem;
        text-align: center;
        border-radius: 999px;
        background: #6b7280;
        color: #fff;
        padding: 0.15rem 0.65rem;
        font-size: 0.72rem;
        font-weight: 800;
        line-height: 1.5;
    }
    .val {
        flex: 1;
        min-width: 0;
        font-size: 0.8rem;
        font-weight: 700;
        line-height: 1.5;
        text-align: left;
        color: var(--kk-text);
    }
    .rewards {
        display: flex;
        align-items: center;
        align-self: flex-start;
        justify-content: flex-start;
        gap: 0.5rem;
        border-radius: 12px;
        background: var(--kk-surface-2);
        padding: 0.25rem 0.7rem;
    }
    .rewards .label {
        font-size: 0.75rem;
        font-weight: 800;
        color: var(--kk-muted);
    }
    /* the question in the frog's mouth */
    .mouth {
        height: 100%;
        overflow-y: auto;
        padding: 12px 14px;
        display: flex;
        flex-direction: column;
        justify-content: center;
        color: #fff;
        font-weight: 700;
        line-height: 1.75;
        text-align: left;
    }
    .mouth .stemwrap {
        overflow-wrap: anywhere;
    }
    .mouth :global(.pts) {
        margin-left: 0.35em;
        font-family: KaTeX_Main, "Times New Roman", serif;
        font-weight: 700;
    }
    .mouth :global(.mq) {
        margin: 0.3rem 0;
        border: 1.5px solid rgb(255 255 255 / 0.45);
        border-radius: 8px;
        background: rgb(255 255 255 / 0.1);
        padding: 0.3rem 0.6rem;
    }
    /* the number of a question and its points are set as an exam sets them: in the LaTeX face */
    .qno {
        margin-right: 0.35rem;
    }
    .qno :global(.katex) {
        font-size: 1.1em;
    }
    .stem :global(.mq),
    .why :global(.mq) {
        margin: 0.3rem 0;
        border: 1.5px solid var(--kk-line);
        border-radius: 10px;
        background: var(--kk-surface-2);
        padding: 0.4rem 0.7rem;
        font-size: 0.95rem;
        line-height: 1.7;
    }
    .stem {
        overflow-x: auto;
        overflow-y: hidden;
        font-size: 1rem;
        font-weight: 600;
        line-height: 1.8;
    }
    .choice {
        display: flex;
        align-items: center;
        gap: 0.6rem;
        overflow-x: auto;
        border-radius: 12px;
        background: var(--kk-surface);
        box-shadow: 0 2px 4px rgb(20 40 80 / 0.2);
        padding: 0.5rem 0.8rem;
        text-align: left;
        transition:
            border-color 0.12s,
            background 0.12s;
    }
    .choice .num {
        flex: none;
        font-weight: 800;
    }
    .choice:hover:not(:disabled) {
        border-color: var(--kk-accent);
    }
    .choice.on {
        border-color: var(--kk-accent);
        background: color-mix(
            in srgb,
            var(--kk-accent) 16%,
            var(--kk-surface-2)
        );
    }
    .choice.good {
        border-color: #3f8a4a;
        background: color-mix(in srgb, #3f8a4a 16%, var(--kk-surface-2));
    }
    .choice.bad {
        border-color: #d33b3b;
        background: color-mix(in srgb, #d33b3b 16%, var(--kk-surface-2));
    }
    .entry {
        width: 100%;
        border: 0;
        border-bottom: 2px solid var(--kk-line-strong);
        background: transparent;
        padding: 0.45rem 0.1rem;
        font-size: 1.25rem;
        font-weight: 800;
        color: var(--kk-text);
        outline: none;
    }
    .entry:focus {
        border-bottom-color: var(--kk-accent);
    }
    .verdict {
        display: flex;
        align-items: center;
        justify-content: center;
        gap: 0.4rem;
        font-size: 1.1rem;
        font-weight: 800;
    }
    .mark {
        display: inline-flex;
        align-items: center;
        gap: 0.25rem;
    }
    /* the red pen over the number of the question: a small circle or a stroke, drawn in and left there */
    .qno.marked {
        position: relative;
        display: inline-block;
    }
    .pen {
        position: absolute;
        left: -7px;
        top: -5px;
        width: calc(100% + 14px);
        height: calc(100% + 10px);
        overflow: visible;
        pointer-events: none;
        fill: none;
        stroke: #ff3b3b;
        stroke-width: 2.6px;
        stroke-linecap: round;
        stroke-linejoin: round;
    }
    .pen .stroke {
        vector-effect: non-scaling-stroke;
        stroke-dasharray: 1;
        stroke-dashoffset: 1;
        animation: pen 0.4s ease-out forwards;
    }
    .pen .s2 {
        stroke-width: 1.6px;
        animation-delay: 0.1s;
    }
    @keyframes pen {
        to {
            stroke-dashoffset: 0;
        }
    }
    @media (prefers-reduced-motion: reduce) {
        .pen .stroke {
            animation: none;
            stroke-dashoffset: 0;
        }
    }
    .right {
        color: #3f8a4a;
    }
    .wrong {
        color: #d33b3b;
    }
    .why {
        border-radius: 12px;
        background: var(--kk-surface-2);
        padding: 0.5rem 0.8rem;
        font-size: 0.85rem;
        line-height: 1.6;
    }
    .answerline {
        display: flex;
        justify-content: space-between;
        gap: 0.5rem;
        padding: 0.3rem 0;
        font-size: 0.85rem;
        font-weight: 800;
    }
    .review {
        border-radius: 12px;
        background: var(--kk-surface);
        box-shadow: 0 2px 4px rgb(20 40 80 / 0.2);
        padding: 0.4rem 0.8rem;
    }
    .review summary {
        display: flex;
        align-items: center;
        gap: 0.6rem;
        cursor: pointer;
        font-weight: 800;
    }
    .review .stem {
        margin-top: 0.4rem;
    }
    @media (prefers-color-scheme: dark) {
        :global(:root:not([data-theme="light"])) .right {
            color: #7bd488;
        }
        :global(:root:not([data-theme="light"])) .wrong {
            color: #ff8a8a;
        }
    }
    :global(:root[data-theme="dark"]) .right {
        color: #7bd488;
    }
    :global(:root[data-theme="dark"]) .wrong {
        color: #ff8a8a;
    }
</style>
