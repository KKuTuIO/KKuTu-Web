<script>
	import { tick, untrack } from 'svelte';
		import { quintOut } from 'svelte/easing';
	import { t } from '../i18n.svelte.js';
	import { charText, QUIZ, BOARD } from '../gameModel.js';
	import { patternPolygons } from '../gameTile.js';
	import { Spring, SpringGroup } from '../spring.js';
	import { planReveal, scoreChars, splitHint } from '../wordReveal.js';
	import { processWord } from '../wordMean.js';
	import { maskBad } from '../badwords.js';
	import { plainText } from '../protocol.js';
	import { prefs } from '../prefs.svelte.js';
	import { ms } from '../motion.js';
	import { playSound, playGameBgm } from '../audio.svelte.js';
	import { IMAGE_CDN, IMAGE_VERSION } from '../images.js';
	import { confirm } from '../ui/confirm.svelte.js';
	import Tooltip from '../ui/Tooltip.svelte';
	import Icon from './Icon.svelte';
	import LevelIcon from './LevelIcon.svelte';
	import NickName from './NickName.svelte';
	import Moremi from './Moremi.svelte';
	import ChatBalloon from './ChatBalloon.svelte';
	import GameResult from './GameResult.svelte';
	import GameLoading from './GameLoading.svelte';
	import DrawingCanvas from './DrawingCanvas.svelte';
	import DrawingTools from './DrawingTools.svelte';
	import '../gameStage.css';

	/**
	 * The game board of the word-chain modes: a port of the legacy client's (markup, CSS and timing are the legacy ones, see
	 * gameStage.css and wordReveal.js; the texts are the legacy messages). It is drawn at the legacy 1010x420 and scaled to
	 * the width it is given.
	 */
	let { store } = $props();
	const net = $derived(store.net);
	const room = $derived(store.currentRoom);
	const g = $derived(store.game);
	const me = $derived(net.users[net.id]);
	const spectating = $derived(me?.game.form === 'S');
	const rule = $derived(net.meta.rules[room?.modeKey]);
	const family = $derived(rule?.rule);
	const quiz = $derived(QUIZ.includes(family)); // everyone answers at once
	const boardMode = $derived(BOARD.includes(family)); // 솎솎 and 잇땅 are drawn on the square board layout
	const supported = $derived(['Classic', 'Hunmin', 'Daneo', 'Jaqwi', 'Typing', 'Mathquiz', 'Wordstack', 'Sock', 'Linkland', 'Crossword', 'Drawing'].includes(family));
	const reverse = $derived(['KAP', 'EAP'].includes(room?.modeKey));
	const kkt = $derived(['KKT', 'KFT'].includes(room?.modeKey));
	const parts = $derived(net.meta.moremiParts);
	const labels = $derived(net.meta.labels);

	// the board is kept out of sight until the veil has covered the screen, so it never shows through the arriving squares
	let veil = $state('none');
	const loadingNow = $derived(!g || g.phase === 'loading');
	const stageHidden = $derived(veil === 'in' || (loadingNow && veil === 'none'));
	// the waiting room stays on screen until the veil has covered it, then the board takes its place beneath the veil
	$effect(() => {
		store.ui.gameCovered = !stageHidden;
		return () => (store.ui.gameCovered = false);
	});
	// the stage is drawn at the legacy size and scaled to fill whatever room it is given, both ways
	const PATTERN = 400;
	const PATTERN_POLYGONS = patternPolygons(2);
	const STAGE_W = 1010;
	// the sketchbook wants more height than the legacy stage
	const STAGE_H = $derived(family === 'Drawing' ? 572 : 435);
	let areaW = $state(0);
	let areaH = $state(0);
	const scale = $derived(areaW && areaH ? Math.min(areaW / STAGE_W, areaH / STAGE_H) : 1);

	// ---- the players, in turn order ----
	const seats = $derived(
		(g?.seq?.length ? g.seq : (room?.players ?? []).filter((id) => net.users[id]?.game.form !== 'S'))
			.map((id) => net.users[id])
			.filter(Boolean)
	);
	const large = $derived(seats.length > 8);
	const currentId = $derived(family === 'Jaqwi' ? g?.winner : g?.phase === 'turn' ? g.current : null);

	// ---- the clocks (legacy turnGoing): lengths from the server, counted down on my own clock ----
	const roundMax = $derived((room?.time ?? 0) * 1000);
	let turnLeft = $state(0);
	let roundLeft = $state(0);
	let turnText = $state('');
	let roundText = $state('');
	let extreme = $state(false);
	const second = $derived(t('kkutu.js.second'));
	const clockText = (v) => `${(Math.max(0, v) * 0.001).toFixed(1)}${second}`;
	$effect(() => {
		if (g?.phase === 'loading' || g?.phase === 'ready') {
			turnLeft = 0;
			roundLeft = 0;
			extreme = false;
		}
	});
	$effect(() => {
		if (g?.phase !== 'turn') return;
		untrack(() => (extreme = false)); // a new turn starts with the plain round bar
		let fastHit = false;
		const at = g.turnAt;
		const turn = g.turnTime;
		const round = g.roundTime;
		let raf;
		const frame = () => {
			const used = Date.now() - at;
			turnLeft = turn - used;
			roundLeft = round - used;
			turnText = clockText(turnLeft);
			roundText = clockText(roundLeft);
			if (roundLeft <= (quiz ? 10000 : 5000) && !fastHit) {
				fastHit = true;
				extreme = true;
				if (quiz) playGameBgm('JaqwiFastBGM'); // legacy: the last ten seconds of a quiz round have a faster music
			}
			raf = requestAnimationFrame(frame);
		};
		frame();
		return () => cancelAnimationFrame(raf);
	});
	const turnPct = $derived(g?.turnTime > 0 ? (turnLeft / g.turnTime) * 100 : 0);
	const roundPct = $derived(roundMax ? (roundLeft / roundMax) * 100 : 0);
	const barStyle = (pct) => `width:${Math.max(0, pct)}%;${reverse ? 'float:right;text-align:left;' : ''}`;

	// ---- the display ----
	let timers = [];
	const later = (delay, fn) => timers.push(setTimeout(fn, delay));
	const clearTimers = () => {
		timers.forEach(clearTimeout);
		timers = [];
	};
	// a word's entry in the history is not cancelled by the next turn, only by a new round
	let historyTimers = [];
	const clearHistoryTimers = () => {
		historyTimers.forEach(clearTimeout);
		historyTimers = [];
	};
	$effect(() => () => {
		clearTimers();
		clearHistoryTimers();
	});

	// ---- JJo eats the words: his container opens (taller) for a letter or a word and shuts (the two timers, his teeth, meet) when it is eaten ----
	let mouthOpen = $state(false);
	// a long word makes the mouth taller (the text wraps) instead of being cut, up to the room around the mouth (22 px below it, and above it the stage's own empty top)
	let textH = $state(0);
	const MOUTH_MAX = 84; // two lines: the stage keeps this much room around the mouth, so it never covers the history
	const mouthH = $derived(Math.min(MOUTH_MAX, Math.max(40, textH)));
	let barH = $state(0);
	const BAR_OPEN = 120; // the bar with a one-line mouth, open
	let openV = $state(0); // how far the mouth is open (1 = fully); the spring may swing a little past either end
	const mouth = new SpringGroup({ v: 0 }, { v: { response: 0.34, dampingFraction: 0.5 } }, (vals) => (openV = vals.v));
	$effect(() => {
		mouth.to({ v: mouthOpen ? 1 : 0 });
	});
	$effect(() => () => mouth.destroy());
	$effect(() => {
		const phase = g?.phase;
		if (phase === 'turn' || phase === 'ready') mouthOpen = true;
		else if (phase === 'loading' || phase === 'ended') mouthOpen = false;
	});

	// a refused word: the letter on the display is struck through in red and blinks (legacy), the reason goes to the chat
	let failing = $state(false);
	$effect(() => {
		const f = g?.fail;
		if (!f) return;
		failing = true;
		const timer = setTimeout(() => (failing = false), 2000);
		return () => clearTimeout(timer);
	});
	// ... and it also ends when the turn is answered (a good word, or the time running out)
	$effect(() => {
		void g?.last;
		failing = false;
	});

	let displayEl = $state();
	let reveal = $state(null); // {chars: [{char, mission, on}]}: the whole word is laid out, each letter switched on at its beat
	let hint = $state(null);
	let history = $state([]); // newest first, at most 6
	let popups = $state([]);
	let bombs = $state({});
	let historyKey = 0;

	// A new round wipes the board (legacy clearBoard); a new turn the display.
	$effect(() => {
		const phase = g?.phase;
		if (phase === 'loading' || phase === 'ready') {
			untrack(() => {
				clearTimers();
				clearHistoryTimers();
				reveal = null;
				hint = null;
				history = [];
				popups = [];
				bombs = {};
			});
		}
	});
	$effect(() => {
		void g?.turnAt;
		untrack(() => {
			clearTimers();
			reveal = null;
			hint = null;
		});
	});

	// 타자 대결: a letter I typed that is not the one asked for is struck out in red on the word being typed
	const typed = $derived(store.typed);
	// Only finished letters are judged: while a syllable is still being composed (the last one), it is left alone.
	const judged = $derived(store.composing ? Math.max(0, typed.length - 1) : typed.length);
	const wrongAt = (i) => i < judged && typed[i] !== (display.first ?? '')[i];

	// A line of words is made smaller until it fits the mouth on one line (only the typing mode has this trait).
	function fit(node) {
		const run = () => {
			node.style.fontSize = '';
			node.style.whiteSpace = 'nowrap';
			const natural = node.scrollWidth;
			const room = node.clientWidth;
			node.style.whiteSpace = '';
			if (natural > room && room > 0) {
				const size = Math.max(14, Math.floor((30 * room) / natural));
				node.style.fontSize = `${size}px`;
				if (size <= 14) node.style.whiteSpace = 'normal'; // too long even then: it wraps and the mouth grows
				else node.style.whiteSpace = 'nowrap';
			}
		};
		run();
		return { update: () => queueMicrotask(run) };
	}

	// each letter of an eaten word zooms in from large to its size on a bouncy spring, in the middle of the mouth
	function zoom(node, on) {
		let spring;
		let raf = 0;
		let last = 0;
		const frame = (now) => {
			const moving = spring.update(Math.min((now - last) / 1000, 1 / 30));
			last = now;
			node.style.transform = `scale(${spring.value})`;
			raf = moving ? requestAnimationFrame(frame) : 0;
		};
		function run(v) {
			if (!v || spring) return;
			spring = new Spring(2.6, { response: 0.42, dampingFraction: 0.45 });
			spring.to(1);
			last = performance.now();
			raf = requestAnimationFrame(frame);
		}
		run(on);
		return { update: run, destroy: () => cancelAnimationFrame(raf) };
	}

	// What came of the turn: the word is spelled out (with its sounds), pulses, and joins the history; or the hint shows.
	$effect(() => {
		const last = g?.last;
		if (!last) return;
		untrack(() => {
			clearTimers();
			if (!last.ok) {
				bombs[last.id] = true;
				hint = splitHint(last.hint, [g.char, g.subChar], reverse);
				return;
			}
			const plan = planReveal({ text: last.value, turnTime: g.turnTime, speed: g.speed, mission: g.mission, kkt, reverse, en: rule?.lang === 'en' });
			const text = last.value;
			reveal = { chars: [...text].map((char) => ({ char, mission: char === g.mission, on: false })) };
			// Like the legacy client, each letter comes in on its beat (planReveal: the BEAT pattern of the word's length, one
			// tick = turn time / 96) with its sound; the word's place in the middle of the mouth is kept from the start, and
			// the letter zooms in there on a spring.
			for (const step of plan.steps) {
				later(step.at, () => {
					playSound(step.sound);
					if (step.mission) playSound('mission'); // legacy pushDisplay: the beat, and on the mission letter the mission sound too
					const pos = reverse ? text.length - 1 - step.index : step.index;
					reveal = { chars: reveal.chars.map((c, i) => (i === pos ? { ...c, on: true } : c)) };
				});
			}
			for (const p of plan.pulses) {
				if (p.sound) later(p.at, () => playSound(p.sound));
			}
			if (plan.endSound) later(plan.revealMs, () => playSound(plan.endSound));
			historyTimers.push(
				setTimeout(() => {
					mouthOpen = false; // the word is eaten
					history = [{ key: ++historyKey, word: last.value, mean: processWord(last.mean, last.theme, labels), wcs: wordClasses(last.wc) }, ...history].slice(0, 6);
				}, plan.historyAt)
			);
		});
	});
	// the word classes (명사, 동사, …), each once
	const wordClasses = (wc) => [...new Set(String(wc ?? '').split(',').filter(Boolean))].map((c) => labels[`word.class.${c}`]).filter(Boolean);

	// 타자 / 수학 대결: the line of words to type, the first in yellow; what comes after it rides in the upper teeth
	const lv = $derived(room?.opts?.proverb || room?.modeKey === 'KMT' || room?.opts?.longtext ? 1 : 5);
	const listLeft = $derived((g?.list ?? []).slice(g?.chain ?? 0));
	const jqTheme = $derived(`${t('kkutu.js.jqTheme')}: ${labels[`word.theme.${g?.theme}`] ?? g?.theme ?? ''}`);
	// the legacy rest between rounds of the typing modes: ten seconds, counted down
	let rest = $state(0);
	$effect(() => {
		if (g?.phase !== 'between' || !['Typing', 'Mathquiz'].includes(family) || g.round >= (room?.rounds ?? 0)) {
			rest = 0;
			return;
		}
		rest = 10;
		const timer = setInterval(() => (rest = Math.max(0, rest - 1)), 1000);
		return () => clearInterval(timer);
	});
	const poolFull = $derived((g?.pool?.length ?? 0) > 7);
	const teethText = $derived(
		family === 'Wordstack' ? `${g?.pool?.length ?? 0}/8` : family === 'Jaqwi' ? jqTheme : family === 'Typing' || family === 'Mathquiz' ? (rest > 0 ? `${rest}${plainText(t('kkutu.js.afterRun'))}` : listLeft.slice(lv, 2 * lv).join(' ')) : turnText
	);

	const display = $derived.by(() => {
		if (!g || g.phase === 'loading') return { kind: 'empty' };
		if (family === 'Jaqwi') {
			if (g.answer) return { kind: 'answer', text: g.answer };
			return { kind: 'text', text: g.phase === 'turn' ? g.char : jqTheme, length: 0 };
		}
		if (family === 'Wordstack') return { kind: 'pieces' };
		if (family === 'Typing' || family === 'Mathquiz') {
			if (g.phase === 'ended') return { kind: 'text', text: t('kkutu.js.roundEnd') };
			return { kind: 'list', first: listLeft[0] ?? '', rest: listLeft.slice(1, lv).join(' ') };
		}
		if (failing && g.submitted) return { kind: 'fail', text: g.submitted }; // my refused word, struck out
		if (reveal) return { kind: 'reveal', ...reveal };
		if (hint) return { kind: 'hint', ...hint };
		if (g.phase === 'ended') return { kind: 'text', text: t('kkutu.js.roundEnd') };
		// 훈민정음 and 단어 대결 show the round's theme where the word chain shows its letter
		if (family === 'Hunmin') return { kind: 'text', text: `<${g.theme}>`, length: 0 };
		if (family === 'Daneo') return { kind: 'text', text: `<${labels[`word.theme.${g.theme}`] ?? g.theme}>`, length: 0 };
		return { kind: 'text', text: charText(g.char, g.subChar), length: g.phase === 'turn' ? g.wordLength : 0 };
	});

	// ---- the cards ----
	let shown = $state({});
	$effect(() => {
		const goals = Object.fromEntries(seats.map((u) => [u.id, Number(u.game.score) || 0]));
		let raf;
		const frame = () => {
			let busy = false;
			const next = { ...untrack(() => shown) };
			for (const [id, goal] of Object.entries(goals)) {
				const now = Number.isFinite(next[id]) ? next[id] : 0;
				if (Math.abs(goal - now) < 1) next[id] = goal;
				else {
					next[id] = now + (goal - now) * 0.2;
					busy = true;
				}
			}
			shown = next;
			if (busy) raf = requestAnimationFrame(frame);
		};
		frame();
		return () => cancelAnimationFrame(raf);
	});

	// changes that were already there when the board opened (a spectator walking in) don't float again
	const seen = Object.fromEntries(Object.entries(untrack(() => store.game?.deltas) ?? {}).map(([k, v]) => [k, v.seq]));
	$effect(() => {
		const d = g?.deltas;
		if (!d) return;
		const fresh = [];
		for (const [key, v] of Object.entries(d)) {
			if (seen[key] === v.seq) continue;
			seen[key] = v.seq;
			fresh.push([key, v]);
		}
		untrack(() => {
			for (const [key, v] of fresh) {
				const bonus = key.endsWith(':bonus');
				later(bonus ? 500 : 0, () => {
					const popup = { key: `${v.seq}:${key}`, id: key.split(':')[0], text: v.text, kind: bonus ? 'bonus' : v.kind };
					popups = [...popups, popup];
					setTimeout(() => (popups = popups.filter((p) => p.key !== popup.key)), 2000);
				});
			}
		});
	});

	// the card of whoever is up scrolls into view (legacy: -13 px)
	let body = $state();
	$effect(() => {
		const id = currentId;
		if (!id || !body) return;
		tick().then(() => {
			const card = body?.querySelector(`[data-player="${CSS.escape(id)}"]`);
			if (card) body.scrollTop = card.offsetTop - body.offsetTop - 13;
		});
	});

	// ---- the answer: typed into the field laid over the history on my turn ----
	let value = $state('');
	let field = $state();
	const canType = $derived(store.myTurn);
	$effect(() => {
		if (!canType) return;
		value = '';
		tick().then(() => field?.focus());
	});
	function keydown(e) {
		if (store.spaceSubmits(e, value, () => (value = ''))) return;
		if (e.key === 'Enter' && !e.isComposing) {
			e.preventDefault();
			if (store.submitWord(value)) value = '';
		} else store.telemetry.input(field?.value ?? '');
	}

	// ---- 워드스택: a piece that has been held for 14 s pulses; pieces fly between the players' cards ----
	let tickNow = $state(Date.now());
	$effect(() => {
		if (family !== 'Wordstack') return;
		const timer = setInterval(() => (tickNow = Date.now()), 500);
		return () => clearInterval(timer);
	});
	const pulsing = (i) => tickNow - (g?.poolAt ?? tickNow) + Number(g?.pieceAges?.[i] ?? 0) >= 14000;

	function flyPiece(fromId, toId, piece) {
		const target = document.getElementById(`game-user-${toId}`);
		if (!target || !piece) return;
		const source = fromId && document.getElementById(`game-user-${fromId}`);
		const tr = target.getBoundingClientRect();
		const sr = source ? source.getBoundingClientRect() : tr;
		const sx = source ? sr.left + sr.width / 2 : tr.left + tr.width / 2;
		const sy = source ? sr.top + sr.height / 2 : tr.top - 34;
		const ex = tr.left + tr.width / 2;
		const ey = tr.top + tr.height / 2;
		const node = document.createElement('div');
		node.className = 'wordstack-flying-piece';
		node.textContent = piece;
		node.style.left = `${sx - 17}px`;
		node.style.top = `${sy - 17}px`;
		document.body.appendChild(node);
		const dx = ex - sx;
		const dy = ey - sy;
		if (node.animate) {
			const flight = node.animate(
				[
					{ transform: 'translate(0, 0) scale(.75) rotate(-12deg)', opacity: 0.15 },
					{ transform: `translate(${dx * 0.5}px, ${dy * 0.5 - 42}px) scale(1.18) rotate(8deg)`, opacity: 1, offset: 0.5 },
					{ transform: `translate(${dx}px, ${dy}px) scale(.82) rotate(0deg)`, opacity: 0.1 }
				],
				{ duration: ms(720), easing: 'cubic-bezier(.2,.75,.3,1)' }
			);
			flight.onfinish = () => node.remove();
		} else {
			node.style.transform = `translate(${dx}px, ${dy}px)`;
			node.style.opacity = '0';
			setTimeout(() => node.remove(), 750);
		}
	}
	const flown = new Set(untrack(() => (store.game?.throws ?? []).map((x) => x.key)));
	$effect(() => {
		for (const th of g?.throws ?? []) {
			if (flown.has(th.key)) continue;
			flown.add(th.key);
			setTimeout(() => flyPiece(th.from, th.to, th.piece), th.delay);
		}
	});

	// ---- 그림퀴즈: the painter has the canvas and the tools, everyone else the picture and the hints ----
	const painting = $derived(family === 'Drawing' && g?.painter === net.id);
	let clearToken = $state(0);
	// each round turns the page: the old picture swings up over the rings and the new, blank page is under it
	let lastImage = '';
	let flip = $state(null);
	let seenRound = 0;
	$effect(() => {
		if (family === 'Drawing' && g?.canvasImage) lastImage = g.canvasImage;
	});
	$effect(() => {
		const r = family === 'Drawing' ? (g?.round ?? 0) : 0;
		untrack(() => {
			if (r && seenRound && r !== seenRound) {
				flip = { key: r, image: lastImage };
				lastImage = '';
			}
			seenRound = r;
		});
	});
	const blanks = $derived.by(() => {
		const n = g?.answerLength ?? 0;
		return n > 11 ? `${n}${t('kkutu.js.cwL')}` : '○'.repeat(n);
	});

	// ---- 십자말풀이: the bars of the round on show, and the one being answered ----
	const cwIdx = $derived((g?.selectedRound ?? 1) - 1);
	const cwBars = $derived(g?.boards?.[cwIdx] ?? []);
	// a solved word shows its letters in every bar that crosses it
	const cwLetters = $derived.by(() => {
		const out = {};
		for (const [x, y, dir, len, word] of cwBars) {
			if (!word) continue;
			for (let j = 0; j < len; j++) out[dir == 1 ? `${x}-${Number(y) + j}` : `${Number(x) + j}-${y}`] = word.charAt(j);
		}
		return out;
	});
	const cwCell = (x, y, dir, j) => (dir == 1 ? `${x}-${Number(y) + j}` : `${Number(x) + j}-${y}`);
	const cwMean = $derived(g?.sel ? g.means?.[g.sel[0]]?.[`${g.sel[1]},${g.sel[2]},${g.sel[3]}`] : null);
	let cwValue = $state('');
	let cwInput = $state();
	function cwPick(bar) {
		const [x, y, dir] = bar;
		store.selectBar([cwIdx, Number(x), Number(y), Number(dir)]);
		cwValue = '';
		tick().then(() => cwInput?.focus());
	}
	function cwKey(e) {
		if (e.key !== 'Enter' || e.isComposing) return;
		e.preventDefault();
		if (!cwValue || !g?.sel) return (cwValue = '');
		if (store.submitWord(cwValue, g.sel)) cwValue = '';
	}

	// ---- 솎솎: the words found, longest first; the cells just taken fade out ----
	const sortedMaps = $derived([...(g?.maps ?? [])].map((text, i) => ({ text, i })).sort((a, b) => b.text.length - a.text.length || a.i - b.i));
	const pickedAt = $derived(Object.fromEntries((g?.picked ?? []).map((x) => [x.index, x])));
	// ---- 잇땅: the colour of a cell is its owner's place in the order ----
	const ownerClass = (i) => {
		const owner = g?.owners?.[i];
		const idx = seatIdx.get(owner);
		return idx === undefined ? '' : `linkland-owner-${idx % 10}`;
	};
	const seatIdx = $derived(new Map((g?.seq ?? []).map((id, i) => [id, i])));

	function rise(node, { duration = 520 } = {}) {
		return { duration: ms(duration), easing: quintOut, css: (t, u) => `transform: translateY(${u * 100}%)` };
	}
</script>

{#snippet meaning(m)}
	<span class="word">
		{#if m.plain !== undefined}{m.plain}
		{:else}
			{#each m.groups as grp (grp.index)}
				{#if m.numbered && grp.index !== 0}<br />{/if}
				<span class="word-m1">
					{#if m.numbered}<span class="word-head word-m1-head">{grp.head}</span>{/if}
					{#each grp.subs as sub, si (si)}
						<span class="word-m2">
							{#if sub.head}<span class="word-head word-m2-head">{sub.head}</span>{/if}
							{#each sub.items as it, ii (ii)}
								<span class="word-m3">
									{#if it.head}<span class="word-head word-m3-head">{it.head}</span>{/if}
									{#if it.theme}<span class="word-theme {it.theme.kind === 'special' ? 'word-special' : it.theme.kind === 'injeong' ? 'word-injeong' : ''}">{it.theme.name}</span>{/if}
									<span class="word-m3-body">{@html it.html}</span>
								</span>
							{/each}
						</span>
					{/each}
				</span>
			{/each}
		{/if}
	</span>
{/snippet}

{#snippet roster()}
	{#each seats as p (p.id)}
		{@const hidden = !p.robot && p.id !== net.id}
		<div id="game-user-{p.id}" data-player={p.id} role="button" tabindex="0" onclick={() => store.hooks.openProfile?.(p.id)} onkeydown={(e) => (e.key === 'Enter' || e.key === ' ') && (e.preventDefault(), store.hooks.openProfile?.(p.id))} class="game-user {bombs[p.id] || g?.gaveUp?.[p.id] ? 'game-user-bomb' : ''} {family === 'Drawing' && g?.painter === p.id ? 'game-user-drawing' : ''} {family === 'Drawing' && g?.winner === p.id ? 'game-user-current' : ''}">
			<div class="game-user-level"><LevelIcon level={p.robot ? 1 : p.lv} size={18} maskable={hidden} /></div>
			<div class="game-user-name ellipse"><NickName user={p} showCheck maskable={hidden} /></div>
			<div class="game-user-score {p.game.team ? `team-${p.game.team}` : ''}" data-score data-value={Math.round(p.game.score ?? 0)}>
				{#each scoreChars(shown[p.id] ?? 0) as c, i (i)}<div class="game-user-score-char">{c}</div>{/each}
			</div>
			{#each popups.filter((x) => x.id === p.id) as x (x.key)}<div class="deltaScore {x.kind}" data-kind={x.kind}>{x.text}</div>{/each}
		</div>
	{/each}
{/snippet}

{#snippet players()}
							{#each seats as p (p.id)}
								{@const team = p.game.team}
								{@const hidden = !p.robot && p.id !== net.id}
								{#snippet who()}{t('kkutu.js.level')} {p.lv}<br />{p.name}{p.tag ? `#${p.tag}` : ''}{/snippet}
								<div id="game-user-{p.id}" data-player={p.id} role="button" tabindex="0" onclick={() => store.hooks.openProfile?.(p.id)} onkeydown={(e) => (e.key === 'Enter' || e.key === ' ') && (e.preventDefault(), store.hooks.openProfile?.(p.id))} aria-current={currentId === p.id ? 'true' : undefined} class="game-user {currentId === p.id || (family === 'Drawing' && g?.winner === p.id) ? 'game-user-current' : ''} {family === 'Drawing' && g?.painter === p.id ? 'game-user-drawing' : ''} {bombs[p.id] || g?.gaveUp?.[p.id] ? 'game-user-bomb' : ''} {large ? 'large-room' : ''}">
									<div class="moremi game-user-image"><Moremi equip={p.equip} shop={store.shop} {parts} size={large ? 76 : 100} label={p.name} frame={false} maskable={hidden} /></div>
									<Tooltip content={who} placement="bottom">
										<div class="game-user-title">
											<div class="game-user-level"><LevelIcon level={p.robot ? 1 : p.lv} size={18} maskable={hidden} /></div>
											<div class="game-user-name ellipse"><NickName user={p} showCheck maskable={hidden} /></div>
										</div>
									</Tooltip>
									<div class="game-user-score {team ? `team-${team}` : ''}" data-score data-value={Math.round(p.game.score ?? 0)}>
										{#if g?.kpm?.[p.id] !== undefined}
											<div style="float:none;color:#4444FF;text-align:center">{g.kpm[p.id]}<span style="font-size:11px">{plainText(t('kkutu.js.kpm'))}</span></div>
										{:else}
											{#each scoreChars(shown[p.id] ?? 0) as c, i (i)}<div class="game-user-score-char">{c}</div>{/each}
										{/if}
									</div>
									{#each popups.filter((x) => x.id === p.id) as x (x.key)}<div class="deltaScore {x.kind}" data-kind={x.kind}>{x.text}</div>{/each}
									<ChatBalloon {store} id={p.id} class="top-3 z-20 max-w-[104px]! px-2! py-1! text-xs!" />
								</div>
							{/each}
						{/snippet}

{#if room}
	<GameLoading loading={loadingNow} bind:stage={veil} />
	<section aria-label={t('kkutu.product.game.title')} in:rise|global class="flex min-h-0 flex-1 flex-col items-center" style:visibility={stageHidden ? 'hidden' : undefined} style:position={stageHidden ? 'absolute' : undefined} style:width={stageHidden ? '100%' : undefined}>
		{#if !supported}
			<div class="kk-card grid w-full place-items-center p-6 text-center font-bold text-(color:--kk-muted)">{t('play.game.unsupported')}</div>
		{:else}
			<div class="kkg-backdrop kk-card relative grid min-h-0 w-full flex-1 place-items-center overflow-hidden" bind:clientWidth={areaW} bind:clientHeight={areaH}>
				<svg class="absolute inset-0 size-full" aria-hidden="true">
					<defs>
						<pattern id="kk-game-tile" width={PATTERN} height={PATTERN} patternUnits="userSpaceOnUse">
							{#each PATTERN_POLYGONS as pg, i (i)}<polygon class={pg.dark ? 'kk-gt-dark' : 'kk-gt-light'} points={pg.points} />{/each}
						</pattern>
					</defs>
					<rect width="100%" height="100%" fill="url(#kk-game-tile)" />
				</svg>
				<div style:width="{Math.floor(STAGE_W * scale)}px" style:height="{Math.floor(STAGE_H * scale)}px">
				<div class="kkg" style:transform="scale({scale})" style:transform-origin="0 0">
					{#if family === 'Drawing'}
						<div class="sketch" data-sketch>
							<div class="sk-top">
							<div class="sk-side sk-left">
								<div class="sk-info" data-display aria-live="polite">
									{#if g?.answer}
										<div class="sk-word sk-answer">{g.answer}</div>
									{:else}
										<div class="sk-theme">{jqTheme}</div>
										<div class="sk-word">{painting ? (g?.painterWord ?? '') : blanks}</div>
									{/if}
								</div>
								{#if painting}
									<DrawingTools side="palette" />
								{:else}
									<div class="sk-panel sk-hints" data-hints>{#each g?.hints ?? [] as hint, i (i)}<div class="sk-hint">{@render meaning(processWord(hint, '', labels))}</div>{/each}</div>
								{/if}
							</div>
							<div class="sk-book">
								<svg class="sk-coil" viewBox="0 0 396 60" aria-hidden="true">
									{#each [96, 300] as x (x)}
										<circle cx={x} cy="46" r="9" fill="#052e16" />
										<line x1={x - 6} y1="46" x2={x - 6} y2="12" stroke="#e5e7eb" stroke-width="5" stroke-linecap="round" />
										<line x1={x + 6} y1="46" x2={x + 6} y2="12" stroke="#e5e7eb" stroke-width="5" stroke-linecap="round" />
									{/each}
								</svg>
								<div class="sk-page">
									{#if g && g.phase !== 'loading'}
										{#if painting}
											{#key g.round}<DrawingCanvas {store} {clearToken} onpicture={(url) => (lastImage = url)} />{/key}
										{:else}
											<img class="sk-picture" alt="" draggable="false" src={g.canvasImage || 'data:image/gif;base64,R0lGODlhAQABAIAAAP///wAAACH5BAEAAAAALAAAAAABAAEAAAICRAEAOw=='} />
										{/if}
									{/if}
								</div>
								{#if flip}{#key flip.key}
									<div class="sk-flip" aria-hidden="true" onanimationend={() => (flip = null)}>
										<div class="sk-flip-front">{#if flip.image}<img alt="" src={flip.image} draggable="false" />{/if}</div>
										<div class="sk-flip-back"></div>
									</div>
								{/key}{/if}
								<div class="sk-stack" aria-hidden="true"></div>
								<div class="sk-time" role="progressbar" aria-label={t('kkutu.js.rounds')} aria-valuemin="0" aria-valuemax="100" aria-valuenow={Math.round(Math.max(0, roundPct))}>
									<div class="sk-time-bar {extreme ? 'round-extreme' : ''}" style="width:{Math.max(0, Math.min(100, roundPct))}%"></div><span>{roundText}</span>
								</div>
							</div>
							<div class="sk-side">
								{#if painting}<DrawingTools side="tools" onclear={() => clearToken++} />{/if}
							</div>
							</div>
							<div class="game-body sk-players">{@render players()}</div>
						</div>
					{:else if boardMode}
						<div class="product-body kkg-cw {family === 'Linkland' ? 'kkg-ll' : ''}">
							<div class="kkg-cw-left">
								{#if family === 'Crossword'}
									<div class="b-left cwcmd" style:opacity={g?.sel ? 1 : 0} data-cwcmd>
										<div class="cw-q-head">{#if cwMean}{t(cwMean.dir == 1 ? 'kkutu.js.cwVert' : 'kkutu.js.cwHorz')}{cwMean.len}{t('kkutu.js.cwL')}{/if}</div>
										<input bind:this={cwInput} bind:value={cwValue} onkeydown={cwKey} onblur={() => store.selectBar(null)} autocomplete="off" spellcheck="false" aria-label={t('kkutu.product.game.title')} placeholder={t('kkutu.product.game.inputHere')} id="cw-q-input" />
										<div class="cw-q-body">{#if cwMean}{@render meaning(processWord(cwMean.mean, cwMean.theme, labels))}{/if}</div>
									</div>
								{:else}
								<div class="b-left bb" data-words>
									{#if family === 'Sock'}
										{#each sortedMaps as w (w.i)}<div class="bb-word">{#each [...w.text] as ch, k (k)}<div class="bb-char">{ch}</div>{/each}</div>{/each}
									{:else}
										{#each g?.words ?? [] as w, k (`${k}:${w}`)}<div class="bb-word linkland-word">{w}</div>{/each}
									{/if}
								</div>
								{/if}
								{#if family === 'Linkland'}<div class="game-body cw">{@render roster()}</div>{/if}
							</div>
							<div class="jjoriping cw">
								<img class="jjoObj jjoEyeL" src="{IMAGE_CDN}jjoeyeL.svg{IMAGE_VERSION}" alt="" /><img class="jjoObj jjoNose" src="{IMAGE_CDN}jjonose.svg{IMAGE_VERSION}" alt="" /><img class="jjoObj jjoEyeR" src="{IMAGE_CDN}jjoeyeR.svg{IMAGE_VERSION}" alt="" />
								<div class="jjoDisplayBar">
									<div class="graph jjo-turn-time"><div class="graph-bar" style="width:100%"></div></div>
									<div class="jjo-display" data-display aria-live="polite" data-board>
										{#if g && g.phase !== 'loading'}
											{#if family === 'Crossword'}
												{#each cwBars as bar (`${cwIdx}-${bar[0]}-${bar[1]}-${bar[2]}`)}
													{@const x = Number(bar[0])}
													{@const y = Number(bar[1])}
													{@const vert = bar[2] == 1}
													{@const len = Number(bar[3])}
													{@const mine = bar[5] === net.id}
													<div
														class="cw-bar {bar[4] ? 'cw-open' : ''} {mine ? 'cw-my-open' : ''}"
														role="button"
														tabindex={bar[4] || mine ? -1 : 0}
														aria-label="{t(vert ? 'kkutu.js.cwVert' : 'kkutu.js.cwHorz')} {len}{t('kkutu.js.cwL')}"
														style="top:{y * 12.5}%;left:{x * 12.5}%;width:{(vert ? 1 : len) * 12.5}%;height:{(vert ? len : 1) * 12.5}%"
														onclick={() => !bar[4] && !mine && cwPick(bar)}
														onkeydown={(e) => e.key === 'Enter' && !bar[4] && !mine && cwPick(bar)}
													>
														{#each Array(len) as _, j (j)}<div class="cw-cell" data-cw-cell={cwCell(x, y, bar[2], j)}>{cwLetters[cwCell(x, y, bar[2], j)] ?? ''}</div>{/each}
													</div>
												{/each}
											{:else if family === 'Sock'}
												{#each [...(g.board ?? '')] as ch, i (i)}
													<div class="sock-char" style:width={rule?.lang === 'ko' ? '12.5%' : '10%'} style:height={rule?.lang === 'ko' ? '12.5%' : '10%'}>
														{#if ch !== '　' && ch !== ' '}{ch}{:else if pickedAt[i]}{#key pickedAt[i].key}<span class="sock-picked sock-picked-anim">{pickedAt[i].char}</span>{/key}{/if}
													</div>
												{/each}
											{:else}
												<div class="linkland-board">
													{#each [...(g.board ?? '')] as ch, i (i)}<div class="linkland-cell {ownerClass(i)}" style:width="{100 / (g.boardSize || 11)}%" style:height="{100 / (g.boardSize || 11)}%">{ch}</div>{/each}
												</div>
											{/if}
										{/if}
									</div>
									<div class="graph jjo-round-time" role="progressbar" aria-label={t('kkutu.js.rounds')} aria-valuemin="0" aria-valuemax="100" aria-valuenow={Math.round(Math.max(0, roundPct))}>
										<div class="graph-bar {extreme ? 'round-extreme' : ''}" style={barStyle(roundPct)}>{roundText}</div>
									</div>
								</div>
							</div>
							{#if family === 'Sock' || family === 'Crossword'}<div class="game-body cw kkg-cw-right">{@render roster()}</div>{/if}
						</div>
					{:else}
					<div class="product-body">
						{#if family === 'Jaqwi'}
							<div class="hints">
								{#each g?.hints ?? [] as hint, i (i)}<div class="hint-item kkg-hist-in-h">{@render meaning(processWord(hint, '', labels))}</div>{/each}
							</div>
						{:else}
							<div class="items" style:opacity={room.opts?.mission && g?.mission ? 1 : 0}>{g?.mission ?? ''}</div>
						{/if}
						<div class="jjoriping" style:transform="translateY({(BAR_OPEN - barH) / 2}px)">
							<img class="jjoObj jjoEyeL" src="{IMAGE_CDN}jjoeyeL.svg{IMAGE_VERSION}" alt="" /><img class="jjoObj jjoNose" src="{IMAGE_CDN}jjonose.svg{IMAGE_VERSION}" alt="" /><img class="jjoObj jjoEyeR" src="{IMAGE_CDN}jjoeyeR.svg{IMAGE_VERSION}" alt="" />
							<div class="jjoDisplayBar" bind:offsetHeight={barH}>
								<div class="graph jjo-turn-time" role="progressbar" aria-label={t('kkutu.js.second')} aria-valuemin="0" aria-valuemax="100" aria-valuenow={Math.round(Math.max(0, turnPct))}>
									<div class="graph-bar {family === 'Wordstack' && poolFull ? 'round-warning' : ''}" style={family === 'Wordstack' ? `width:${poolFull ? 100 : ((g?.pool?.length ?? 0) / 8) * 100}%` : quiz ? 'width:100%;text-align:center' : barStyle(turnPct)}>{teethText}</div>
								</div>
								<div class="jjo-display" data-display bind:this={displayEl} aria-live="polite" style:height="{Math.max(0, mouthH * openV)}px" style:padding-top="{Math.max(0, 8 * openV)}px" style:padding-bottom="{Math.max(0, 8 * openV)}px" style:overflow={openV > 0.98 && textH <= MOUTH_MAX ? 'visible' : 'hidden'}>
									<div class="jjo-text" bind:clientHeight={textH}>
										{#if display.kind === 'fail'}<span class="game-fail-text">{display.text}</span>
										{:else if display.kind === 'reveal'}
											{#each display.chars as c, i (i)}<span class="jjo-ch" use:zoom={c.on} style:opacity={c.on ? 1 : 0} style:color={c.mission ? '#66FF66' : undefined}>{c.char}</span>{/each}
										{:else if display.kind === 'pieces'}
											{#each g?.pool ?? [] as piece, i (i)}<span class="wordstack-piece {pulsing(i) ? 'wordstack-piece-pulse' : ''}">{piece}{#if g.subpool?.[i]}<span class="jjo-display-word-length">{g.subpool[i]}</span>{/if}</span>{' '}{/each}
										{:else if display.kind === 'answer'}<span style="color:#FFFF44">{display.text}</span>
										{:else if display.kind === 'list'}
											<div class="jjo-fit" use:fit={display.first + display.rest}>
												<span style="color:#FFFF44">{#each [...display.first] as ch, i (i)}<span class={family === 'Typing' && wrongAt(i) ? 'jjo-wrong' : ''}>{ch}</span>{/each}</span>{#if display.rest}{' '}<span>{display.rest}</span>{/if}
											</div>
										{:else if display.kind === 'hint'}
											{#if reverse}<span style="color:#AAAAAA">{display.dim}</span><span>{display.bright}</span>{:else}<span>{display.bright}</span><span style="color:#AAAAAA">{display.dim}</span>{/if}
										{:else if display.kind === 'text'}{display.text}{#if display.length}<span class="jjo-display-word-length">({display.length})</span>{/if}{/if}
									</div>
								</div>
								<div class="graph jjo-round-time" role="progressbar" aria-label={t('kkutu.js.rounds')} aria-valuemin="0" aria-valuemax="100" aria-valuenow={Math.round(Math.max(0, roundPct))}>
									<div class="graph-bar {extreme ? 'round-extreme' : ''}" style={barStyle(roundPct)}>{roundText}</div>
								</div>
							</div>
						</div>
						<div class="chain" data-chain style:display={g && g.phase !== 'loading' && family !== 'Jaqwi' ? 'block' : 'none'}>{family === 'Wordstack' ? (g?.pool?.length ?? 0) : (g?.chain ?? 0)}</div>
						<div class="history-holder">
							<div class="history" data-history>
								{#if quiz}
									{#each g?.history ?? [] as h (h.key)}
										{#if h.raw}
											{@const mean = processWord(h.mean, h.theme, labels)}
											{#snippet rawTip()}<h5 style="color:#BBBBBB">{@render meaning(mean)}</h5>{/snippet}
											<Tooltip content={rawTip} placement="bottom">
												<div class="ellipse history-item kkg-hist-in" data-word={h.word}>
													{h.word}{#each wordClasses(h.wc) as c (c)}<span class="history-class">{c}</span>{/each}
													<div class="history-mean ellipse">{@render meaning(mean)}</div>
												</div>
											</Tooltip>
										{:else}
											<div class="ellipse history-item kkg-hist-in" data-word={h.word}>
												{#if h.mark}<Icon name={h.mark === 'ok' ? 'check' : 'block'} size={16} class="align-[-3px]" />{/if}{h.word}
												{#if h.mean}<div class="history-mean ellipse">{h.mean}</div>{/if}
											</div>
										{/if}
									{/each}
								{/if}
								{#each history as h (h.key)}
									{#snippet tip()}<h5 style="color:#BBBBBB">{@render meaning(h.mean)}</h5>{/snippet}
									<Tooltip content={tip} placement="bottom">
										<div class="ellipse history-item kkg-hist-in" data-word={h.word}>
											{h.word}{#each h.wcs as c (c)}<span class="history-class">{c}</span>{/each}
											<div class="history-mean ellipse">{@render meaning(h.mean)}</div>
										</div>
									</Tooltip>
								{/each}
							</div>
						</div>
						<div class="game-body" bind:this={body}>{@render players()}</div>
						<div class="game-input" style:display={canType ? 'block' : 'none'}>
							<input id="game-input" bind:this={field} bind:value oninput={(e) => store.setTyped(value, e.isComposing)} oncompositionend={() => store.setTyped(value)} onkeydown={keydown} autocomplete="off" autocorrect="off" autocapitalize="off" spellcheck="false" aria-label={t('kkutu.product.game.title')} placeholder={t('kkutu.product.game.inputHere')} />
						</div>
					</div>
					{/if}
				</div>
				</div>
			</div>
		{/if}
	</section>
	<GameResult {store} />
{/if}

<style>
	:global(.kkg .kkg-hist-in) {
		animation: kkg-hist-in 0.5s ease-out both;
	}
	@keyframes kkg-hist-in {
		from { width: 0; }
		to { width: 200px; }
	}
	:global(.kkg .kkg-hide-room .room-head-number, .kkg .kkg-hide-room .room-head-title) { visibility: hidden; }
	:global(.kkg .kkg-hide-room:hover .room-head-number, .kkg .kkg-hide-room:hover .room-head-title) { visibility: visible; }
	@media (prefers-reduced-motion: reduce) {
		:global(.kkg .kkg-hist-in) { animation: none; width: 200px; }
	}
</style>
