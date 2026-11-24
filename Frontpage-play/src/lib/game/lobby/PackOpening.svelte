<script>
    import { onMount, untrack } from "svelte";
    import { t } from "../i18n.svelte.js";
    import { ms } from "../motion.js";
    import { itemImage } from "../images.js";
    import { themeOf } from "../theme.js";
    import { titleOf } from "../title.js";
    import {
        playSound,
        playPackBgm,
        endPackBgm,
        playPackSfx,
        preloadPackSounds,
    } from "../audio.svelte.js";
    import { prompt, confirm, question } from "../ui/confirm.svelte.js";
    import { boxOdds } from "../shop.js";
    import { toast } from "../ui/toast.svelte.js";
    import { plainText } from "../protocol.js";
    import { themeFor, themeFromImage, themeStyle } from "../packTheme.js";
    import ShopThumb from "./ShopThumb.svelte";
    import Icon from "./Icon.svelte";
    import PackParticles from "./PackParticles.svelte";
    import NewsWaveTitle from "./NewsWaveTitle.svelte";
    import Dialog from "./Dialog.svelte";
    import DataTable from "../ui/DataTable.svelte";
    import Rich from "../ui/Rich.svelte";

    /**
     * 확률형 아이템 개봉 연출. A holographic foil pack floats in the middle; the player slides along its perforation (mouse or touch) and
     * the strip tears off, a flash, and the cards come out and turn over one by one - the rare ones last, after a beat, with a holo foil.
     *
     * The pack's colours come from the box's own picture (`packTheme`): its dominant hue and a pastel prism around it.
     *
     * `choose` (the profile): before the tear the pack asks how many to open (1 / 10 / any, never more than `owned`). Without it (the shop,
     * which has asked and counted already) the pack is ready to tear. The request goes out when the tear is done, `onrequest(n)`, and the
     * flash and the wait hide the server's answer time; the parent then hands the prizes in (`prizes`, rarest last, `rare` the keys that
     * are under 1%). Nothing is taken from the player until the strip is torn.
     * @type {{open:boolean, itemId:string, shop:Record<string,any>, nameOf:(id:string)=>string, owned?:number, choose?:boolean, prizes?:{key:string,q:number}[]|null, rare?:Set<string>, note?:string, onrequest:(n:number)=>void, onclose?:()=>void}}
     */
    let {
        open = $bindable(false),
        itemId,
        shop,
        nameOf,
        owned = 1,
        choose = false,
        prizes = null,
        rare = new Set(),
        note = "",
        onrequest,
        onclose,
    } = $props();

    const TEAR = 15; // the perforation lies this far down the pack (% of its height)
    const COMMIT = 0.82; // torn this far, the rest tears itself
    const TEETH = 22;
    const DEPTH = 1.5;
    const JAG_N = 30;
    const JAG = [0.5, -0.4, 0.7, -0.2, 0.3, -0.6, 0.4, 0.1, -0.5, 0.6, -0.1];
    const jagPts = Array.from({ length: JAG_N + 1 }, (_, i) => [
        (i * 100) / JAG_N,
        TEAR + JAG[i % JAG.length],
    ]);
    const edge = (y, dir) =>
        Array.from({ length: TEETH + 1 }, (_, i) => [
            (i * 100) / TEETH,
            y + (i % 2 ? dir * DEPTH : 0),
        ]);
    const poly = (pts) =>
        `polygon(${pts.map(([x, y]) => `${x.toFixed(2)}% ${y.toFixed(2)}%`).join(",")})`;
    // the strip above the perforation (crimped top, torn bottom) and the pack below it (torn top, crimped bottom): one jagged line shared
    const STRIP = poly([...edge(0, 1), ...[...jagPts].reverse()]);
    const BODY = poly([...jagPts, ...edge(100, -1).reverse()]);
    const STARS = [
        { x: 16, y: 30, s: 16, d: 0 },
        { x: 82, y: 38, s: 12, d: 0.8 },
        { x: 24, y: 72, s: 11, d: 1.4 },
        { x: 78, y: 78, s: 18, d: 0.4 },
    ];

    let el = $state();
    let stage = $state();
    let packEl = $state();
    let oddsOpen = $state(false);
    let fx = $state(); // the particles
    let cardEls = $state([]);
    let rareMusic = false;
    /** idle (floating, asking how many) -> armed (ready to tear) -> burst -> wait (for the prizes) -> reveal */
    let phase = $state("idle");
    let n = $state(1);
    let p = $state(0); // how far the strip is torn, 0~1
    let snapping = $state(false); // the strip moves by transition (it was let go, or the rest tears itself), not with the pointer
    let theme = $state(themeFor(untrack(() => itemId)));
    let cardThemes = $state({});
    let flips = $state([]);
    let spot = $state(-1); // the card that has the light (a rare one, just before it turns)
    let finished = $state(false);
    let drag = null;
    let token = 0;
    let burstTimer;

    const sleep = (t) => new Promise((r) => setTimeout(r, t));
    const clamp = (v, lo, hi) => Math.min(hi, Math.max(lo, v));
    const boxName = $derived(nameOf(itemId));
    const odds = $derived(boxOdds(shop[itemId]));
    const oddsColumns = $derived([
        { key: "item", label: t("play.pack.oddsItem"), class: "align-middle" },
        {
            key: "p",
            label: t("play.pack.oddsRate"),
            align: "right",
            numeric: true,
            nowrap: true,
            width: "w-28",
            class: "align-middle font-bold",
        },
    ]);
    const fmtP = (p) => `${+p.toFixed(p < 1 ? 3 : 2)}%`;
    const list = $derived(prizes ?? []);
    const cardSize = $derived(
        list.length <= 5
            ? "min(11rem, 38vw)"
            : list.length <= 10
              ? "min(8.2rem, 25vw)"
              : "min(5.4rem, 18vw)",
    );

    function reset() {
        token++;
        clearTimeout(burstTimer);
        p = 0;
        snapping = false;
        drag = null;
        flips = [];
        spot = -1;
        finished = false;
        cardThemes = {};
        n = 1;
        phase = choose ? "idle" : "armed";
    }

    $effect(() => {
        if (!el) return;
        if (open) {
            if (!el.open) {
                untrack(reset);
                el.showModal();
            }
        } else if (el.open) {
            token++;
            clearTimeout(burstTimer);
            el.close();
        }
    });

    // the music: the waiting loop while the pack is open (the lobby's or the shop's comes back when it closes)
    $effect(() => {
        if (!open) return;
        playPackBgm("bgm");
        playPackSfx("idle", 0.8);
        return () => {
            rareMusic = false;
            endPackBgm();
        };
    });

    // the box's own colours (read from its picture; the id's hue stands in until then)
    $effect(() => {
        if (!open) return;
        const id = itemId;
        theme = themeFor(id);
        themeFromImage(id, shop[id] ? itemImage(id, shop) : "").then(
            (th) => id === itemId && (theme = th),
        );
    });

    // the prizes came while the pack was waiting for them
    $effect(() => {
        if (open && phase === "wait" && prizes?.length) untrack(startReveal);
        else if (open && phase === "wait" && prizes) untrack(dismiss);
    });

    const dismiss = () => {
        open = false;
        onclose?.();
    };
    /** The X and Esc: a pack that is ready to tear asks first (nothing is lost before the tear, but a stray press should not throw the pack away). */
    async function leave() {
        if (phase === "armed") {
            const yes = await confirm({
                message: t("play.pack.cancelAsk"),
                ok: t("play.pack.cancelOk"),
                cancel: t("play.pack.cancelBack"),
            });
            if (!yes || (phase !== "armed" && phase !== "idle")) return;
        }
        dismiss();
    }

    // ---- before the tear ----
    function pick(count) {
        n = count;
        phase = "armed";
    }
    async function custom() {
        const v = await prompt({
            message: t("kkutu.js.consume", { count: owned }),
            value: String(Math.min(owned, 10)),
            max: 4,
        });
        if (v === null) return;
        const c = parseInt(v, 10);
        if (isNaN(c))
            return void toast(plainText(t("kkutu.js.invalidConsume")), {
                kind: "error",
            });
        if (c < 1) return;
        if (c > owned)
            return void toast(plainText(t("kkutu.js.notEnoughConsume")), {
                kind: "error",
            });
        pick(c);
    }

    // ---- the tear ----
    function down(e) {
        if (phase !== "armed") return;
        e.currentTarget.setPointerCapture(e.pointerId);
        drag = {
            x0: e.clientX,
            p0: p,
            w: (packEl?.getBoundingClientRect().width ?? 240) * 0.9,
        };
        snapping = false;
    }
    function move(e) {
        if (!drag) return;
        p = clamp(drag.p0 + (e.clientX - drag.x0) / drag.w, 0, 1);
        if (p >= COMMIT) {
            drag = null;
            finishTear();
        }
    }
    function up() {
        if (!drag) return;
        drag = null;
        snapping = true;
        p = 0; // let go too early: the strip falls back
    }
    function finishTear() {
        if (phase !== "armed") return;
        snapping = true;
        p = 1;
        phase = "burst";
        onrequest(n);
        playPackSfx("tear");
        setTimeout(() => playPackSfx("burst"), 140);
        const at = center(packEl);
        fx?.burst(at.x, at.y, 1.15);
        burstTimer = setTimeout(() => {
            phase = "wait";
            if (prizes?.length) startReveal();
            else if (prizes) dismiss(); // opened, but no item came out (ping or exp only): the notices say what it gave
        }, ms(1000));
    }

    /** The middle of an element, in the stage's own pixels (where the particles live). */
    function center(node) {
        const st = stage?.getBoundingClientRect();
        const r = node?.getBoundingClientRect();
        if (!st || !r)
            return { x: (st?.width ?? 600) / 2, y: (st?.height ?? 600) / 2 };
        return {
            x: r.left - st.left + r.width / 2,
            y: r.top - st.top + r.height / 2,
        };
    }

    // ---- the reveal ----
    async function startReveal() {
        if (phase === "reveal") return;
        phase = "reveal";
        const mine = ++token;
        const items = list;
        flips = items.map(() => false);
        finished = false;
        for (const { key } of items) {
            cardThemes[key] = themeFor(key);
            if (shop[key] && !themeOf(shop[key]) && !titleOf(shop[key]))
                themeFromImage(key, itemImage(key, shop)).then(
                    (th) => (cardThemes[key] = th),
                );
        }
        const step = Math.max(100, 420 - items.length * 14);
        await sleep(ms(650));
        for (let i = 0; i < items.length; i++) {
            if (mine !== token) return;
            const isRare = rare.has(items[i].key);
            if (isRare) {
                spot = i;
                if (!rareMusic) ((rareMusic = true), playPackBgm("rare"));
                playPackSfx("riser");
                await sleep(ms(800));
                if (mine !== token) return;
            }
            flips[i] = true;
            if (isRare) {
                playPackSfx("hit");
                const at = center(cardEls[i]);
                fx?.burst(at.x, at.y, 1.5);
            } else playPackSfx("flip", 0.7);
            await sleep(ms(isRare ? 1200 : step));
            spot = -1;
        }
        if (mine === token) end();
    }
    function end() {
        finished = true;
        spot = -1;
        playSound("success");
    }
    function skip() {
        token++;
        flips = flips.map(() => true);
        end();
    }
    function turn(i) {
        if (phase !== "reveal" || flips[i]) return;
        flips[i] = true;
        playPackSfx(rare.has(list[i].key) ? "hit" : "flip", 0.7);
        if (flips.every(Boolean)) {
            token++;
            end();
        }
    }

    // the keyboard: Enter tears a pack that is ready, closes a finished one; Esc backs out before the tear, skips during the reveal
    let raf;
    function autoTear() {
        if (phase !== "armed" || drag) return;
        const from = p;
        const t0 = performance.now();
        snapping = false;
        const run = (now) => {
            const k = Math.min(1, (now - t0) / ms(320) || 1);
            p = from + (1 - from) * k;
            if (k < 1 && phase === "armed") raf = requestAnimationFrame(run);
            else finishTear();
        };
        raf = requestAnimationFrame(run);
    }
    function key(e) {
        if (!open || question.current || oddsOpen) return; // a question (cancel the opening? how many?) or the odds table has the keys
        const go = e.key === "Enter" || e.key === " ";
        if (e.key === "Escape") {
            e.preventDefault();
            e.stopPropagation();
            if (phase === "idle" || phase === "armed") leave();
            else if (phase === "reveal") (finished ? dismiss : skip)();
        } else if (go && e.target?.tagName !== "BUTTON") {
            e.preventDefault();
            e.stopPropagation();
            if (e.repeat) return;
            if (phase === "armed") autoTear();
            else if (phase === "reveal") (finished ? dismiss : skip)();
        }
    }
    function light(e) {
        if (!stage) return;
        const r = stage.getBoundingClientRect();
        stage.style.setProperty(
            "--mx",
            clamp((e.clientX - r.left) / r.width, 0, 1).toFixed(3),
        );
        stage.style.setProperty(
            "--my",
            clamp((e.clientY - r.top) / r.height, 0, 1).toFixed(3),
        );
    }

    onMount(() => {
        preloadPackSounds();
        return () => {
            cancelAnimationFrame(raf);
            clearTimeout(burstTimer);
            el?.open && el.close();
        };
    });
    const tearing = $derived(p > 0 && phase === "armed");
</script>

<svelte:window onkeydowncapture={key} />

{#snippet foil(kind)}
    <div class="face foil" style:clip-path={kind === "strip" ? STRIP : BODY}>
        <span class="crimp top"></span>
        <span class="crimp bottom"></span>
        {#each STARS as s, i (i)}<span
                class="star"
                style="left:{s.x}%;top:{s.y}%;width:{s.s}px;height:{s.s}px;animation-delay:{s.d}s"
            ></span>{/each}
        <img
            class="art"
            src={shop[itemId] ? itemImage(itemId, shop) : ""}
            alt=""
            draggable="false"
            crossorigin="anonymous"
        />
        <span class="label">{boxName}</span>
        <span class="cut"></span>
    </div>
{/snippet}

<dialog
    bind:this={el}
    aria-label={boxName}
    class="pack"
    style={themeStyle(theme)}
    oncancel={(e) => e.preventDefault()}
>
    {#if open}
        <!-- svelte-ignore a11y_no_static_element_interactions -->
        <div
            bind:this={stage}
            class="stage"
            onpointermove={light}
            style="--mx:0.5;--my:0.4;--cw:{cardSize}"
        >
            {#key theme.hue}<PackParticles
                    bind:this={fx}
                    colors={[theme.a, theme.b, theme.c, theme.d]}
                    active={open}
                />{/key}
            <span class="glow" aria-hidden="true"></span>
            {#if phase === "idle" || phase === "armed"}
                <div class="corner">
                    {#if odds.length}<button
                            type="button"
                            class="round"
                            aria-label={t("play.pack.odds")}
                            onclick={() => (oddsOpen = true)}
                        >
                            <Icon name="percent" size={24} weight={600} />
                        </button>{/if}
                    <button
                        type="button"
                        class="round"
                        aria-label={t("play.pack.close")}
                        onclick={leave}
                    >
                        <Icon name="close" size={24} />
                    </button>
                </div>
            {/if}
            {#if phase === "reveal" && spot >= 0}<span
                    class="spot"
                    aria-hidden="true"
                ></span>{/if}

            {#if phase === "reveal"}
                <h2 class="kk-fire-text title">{t("play.obtain.acquired")}</h2>
                <ul class="cards">
                    {#each list as it, i (it.key)}
                        {@const th = cardThemes[it.key] ?? themeFor(it.key)}
                        {@const isRare = rare.has(it.key)}
                        <li
                            class="slot"
                            class:dim={spot >= 0 && spot !== i}
                            style="--i:{i}"
                        >
                            <button
                                type="button"
                                bind:this={cardEls[i]}
                                class="card"
                                class:flipped={flips[i]}
                                class:rare={isRare}
                                class:lit={spot === i}
                                onclick={() => turn(i)}
                                aria-label={flips[i]
                                    ? nameOf(it.key)
                                    : t("play.pack.card")}
                            >
                                <span class="inner">
                                    <span
                                        class="side back foil"
                                        aria-hidden="true"
                                    >
                                        <Icon
                                            name="auto_awesome"
                                            size={38}
                                            fill
                                            weight={500}
                                            class="mark"
                                        />
                                    </span>
                                    <span
                                        class="side front"
                                        style={themeStyle(th)}
                                    >
                                        {#if isRare}<span class="holo"
                                            ></span>{/if}
                                        <span class="shine"></span>
                                        <span class="pic">
                                            <ShopThumb
                                                id={it.key}
                                                {shop}
                                                class="size-full"
                                            />
                                        </span>
                                        <span class="nm">{nameOf(it.key)}</span>
                                        {#if it.q > 1}<b class="qty">
                                                ×{it.q}
                                            </b>{/if}
                                        {#if isRare}<span class="glitter"
                                            ></span>{/if}
                                    </span>
                                </span>
                            </button>
                        </li>
                    {/each}
                </ul>
                <div class="foot">
                    {#if finished}
                        {#if note}<p class="note"><Rich html={note} /></p>{/if}
                        <button
                            type="button"
                            class="kk-btn kk-btn-primary inline-flex items-center gap-2.5 px-9"
                            onclick={dismiss}
                        >
                            {t("kkutu.dialog.obtain.ok")}<kbd
                                class="kk-key"
                                aria-hidden="true"
                            >
                                Enter
                            </kbd>
                        </button>
                    {:else}
                        <button
                            type="button"
                            class="kk-btn inline-flex items-center gap-1.5 px-6"
                            onclick={skip}
                        >
                            <Icon name="fast_forward" size={20} fill />{t(
                                "play.pack.skip",
                            )}
                        </button>
                    {/if}
                </div>
            {:else}
                <div class="center">
                    <div
                        class="floater"
                        class:hold={tearing}
                        class:burst={phase === "burst" || phase === "wait"}
                    >
                        <div
                            bind:this={packEl}
                            class="packbox"
                            class:snap={snapping}
                            style="--p:{p}"
                            aria-hidden={phase === "idle"}
                        >
                            <span class="inside" style:opacity={p * 1.4}></span>
                            {@render foil("body")}
                            <div
                                class="half torn"
                                style="clip-path:inset(0 {100 -
                                    p * 100}% 0 0);transform-origin:{p *
                                    100}% {TEAR}%"
                            >
                                {@render foil("strip")}
                            </div>
                            <div
                                class="half"
                                style="clip-path:inset(0 0 0 {p * 100}%)"
                            >
                                {@render foil("strip")}
                            </div>
                            {#if phase === "armed"}
                                <span
                                    class="knob"
                                    style="left:{p * 100}%;top:{TEAR}%"
                                >
                                    <Icon
                                        name="arrow_forward"
                                        size={24}
                                        weight={700}
                                    />
                                </span>
                                <!-- svelte-ignore a11y_no_static_element_interactions -->
                                <div
                                    class="zone"
                                    onpointerdown={down}
                                    onpointermove={move}
                                    onpointerup={up}
                                    onpointercancel={up}
                                ></div>
                            {/if}
                        </div>
                    </div>
                    {#if phase === "wait"}<span class="orb" aria-hidden="true"
                        ></span>{/if}
                </div>

                {#if phase === "burst"}
                    <span class="flash" aria-hidden="true"></span>
                {/if}

                <div class="foot">
                    {#if phase === "idle"}
                        <p class="owned">
                            <Icon name="inventory_2" size={20} fill />{t(
                                "play.pack.owned",
                                { n: owned },
                            )}
                        </p>
                        <div class="btns">
                            <button
                                type="button"
                                class="kk-btn kk-btn-primary px-6"
                                disabled={owned < 1}
                                onclick={() => pick(1)}
                            >
                                {t("play.pack.open1")}
                            </button>
                            {#if owned > 1}
                                <button
                                    type="button"
                                    class="kk-btn kk-btn-primary px-6"
                                    disabled={owned < 10}
                                    onclick={() => pick(10)}
                                >
                                    {t("play.pack.open10")}
                                </button>
                                <button
                                    type="button"
                                    class="kk-btn inline-flex items-center gap-1.5 px-6"
                                    onclick={custom}
                                >
                                    <Icon
                                        name="edit"
                                        size={18}
                                        weight={600}
                                    />{t("play.pack.custom")}
                                </button>
                            {/if}
                        </div>
                    {:else if phase === "armed"}
                        <div class="guide" class:gone={tearing}>
                            <Icon
                                name="auto_awesome"
                                size={34}
                                fill
                                class="gs gs1"
                            />
                            <NewsWaveTitle
                                text={t("play.pack.hint")}
                                accent="#5d54d6"
                                outline="#fff"
                                outlineWidth="0.3em"
                                fill="linear-gradient(180deg, #00d2ee 0%, #8f5bff 52%, #ff1fbd 100%)"
                                class="gtext"
                            />
                            <Icon
                                name="auto_awesome"
                                size={24}
                                fill
                                class="gs gs2"
                            />
                        </div>
                    {/if}
                </div>
            {/if}
        </div>
    {/if}
</dialog>

<!-- the odds of what the box can give (the table the game client uses everywhere); the pack's own dialog is on top of the page, so this opens above it -->
<Dialog bind:open={oddsOpen} title="{boxName} {t('play.pack.odds')}">
    <DataTable
        caption={t("play.pack.odds")}
        columns={oddsColumns}
        rows={odds}
        dense
        maxHeight="max-h-[60vh]"
    >
        {#snippet cell(r, col)}
            {#if col.key === "item"}
                <span class="inline-flex items-center gap-2">
                    <ShopThumb id={r.key} {shop} class="size-8" /><span
                        class="font-bold"
                    >
                        {nameOf(r.key)}
                    </span>{#if r.q > 1}<span
                            class="text-xs font-bold text-(color:--kk-muted)"
                        >
                            ×{r.q}
                        </span>{/if}
                </span>
            {:else}{fmtP(r.p)}{/if}
        {/snippet}
    </DataTable>
</Dialog>

<style>
    @property --kk-ang {
        syntax: "<angle>";
        inherits: false;
        initial-value: 0deg;
    }
    .pack {
        position: fixed;
        inset: 0;
        width: 100vw;
        max-width: none;
        height: 100dvh;
        max-height: none;
        margin: 0;
        padding: 0;
        border: 0;
        overflow: hidden;
        --ink: color-mix(in srgb, var(--pdeep) 38%, #34336e);
        color: var(--ink);
        /* a pastel sky: aqua, lilac and pink, with only a trace of the box's own colour in the middle (the box's hue never floods the screen) */
        background:
            radial-gradient(
                55% 42% at 50% 44%,
                color-mix(in srgb, var(--pa) 30%, #fff),
                transparent 72%
            ),
            radial-gradient(85% 60% at 4% 0%, #a9efff, transparent 66%),
            radial-gradient(85% 60% at 100% 6%, #d6c4ff, transparent 66%),
            radial-gradient(95% 70% at 92% 100%, #ffc6e8, transparent 66%),
            radial-gradient(70% 55% at 0% 100%, #c5f0ff, transparent 66%),
            linear-gradient(165deg, #d9f6ff, #e7defe 55%, #ffe3f4);
        animation: kk-fade-in 0.25s ease-out both;
    }
    .pack::backdrop {
        background: rgb(0 0 0 / 0.5);
    }
    .stage {
        position: relative;
        isolation: isolate;
        display: flex;
        flex-direction: column;
        align-items: center;
        justify-content: center;
        width: 100%;
        height: 100%;
        gap: 1.2rem;
        padding: 1rem 1rem 8rem;
        touch-action: none;
    }
    /* a soft breathing light where the pack floats */
    .glow {
        position: absolute;
        left: 50%;
        top: 46%;
        width: min(34rem, 90vw);
        aspect-ratio: 1;
        translate: -50% -50%;
        border-radius: 50%;
        background: radial-gradient(
            circle,
            rgb(255 255 255 / 0.85),
            rgb(255 255 255 / 0.25) 45%,
            transparent 70%
        );
        animation: pk-breathe 3.4s ease-in-out infinite;
        pointer-events: none;
    }
    .center {
        position: relative;
        z-index: 1;
        display: grid;
        place-items: center;
    }
    .floater {
        animation: pk-float 3.4s ease-in-out infinite;
        filter: drop-shadow(
            0 18px 22px color-mix(in srgb, var(--pdeep) 38%, transparent)
        );
    }
    .floater.hold {
        animation-play-state: paused;
    }
    .floater.burst {
        animation: pk-blow 0.7s cubic-bezier(0.3, 0, 0.2, 1) forwards;
    }
    .packbox {
        --w: min(16.5rem, 64vw);
        position: relative;
        width: var(--w);
        aspect-ratio: 3 / 5;
        transform: rotate(5deg); /* the resting tilt, to the right: the floating itself only goes up and down */
    }
    .face {
        position: absolute;
        inset: 0;
    }
    /* the conic gradient's centre (where its colours meet in a point) is the middle of the item's picture, which covers it */
    .foil {
        background:
            linear-gradient(
                    112deg,
                    transparent 34%,
                    rgb(255 255 255 / 0.5) 47%,
                    transparent 60%
                )
                calc(var(--mx) * 160% - 30%) 0 / 220% 100% no-repeat,
            conic-gradient(
                from calc(var(--kk-ang) + var(--mx) * 160deg) at 50% 49%,
                var(--pa),
                var(--pb),
                var(--pc),
                var(--pd),
                var(--pa)
            ),
            var(--pa);
        animation: pk-hue 7s linear infinite;
    }
    /* the crinkled foil: thin ridges at a slant, and a soft light across them */
    .foil::after {
        content: "";
        position: absolute;
        inset: 0;
        background:
            repeating-linear-gradient(
                98deg,
                rgb(255 255 255 / 0.22) 0 1.5px,
                transparent 1.5px 9px
            ),
            radial-gradient(
                60% 40% at 30% 20%,
                rgb(255 255 255 / 0.3),
                transparent 70%
            );
        mix-blend-mode: soft-light;
        pointer-events: none;
    }
    .crimp {
        position: absolute;
        inset-inline: 0;
        height: 4.5%;
        background: repeating-linear-gradient(
            90deg,
            rgb(255 255 255 / 0.42) 0 2px,
            rgb(0 0 0 / 0.07) 2px 4px
        );
    }
    .crimp.top {
        top: 0;
    }
    .crimp.bottom {
        bottom: 0;
    }
    .art {
        position: absolute;
        left: 50%;
        top: 49%;
        width: 64%;
        aspect-ratio: 1;
        translate: -50% -50%;
        object-fit: contain;
        filter: drop-shadow(0 0 10px var(--pac))
            drop-shadow(0 4px 0 rgb(255 255 255 / 0.55));
    }
    .label {
        position: absolute;
        inset-inline: 9%;
        bottom: 8%;
        padding: 0.35rem 0.5rem;
        border-radius: 999px;
        background: rgb(255 255 255 / 0.82);
        color: var(--pdeep);
        font-family: "ONE-Mobile-POP", "NanumSquare Neo", sans-serif;
        font-size: 0.95rem;
        line-height: 1.15;
        text-align: center;
        overflow: hidden;
        white-space: nowrap;
        text-overflow: ellipsis;
        box-shadow: 0 2px 0 rgb(0 0 0 / 0.12);
    }
    /* the perforation: a dashed line along the tear */
    .cut {
        position: absolute;
        inset-inline: 0;
        top: calc(15% - 1px);
        border-top: 2px dashed rgb(255 255 255 / 0.9);
        filter: drop-shadow(
            0 1px 0 color-mix(in srgb, var(--pdeep) 40%, transparent)
        );
    }
    .star {
        clip-path: polygon(
            50% 0,
            62% 38%,
            100% 50%,
            62% 62%,
            50% 100%,
            38% 62%,
            0 50%,
            38% 38%
        );
        background: #fff;
    }
    .star {
        position: absolute;
        animation: pk-twinkle 2.2s ease-in-out infinite;
    }
    .half {
        position: absolute;
        inset: 0;
        pointer-events: none;
    }
    .half.torn {
        transform: rotate(calc(var(--p) * -16deg))
            translateY(calc(var(--p) * -5%));
    }
    .packbox.snap .half {
        transition:
            clip-path 0.24s ease-out,
            transform 0.24s ease-out;
    }
    /* light inside the pack, seen through the opening */
    .inside {
        position: absolute;
        inset-inline: 4%;
        top: calc(15% - 4%);
        height: 10%;
        background: radial-gradient(
            60% 100% at 50% 100%,
            #fff,
            transparent 70%
        );
        filter: blur(3px);
    }
    /* the round floating button of the arcade screens (their `.round`) */
    .round {
        display: grid;
        place-items: center;
        width: 2.75rem;
        height: 2.75rem;
        border: 0;
        border-radius: 999px;
        background: rgb(255 255 255 / 0.9);
        color: #334155;
        box-shadow: 0 4px 6px rgb(20 40 80 / 0.2);
        cursor: pointer;
        transition:
            transform 0.15s,
            background 0.15s;
    }
    .round:hover {
        transform: scale(1.05);
        background: #fff;
    }
    .corner {
        position: absolute;
        z-index: 3;
        display: flex;
        gap: 0.6rem;
        top: calc(0.9rem + env(safe-area-inset-top));
        right: calc(0.9rem + env(safe-area-inset-right));
    }
    .zone {
        position: absolute;
        inset-inline: -12%;
        top: calc(15% - 9%);
        height: 18%;
        cursor: grab;
        touch-action: none;
    }
    .zone:active {
        cursor: grabbing;
    }
    /* the guide that runs along the perforation: a cyan (#00FFFF) to magenta 45deg disc, a white outline at half strength, a white arrow */
    .knob {
        position: absolute;
        z-index: 2;
        display: grid;
        place-items: center;
        width: 2.6rem;
        height: 2.6rem;
        translate: -50% -50%;
        border: 3px solid rgb(255 255 255 / 0.5);
        border-radius: 50%;
        background: linear-gradient(45deg, #00ffff, #ff00ff) padding-box;
        color: #fff;
        box-shadow:
            0 0 18px rgb(0 255 255 / 0.5),
            0 3px 8px rgb(30 40 120 / 0.35);
        pointer-events: none;
        animation: pk-nudge 1.6s ease-in-out infinite;
    }
    /* a white arrow on cyan needs a thin shadow to read */
    .knob :global(.material-symbols-outlined) {
        filter: drop-shadow(0 1px 1.5px rgb(40 30 120 / 0.55));
    }
    /* 밀어서 포장 열기!: big lettering, a deep cyan to magenta fill running down it, a thick white outline under it (a copy of the text behind) */
    .guide {
        display: flex;
        align-items: center;
        gap: 0.6rem;
        transition: opacity 0.2s;
    }
    .gone {
        opacity: 0;
        animation: none;
    }
    .guide :global(.gtext) {
        margin: 0;
        font-size: clamp(2.1rem, 7.5vw, 3.2rem);
        white-space: nowrap;
    }
    .guide :global(.gs) {
        color: #fff;
        filter: drop-shadow(0 0 6px #00ffff) drop-shadow(0 0 10px #ff00ff);
        animation: pk-twinkle 1.6s ease-in-out infinite;
    }
    .guide :global(.gs2) {
        animation-delay: 0.7s;
        align-self: flex-start;
    }
    .owned {
        display: inline-flex;
        align-items: center;
        gap: 0.4rem;
        margin: 0;
        font-family: "ONE-Mobile-POP", "NanumSquare Neo", sans-serif;
        color: var(--ink);
        text-shadow: 0 1px 0 rgb(255 255 255 / 0.8);
    }
    .foot :global(.kk-btn) {
        font-family: "ONE-Mobile-POP", "NanumSquare Neo", sans-serif;
        letter-spacing: 0.01em;
    }
    .foot {
        z-index: 2;
        position: absolute;
        inset-inline: 0;
        bottom: 0;
        display: flex;
        flex-direction: column;
        align-items: center;
        gap: 0.7rem;
        padding: 1rem 1rem calc(2.4rem + env(safe-area-inset-bottom));
    }
    .btns {
        display: flex;
        flex-wrap: wrap;
        justify-content: center;
        gap: 0.6rem;
    }
    .note {
        margin: 0;
        color: var(--ink);
        max-width: 34rem;
        padding: 0.5rem 1rem;
        border-radius: 1rem;
        background: rgb(255 255 255 / 0.75);
        font-size: 0.85rem;
        font-weight: 700;
        text-align: center;
        line-height: 1.5;
    }

    /* the opening: a white flash, a burst of rays and bits, then a glow while the prizes are on their way */
    .flash {
        position: absolute;
        inset: 0;
        background: radial-gradient(
            circle at 50% 46%,
            #fff 0%,
            rgb(255 255 255 / 0.85) 35%,
            transparent 75%
        );
        animation: pk-flash 0.9s ease-out both;
        pointer-events: none;
    }
    .orb {
        position: absolute;
        width: 7rem;
        height: 7rem;
        border-radius: 50%;
        background: radial-gradient(
            circle,
            #fff 0 30%,
            color-mix(in srgb, var(--pa) 70%, #fff) 60%,
            transparent 72%
        );
        animation: pk-orb 1.1s ease-in-out infinite;
    }

    /* the cards */
    .title {
        --c: #5d54d6;
        position: relative;
        z-index: 1;
        margin: 0;
        font-size: clamp(1.8rem, 6vw, 2.8rem);
        line-height: 1.1;
        text-align: center;
        animation: kk-title-in 0.5s cubic-bezier(0.22, 1, 0.36, 1) both;
    }
    .cards {
        position: relative;
        z-index: 1;
        display: flex;
        flex-wrap: wrap;
        justify-content: center;
        gap: 0.8rem;
        max-width: min(72rem, calc(100% + 2.4rem));
        max-height: 66dvh;
        /* overflow clips on both axes, so the box itself must be roomy: the shadows, the rare card's glow and the lit card's scale all fit inside it */
        margin: -1.2rem;
        padding: 2.2rem;
        overflow-y: auto;
        list-style: none;
        perspective: 900px;
    }
    .slot {
        transition:
            opacity 0.3s,
            scale 0.3s;
        animation: pk-emerge 0.55s cubic-bezier(0.34, 1.5, 0.64, 1) both;
        animation-delay: calc(var(--i) * 45ms);
    }
    .slot.dim {
        opacity: 0.3;
        scale: 0.94;
    }
    .card {
        display: block;
        width: var(--cw);
        aspect-ratio: 63 / 88;
        padding: 0;
        border: 0;
        background: none;
        cursor: pointer;
        perspective: 700px;
    }
    .inner {
        position: relative;
        display: block;
        width: 100%;
        height: 100%;
        transform-style: preserve-3d;
        transition: transform 0.6s cubic-bezier(0.3, 1.3, 0.5, 1);
    }
    .card.flipped .inner {
        transform: rotateY(180deg);
    }
    .side {
        position: absolute;
        inset: 0;
        display: grid;
        place-items: center;
        border-radius: 0.7rem;
        backface-visibility: hidden;
        overflow: hidden;
        box-shadow:
            0 3px 0 rgb(0 0 0 / 0.14),
            0 8px 18px color-mix(in srgb, var(--pdeep) 30%, transparent);
    }
    .back {
        border: 2px solid rgb(255 255 255 / 0.85);
    }
    .back :global(.mark) {
        color: #fff;
        filter: drop-shadow(0 0 8px rgb(255 255 255 / 0.95));
    }
    .front {
        transform: rotateY(180deg);
        grid-template-rows: 1fr auto;
        padding: 0.55rem 0.4rem 0.45rem;
        background:
            radial-gradient(90% 55% at 50% 0%, var(--pb), transparent 75%),
            linear-gradient(165deg, var(--ppale), var(--pa) 70%, var(--pc));
        border: 2px solid rgb(255 255 255 / 0.9);
        color: var(--pdeep);
    }
    .pic {
        display: grid;
        place-items: center;
        width: 78%;
        aspect-ratio: 1;
        align-self: center;
        justify-self: center;
        filter: drop-shadow(0 0 7px var(--pac));
    }
    .nm {
        display: -webkit-box;
        -webkit-line-clamp: 2;
        line-clamp: 2;
        -webkit-box-orient: vertical;
        overflow: hidden;
        min-height: 2.3em;
        font-family: "ONE-Mobile-POP", "NanumSquare Neo", sans-serif;
        font-size: 0.8rem;
        line-height: 1.15;
        text-align: center;
        word-break: keep-all;
        overflow-wrap: anywhere;
    }
    .qty {
        position: absolute;
        top: 0.35rem;
        right: 0.35rem;
        padding: 0 0.4rem;
        border-radius: 0.4rem;
        background: var(--pdeep);
        color: #fff;
        font-family: "ONE-Mobile-POP", "NanumSquare Neo", sans-serif;
        font-size: 0.78rem;
        line-height: 1.3rem;
    }
    .shine {
        position: absolute;
        inset: 0;
        background: linear-gradient(
            115deg,
            transparent 38%,
            rgb(255 255 255 / 0.55) 50%,
            transparent 62%
        );
        background-size: 250% 100%;
        background-position: calc(var(--mx) * 100%) 0;
        pointer-events: none;
    }
    /* a rare card: the foil turns through every colour, with glitter, and a ring pulses once it is face up */
    .holo {
        position: absolute;
        inset: 0;
        background: conic-gradient(
            from var(--kk-ang) at 50% 44%,
            #ff9ad5,
            #ffe29a,
            #a6ffd0,
            #9ad7ff,
            #c9a0ff,
            #ff9ad5
        );
        mix-blend-mode: soft-light;
        opacity: 1;
        animation: pk-holo 4s linear infinite;
        pointer-events: none;
    }
    .glitter {
        position: absolute;
        inset: 0;
        background:
            radial-gradient(circle at 20% 25%, #fff 0 1.2px, transparent 2px),
            radial-gradient(circle at 70% 60%, #fff 0 1.2px, transparent 2px),
            radial-gradient(circle at 45% 82%, #fff 0 1.2px, transparent 2px),
            radial-gradient(circle at 85% 18%, #fff 0 1.2px, transparent 2px);
        background-size: 48px 48px;
        mix-blend-mode: overlay;
        animation: pk-glit 1.6s ease-in-out infinite;
        pointer-events: none;
    }
    .card.rare.flipped .front {
        box-shadow:
            0 0 0 3px #fff,
            0 0 22px 4px var(--pac),
            0 8px 18px color-mix(in srgb, var(--pdeep) 30%, transparent);
    }
    .card.lit .inner {
        transform: scale(1.12) rotateY(0deg);
    }
    .card.lit.flipped .inner {
        transform: scale(1.12) rotateY(180deg);
    }
    .spot {
        position: absolute;
        inset: 0;
        background: radial-gradient(
            circle at 50% 50%,
            transparent 18%,
            rgb(58 44 120 / 0.55) 100%
        );
        animation: kk-fade-in 0.3s ease-out both;
        pointer-events: none;
    }

    @keyframes pk-float {
        0%,
        100% {
            transform: translateY(0);
        }
        50% {
            transform: translateY(-14px);
        }
    }
    @keyframes pk-breathe {
        50% {
            transform: scale(1.12);
            opacity: 0.75;
        }
    }
    @keyframes pk-blow {
        0% {
            transform: scale(1);
            opacity: 1;
        }
        35% {
            transform: scale(1.12) rotate(-2deg);
            opacity: 1;
        }
        100% {
            transform: scale(1.5);
            opacity: 0;
        }
    }
    @keyframes pk-hue {
        to {
            --kk-ang: 360deg;
        }
    }
    @keyframes pk-holo {
        to {
            --kk-ang: 360deg;
        }
    }
    @keyframes pk-twinkle {
        0%,
        100% {
            opacity: 0.2;
            transform: scale(0.5) rotate(0deg);
        }
        50% {
            opacity: 1;
            transform: scale(1.1) rotate(25deg);
        }
    }
    @keyframes pk-nudge {
        0%,
        100% {
            translate: -50% -50%;
        }
        50% {
            translate: -30% -50%;
        }
    }
    @keyframes pk-flash {
        0% {
            opacity: 0;
        }
        20% {
            opacity: 1;
        }
        100% {
            opacity: 0;
        }
    }
    @keyframes pk-orb {
        0%,
        100% {
            transform: scale(0.8);
            opacity: 0.7;
        }
        50% {
            transform: scale(1.15);
            opacity: 1;
        }
    }
    @keyframes pk-emerge {
        from {
            opacity: 0;
            transform: translateY(40%) scale(0.4) rotate(-8deg);
        }
    }
    @keyframes pk-glit {
        50% {
            opacity: 0.25;
        }
    }
    @media (prefers-reduced-motion: reduce) {
        .floater,
        .floater.burst,
        .foil,
        .holo,
        .glitter,
        .star,
        .knob,
        .glow,
        .orb,
        .flash,
        .slot,
        .title {
            animation: none;
        }
        .floater.burst {
            opacity: 0;
        }
        .inner,
        .slot {
            transition: none;
        }
    }
</style>
