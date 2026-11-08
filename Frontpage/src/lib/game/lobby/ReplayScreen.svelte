<script>
	import { onMount, untrack } from 'svelte';
	import ChipRow from './ChipRow.svelte';
	import Moremi from './Moremi.svelte';
	import NickName from './NickName.svelte';
	import { lookupUsers } from '../friendSearch.js';
	import { fly } from 'svelte/transition';
	import { quintOut } from 'svelte/easing';
	import { t, i18n } from '../i18n.svelte.js';
	import { ms } from '../motion.js';
	import { toast } from '../ui/toast.svelte.js';
	import { fetchHistory, historyOutcome, timeAgo } from '../profile.js';
	import { academyApi } from '../academy/api.js';
	import { decodeKkio, sanitizeRecording } from '../kkio.js';
	import { summarize } from '../replayPayload.js';
	import { buildReplayView, decodeReplayPayload } from '../../replayView.js';
	import { recFromRecord, replaySupport } from '../replayFromRecord.js';
	import { plainText } from '../protocol.js';
	import Icon from './Icon.svelte';
	import EmptyState from './EmptyState.svelte';
	import PlacementMedal from './PlacementMedal.svelte';
	import ReplayInfo from './ReplayInfo.svelte';
	import ReplayPlayer from './ReplayPlayer.svelte';
	import ReplaySummary from './ReplaySummary.svelte';
	import ShareDialog from './ShareDialog.svelte';

	/**
	 * The 리플레이 page. Left: where a replay comes from (the game I just played, my games on the server, any game by its ID or a player's
	 * name, a .kkio file from my computer). Right: what that holds. A recording (.kkio) plays on the real game screen; a game from the
	 * server's records opens as a sheet (the server keeps a summary of each game, not its packets).
	 * A file from my computer is only watched here: it cannot be put on the server.
	 * It is the same page in the game client and on the portal (/records); `store` is the lobby store there, a small host (replayHost.js) here.
	 * `start`: what to open (a game, or a player's games: `user` is an id, or a nickname when `userType` is 'nick'); `onroute(gameId|null)` lets the
	 * host keep the address in step (a game's link can be shared); `shareLink(gameId)` makes that link.
	 * @type {{store:any, start?:{game:string, user?:string, userType?:string, n:number}, onroute?:((id:string|null)=>void)|null, shareLink?:((id:string)=>string)|null}}
	 */
	let { store, start = { game: '', user: '', userType: '', n: 0 }, onroute = null, shareLink = null } = $props();
	const net = $derived(store.net);
	const guest = $derived(net.guest);
	// Members: their own games first. Anonymous visitors have no games of their own to replay: a game by its ID, or a file.
	const SIDE = $derived([
		...(guest ? [] : [{ id: 'mine', icon: 'video_library', label: t('play.replay.side.mine') }]),
		{ id: 'find', icon: 'search', label: t('play.replay.side.find') },
		{ id: 'file', icon: 'folder_open', label: t('play.replay.side.file') }
	]);
	let section = $state('file');
	let playing = $state(null); // {rec}
	let sheet = $state(null); // a server game, summarised
	let dbPlay = $state(null); // ... and the recording built from it, when its mode can be played (the same player as a .kkio file)
	let file = $state(null); // {rec, name} a .kkio that was read
	let fileError = $state('');
	let dragging = $state(false);
	let rows = $state([]);
	let page = $state(1);
	let hasNext = $state(false);
	let loading = $state(false);
	let query = $state('');
	let note = $state('');
	let findType = $state('game'); // what the search text is: a game's ID, or a player's 별명 (or 식별번호)
	let cand = $state({ status: 'idle', users: [], page: 1, hasNext: false }); // the players that match what is typed
	let picked = $state(null); // the player whose games are shown
	let found = $state(null); // {id, rows, page, hasNext}: their games
	let autoPick = false; // opened from a link: the one player it names is picked as soon as the answer is here
	let seq = 0;
	let timer = 0;
	const modeName = (key) => i18n.messages[`game.mode.${key}.name`] ?? key;

	onMount(() => {
		section = guest ? 'find' : 'mine';
	});
	// a game asked for from outside (a profile's 경기 내역)
	let seen = untrack(() => start.n);
	$effect(() => {
		if (start.n === seen) return;
		seen = start.n;
		playing = sheet = dbPlay = null;
		go(start);
	});
	onMount(() => {
		if (start.game || start.user) go(start);
	});
	function go(st) {
		if (st.game) openGame(st.game);
		else if (st.user) {
			section = 'find';
			findType = 'nick';
			picked = found = null;
			autoPick = true;
			query = st.user;
		} else section = guest ? 'find' : 'mine';
	}
	$effect(() => {
		if (section === 'mine' && !rows.length && !loading && !guest) loadMine(1);
	});

	async function loadMine(p) {
		loading = true;
		note = '';
		try {
			const r = await fetchHistory(net.id, p, 20);
			rows = p === 1 ? r.rows : [...rows, ...r.rows];
			page = p;
			hasNext = r.hasNext;
		} catch {
			note = t('play.replay.notFound');
		} finally {
			loading = false;
		}
	}

	/** Open a game of the server's records: its sheet, with the player when the mode can be played. */
	async function openGame(id) {
		loading = true;
		note = '';
		try {
			const r = await academyApi.replay(String(id).trim());
			if (!r?.ok || !r?.game) throw new Error('none');
			const game = r.game;
			const s = summarize(game.detailPayload, game);
			if (!s) throw new Error('none');
			let detail = null;
			const view = buildReplayView(game.detailPayload);
			if (view?.payload && replaySupport({ ...game, replayView: view }).supported) detail = { ...game, replayView: view };
			else detail = await participantDetail(game); // the public payload of a ranked game has no inputs: a player of it may fetch the whole one
			sheet = s;
			dbPlay = detail ? recFromRecord(detail, { optionDefs: net.meta.optionDefs, me: net.id }) : null;
			onroute?.(s.id || String(id).trim());
		} catch {
			toast(t('play.academy.replay.notFound'), { kind: 'error' });
		} finally {
			loading = false;
		}
	}
	async function participantDetail(game) {
		if (!Array.isArray(game.userIds) || !game.userIds.includes(net.id)) return null;
		try {
			const body = await (await fetch(`/api/replay/game/${encodeURIComponent(game.gameId)}?includePayload=true`)).json();
			const g = body?.game;
			const payload = g?.payloadDecoded || (await decodeReplayPayload(g?.payload, g?.payloadCodec));
			const view = buildReplayView(payload);
			const detail = g && view ? { ...g, replayView: view } : null;
			return detail && replaySupport(detail).supported ? detail : null;
		} catch {
			return null;
		}
	}

	/** A game by its ID; a player is found by what the list under the box shows (the first one on Enter). */
	function search(e) {
		e.preventDefault();
		const q = query.trim();
		if (!q) return toast(t('play.academy.replay.enter'), { kind: 'info' });
		if (findType === 'game') return openGame(q);
		if (cand.users.length) pick(cand.users[0]);
	}
	// The players that match, as the typing pauses (any part of a 별명, or an exact 식별번호), a page at a time
	$effect(() => {
		if (findType !== 'nick' || picked) return;
		const q = query.trim();
		clearTimeout(timer);
		const mine = ++seq;
		if (q.length < 2) return void (cand = { status: 'idle', users: [], page: 1, hasNext: false });
		cand = { status: 'loading', users: [], page: 1, hasNext: false };
		timer = setTimeout(async () => {
			const r = await lookupUsers(q, 1);
			if (mine !== seq) return;
			cand = r.ok ? { status: 'done', users: r.users, page: 1, hasNext: r.hasNext } : { status: r.reason, users: [], page: 1, hasNext: false };
			if (autoPick && r.ok) {
				autoPick = false;
				const low = q.toLowerCase();
				const one = r.users.find((u) => u.id === q || u.name.toLowerCase() === low || `${u.name}#${u.tag}`.toLowerCase() === low) ?? (r.users.length === 1 ? r.users[0] : null);
				if (one) pick(one);
			}
		}, 350);
		return () => clearTimeout(timer);
	});
	async function moreCand() {
		loading = true;
		try {
			const r = await lookupUsers(query.trim(), cand.page + 1);
			if (r.ok) cand = { status: 'done', users: [...cand.users, ...r.users], page: cand.page + 1, hasNext: r.hasNext };
		} finally {
			loading = false;
		}
	}
	async function pick(u) {
		picked = u;
		found = null;
		note = '';
		loading = true;
		try {
			const h = await fetchHistory(u.id, 1, 20);
			found = { id: u.id, rows: h.rows, page: 1, hasNext: h.hasNext };
		} catch {
			note = t('play.replay.notFound');
		} finally {
			loading = false;
		}
	}
	function unpick() {
		picked = found = null;
		note = '';
	}
	async function foundMore() {
		if (!found) return;
		loading = true;
		try {
			const h = await fetchHistory(found.id, found.page + 1, 20);
			found = { ...found, rows: [...found.rows, ...h.rows], page: found.page + 1, hasNext: h.hasNext };
		} catch {
			/* the rows already there stay */
		} finally {
			loading = false;
		}
	}
	function closeSheet() {
		sheet = dbPlay = null;
		onroute?.(null);
	}
	/** The link to a game that anyone can open (the portal's replay page). */
	const linkTo = (id) => (shareLink ? shareLink(id) : `${location.origin}/records?type=gameId&q=${encodeURIComponent(id)}`);
	let shareOpen = $state(false);
	const share = () => sheet?.id && (shareOpen = true);

	async function readFile(f) {
		fileError = '';
		file = null;
		if (!f || !f.name.toLowerCase().endsWith('.kkio')) return (fileError = plainText(t('kkutu.js.replayError')));
		try {
			file = { rec: sanitizeRecording(decodeKkio(await f.arrayBuffer())), name: f.name };
		} catch {
			fileError = plainText(t('kkutu.js.replayError'));
		}
	}
	function drop(e) {
		e.preventDefault();
		dragging = false;
		readFile(e.dataTransfer?.files?.[0]);
	}
	function rise(node, { duration = 420 } = {}) {
		return { duration: ms(duration), easing: quintOut, css: (t, u) => `transform: translateY(${u * 10}%); opacity: ${Math.min(1, t * 2.2)}` };
	}
</script>

{#snippet gameRow(row)}
	{@const out = historyOutcome(row)}
	<button type="button" onclick={() => openGame(row.gameId)} class="flex w-full items-center gap-3 rounded-xl border border-(color:--kk-line) bg-(--kk-surface) px-3 py-2 text-left shadow-[0_2px_0_var(--kk-line)] hover:bg-(--kk-surface-2)">
		<PlacementMedal placement={row.placement} won={row.won} outcome={out} size={40} />
		<span class="min-w-0 flex-1">
			<span class="block truncate text-sm font-extrabold">{modeName(row.modeName)}</span>
			<span class="block truncate text-xs text-(color:--kk-muted)">{row.roomTitle || t('play.profile.noTitle')} · {row.placement}/{row.playerCount} · {timeAgo(row.startedAt)}</span>
		</span>
		<span class="shrink-0 text-right text-sm font-extrabold tabular-nums">{t('play.profile.points', { n: Number(row.score).toLocaleString() })}</span>
		<Icon name="play_circle" size={22} class="shrink-0 text-(color:--kk-muted)" />
	</button>
{/snippet}

{#if playing}
	<ReplayPlayer {store} rec={playing.rec} onclose={() => (playing = null)} />
{:else if sheet}
	<ShareDialog bind:open={shareOpen} title={t('play.replay.share')} link={sheet.id ? linkTo(sheet.id) : ''} shareTitle={t('play.replay.shareTitle')} copied={t('play.replay.linkCopied')} shareText={sheet.room.title || modeName(sheet.room.modeKey)} />
	<ReplaySummary {store} {sheet} onclose={closeSheet} onshare={share} onplay={dbPlay ? () => (playing = { rec: dbPlay }) : null} />
{:else}
	<section aria-label={t('play.nav.replay')} in:rise|global class="flex min-h-0 flex-1 gap-3">
		<nav aria-label={t('play.nav.replay')} class="kk-card flex w-14 shrink-0 flex-col gap-1 self-start p-1.5 sm:w-56">
			{#each SIDE as s (s.id)}
				{@const on = s.id === section}
				<button type="button" aria-current={on ? 'page' : undefined} aria-label={s.label} onclick={() => (section = s.id)} class="flex min-h-11 items-center gap-2.5 rounded-xl px-2.5 text-left text-sm font-bold transition-colors max-sm:justify-center {on ? 'bg-(--kk-accent-soft) text-(color:--kk-accent-strong)' : 'text-(color:--kk-muted) hover:bg-(--kk-surface-2) hover:text-(color:--kk-text)'}">
					<Icon name={s.icon} size={22} fill={on} /><span class="max-sm:hidden">{s.label}</span>
				</button>
			{/each}
		</nav>

		<div class="kk-card min-w-0 flex-1 overflow-y-auto p-4">
			{#key section}
				<div in:fly|global={{ y: 12, duration: ms(240), easing: quintOut }} class="flex flex-col gap-3">
					{#if section === 'mine'}
						{#if rows.length}
							<ul class="flex flex-col gap-2">{#each rows as row (row.gameId)}<li>{@render gameRow(row)}</li>{/each}</ul>
							{#if hasNext}<button type="button" disabled={loading} onclick={() => loadMine(page + 1)} class="kk-btn self-center">{t('play.profile.more')}</button>{/if}
						{:else if loading}
							<p class="py-8 text-center text-sm text-(color:--kk-muted)">{t('play.profile.loading')}</p>
						{:else}
							<EmptyState title={note || t('play.profile.noHistory')} />
						{/if}
					{:else if section === 'find'}
						<ChipRow options={[{ value: 'game', label: t('play.replay.type.game') }, { value: 'nick', label: t('play.replay.type.nick') }]} value={findType} onchange={(v) => ((findType = v), unpick(), (cand = { status: 'idle', users: [], page: 1, hasNext: false }))} label={t('play.replay.side.find')} />
						{#if !picked}
							<form onsubmit={search} class="flex gap-2">
								<label class="kk-input flex h-11 min-w-0 flex-1 items-center gap-2 px-3"><Icon name="search" size={19} /><input bind:value={query} placeholder={t(findType === 'game' ? 'play.replay.find.placeholder' : 'play.friends.searchPlaceholder')} aria-label={t('play.replay.side.find')} autocomplete="off" maxlength="64" class="min-w-0 flex-1 bg-transparent text-sm outline-none" /></label>
								{#if findType === 'game'}<button class="kk-btn kk-btn-primary min-h-11 px-5" disabled={loading}>{t('play.replay.find.go')}</button>{/if}
							</form>
						{/if}
						{#if findType === 'game'}
							<p class="py-6 text-center text-sm text-(color:--kk-muted)">{t('play.replay.find.hint')}</p>
						{:else if picked}
							<!-- one player's games, with the way back to the list of players -->
							<div class="flex items-center gap-3">
								<button type="button" aria-label={t('play.replay.back')} onclick={unpick} class="grid size-9 shrink-0 place-items-center rounded-lg text-(color:--kk-muted) hover:bg-(--kk-surface-2) hover:text-(color:--kk-text)"><Icon name="arrow_back" size={22} /></button>
								<Moremi equip={picked.equip} shop={store.shop} parts={net.meta.moremiParts} size={40} label={picked.name} frame={false} />
								<b class="min-w-0 flex-1 truncate text-base"><NickName user={picked} /></b>
							</div>
							{#if found?.rows.length}
								<ul class="flex flex-col gap-2">{#each found.rows as row (row.gameId)}<li>{@render gameRow(row)}</li>{/each}</ul>
								{#if found.hasNext}<button type="button" disabled={loading} onclick={foundMore} class="kk-btn self-center">{t('play.profile.more')}</button>{/if}
							{:else if loading}
								<p class="py-8 text-center text-sm text-(color:--kk-muted)">{t('play.profile.loading')}</p>
							{:else}
								<EmptyState title={note || t('play.profile.noHistory')} />
							{/if}
						{:else if cand.status === 'loading'}
							<div class="flex flex-col gap-2" role="status" aria-label={t('play.profile.loading')}>{#each [0, 1, 2] as i (i)}<div class="h-14 animate-pulse rounded-xl bg-(--kk-surface-2)"></div>{/each}</div>
						{:else if cand.status === 'done' && cand.users.length}
							<ul class="flex flex-col divide-y divide-(color:--kk-line) rounded-xl border-[1.5px] border-(color:--kk-line)">
								{#each cand.users as u (u.id)}
									<li>
										<button type="button" onclick={() => pick(u)} class="flex w-full items-center gap-3 px-3 py-2 text-left hover:bg-(--kk-surface-2)">
											<Moremi equip={u.equip} shop={store.shop} parts={net.meta.moremiParts} size={40} label={u.name} frame={false} />
											<span class="min-w-0 flex-1 truncate font-extrabold"><NickName user={u} /></span>
											<Icon name="chevron_right" size={22} class="shrink-0 text-(color:--kk-muted)" />
										</button>
									</li>
								{/each}
							</ul>
							{#if cand.hasNext}<button type="button" disabled={loading} onclick={moreCand} class="kk-btn self-center">{t('play.profile.more')}</button>{/if}
						{:else if cand.status === 'idle'}
							<p class="py-6 text-center text-sm text-(color:--kk-muted)">{t('play.replay.find.hint')}</p>
						{:else}
							<p role="status" class="rounded-xl bg-(--kk-surface-2) px-4 py-5 text-center text-sm text-(color:--kk-muted)">{t(cand.status === 'login' ? 'play.replay.find.login' : cand.status === 'rate' ? 'play.friends.searchRate' : cand.status === 'error' ? 'play.friends.searchError' : 'play.friends.searchNone')}</p>
						{/if}
					{:else}
						<!-- a .kkio from my computer: opened, looked at, watched; never put on the server -->
						<label
							ondragover={(e) => (e.preventDefault(), (dragging = true))}
							ondragleave={() => (dragging = false)}
							ondrop={drop}
							class="flex cursor-pointer flex-col items-center gap-2 rounded-2xl border-2 border-dashed px-6 py-10 text-center transition-colors {dragging ? 'border-(--kk-accent) bg-(--kk-accent-soft)' : 'border-(color:--kk-line-strong) hover:bg-(--kk-surface-2)'}"
						>
							<Icon name="upload_file" size={40} class="text-(color:--kk-muted)" />
							<b class="text-sm">{t('play.replay.file.drop')}</b>
							<span class="text-xs text-(color:--kk-muted)">{t('play.replay.file.local')}</span>
							<input type="file" accept=".kkio" class="sr-only" onchange={(e) => (readFile(e.currentTarget.files?.[0]), (e.currentTarget.value = ''))} />
						</label>
						{#if fileError}<p role="alert" class="rounded-xl bg-(--kk-danger)/10 px-3 py-2 text-sm font-bold text-(color:--kk-danger)">{fileError}</p>{/if}
						{#if file}<ReplayInfo {store} rec={file.rec} name={file.name} onwatch={() => (playing = { rec: file.rec })} />{/if}
					{/if}
				</div>
			{/key}
		</div>
	</section>
{/if}
