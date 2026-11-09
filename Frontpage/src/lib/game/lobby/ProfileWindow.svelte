<script>
	import { untrack } from 'svelte';
	import { t, i18n } from '../i18n.svelte.js';
	import { levelSpan } from '../level.js';
	import { IMAGE_CDN, IMAGE_VERSION } from '../images.js';
	import { maskBad } from '../badwords.js';
	import { prefs } from '../prefs.svelte.js';
	import { MAX_NOTE, notes, setNote } from '../notes.svelte.js';
	import { isShut } from '../blocks.svelte.js';
	import { buildModeStats, fetchHistory, fetchModeStats, fetchProfile, historyOutcome, recommendedMode, needsWorkMode, timeAgo } from '../profile.js';
	import FloatWindow from './FloatWindow.svelte';
	import PushScreen from './PushScreen.svelte';
	import Moremi from './Moremi.svelte';
	import LevelIcon from './LevelIcon.svelte';
	import NickName from './NickName.svelte';
	import TitleBadge from './TitleBadge.svelte';
	import EmptyState from './EmptyState.svelte';
	import Icon from './Icon.svelte';
	import { plainText } from '../protocol.js';
	import Tooltip from '../ui/Tooltip.svelte';
	import Tabs from '../ui/Tabs.svelte';
	import DataTable from '../ui/DataTable.svelte';
	import PlacementMedal from './PlacementMedal.svelte';
	import ProfileBox from './ProfileBox.svelte';
	import BioCard from './BioCard.svelte';
	import NicknameDialog from './NicknameDialog.svelte';
	import { fullNick } from './nickTip.js';
	import StatusBubble from './StatusBubble.svelte';
	import StatusDot from './StatusDot.svelte';
	import RatingIcon from './RatingIcon.svelte';
	import { openRating } from './ratingDialog.svelte.js';
	import Select from '../ui/Select.svelte';
	import WardrobeDialog from './WardrobeDialog.svelte';
	import LevelTableDialog from './LevelTableDialog.svelte';
	import { loadGoodDetails, itemName } from '../shop.js';
	import { confirm, prompt } from '../ui/confirm.svelte.js';
	import { toast } from '../ui/toast.svelte.js';

	/**
	 * A player's profile: who they are, where they are, how they play, and what you can do about it - befriend them,
	 * keep a note, whisper, block, report; the master also manages the room's players from here. One window for every
	 * way in (a seat, the 접속자 list, a chat name, "/정보"). Finding people by name is the ranking's job.
	 */
	let { store, userId = null, start = { tab: 'profile', n: 0 }, open = $bindable(false), onai } = $props();
	const net = $derived(store.net);
	const parts = $derived(net.meta.moremiParts);

	// Phone: a screen of its own (hero, actions and tabs in one scroll) instead of a window
	let winW = $state(typeof innerWidth === 'undefined' ? 1280 : innerWidth);
	const phone = $derived(winW < 768);
	let wardrobeOpen = $state(false);
	let nickOpen = $state(false);
	let details = $state(null); // item names, for the wardrobe
	let tab = $state('profile'); // 프로필 / 통계 / 경기 내역; for me also 프로필 관리 (what I own)
	let current = $state(null);
	let remote = $state(null); // the game's record of them (also for people who are online)
	let missing = $state(false);
	let stats = $state(null);
	let rows = $state([]); // 경기 내역: a summary of each game (the details and the replay are on the 전적 page)
	let hasNext = $state(false);
	let page = $state(1);
	let levelTableOpen = $state(false);
	$effect(() => {
		if (levelTableOpen && !net.levelTable) store.requestLevelRewards();
	});
	let loadingRows = $state(false);
	let failed = $state(false);
	let seq = 0; // drops the answers of a profile we've already left

	const live = $derived(current ? net.users[current] : null);
	const user = $derived(live ? { ...(remote ?? {}), ...live, exordial: remote?.exordial ?? live.exordial ?? '', rank: remote?.rank ?? null, lastLogin: remote?.lastLogin ?? 0 } : remote);
	const isMe = $derived(!!user && user.id === net.id);
	const member = $derived(!!user && !user.robot && !user.guest);
	const social = $derived(member && !isMe && !net.guest); // friends and notes are for members, among members
	const friend = $derived(!!user && store.isFriend(user.id));
	const bestFriend = $derived(!!user && store.isBestFriend(user.id));
	const PAGE = 10;
	const span = $derived(user && !user.robot ? levelSpan(user.score) : null);
	const bar = $derived(span && isFinite(span.to) ? Math.min(100, Math.max(0, ((user.score - span.from) / (span.to - span.from)) * 100)) : 100);
	const modeStats = $derived(stats ? buildModeStats(user?.record, stats) : []);
	const recommended = $derived(recommendedMode(modeStats));
	const needsWork = $derived(needsWorkMode(modeStats));
	const lockText = $derived(plainText(store.net.meta.labels['kkutu.js.error.438'] ?? ''));
	// a game that starts while 프로필 관리 is open closes that tab
	$effect(() => {
		if (store.dressLocked && tab === 'box') tab = 'profile';
		if (store.dressLocked) nickOpen = false;
	});
	const tabs = $derived([{ id: 'profile', label: t('play.profile.windowTitle') }, { id: 'stats', label: t('play.profile.tab.stats') }, { id: 'history', label: t('play.profile.tab.history') }, ...(isMe ? [{ id: 'box', label: t('kkutu.dialog.profile.dress'), disabled: store.dressLocked, hint: lockText }] : [])]);
	const canManage = $derived(!!user && store.isMaster && user.id !== net.id);
	const hidden = $derived(prefs.hideUsers && !isMe && !!user && !user.robot);
	// The profile banner they equipped; without one (or when its picture is missing) the default banner, as legacy. It fills the stage.
	let bannerFailed = $state('');
	const bannerId = $derived(user?.equip?.BAN && user.equip.BAN !== bannerFailed ? user.equip.BAN : 'def_banner');
	const banner = $derived(`${IMAGE_CDN}kkutu/moremi/banner/${bannerId}_p.png${IMAGE_VERSION}`);
	const note = $derived(user ? notes[user.id] : '');
	const shut = $derived(!!user && isShut(user.id));
	const text = (s) => (prefs.filter === 'off' ? s : maskBad(s));
	const modeName = (key) => i18n.messages[`game.mode.${key}.name`] ?? key;
	// 매칭 시작 on the two insight panels: opens the quick match dialog with that mode chosen (the player still sees and may change the rules, themes
	// and so on before anything starts); only from the lobby, where a match can be started
	const modeIndex = (key) => store.net.meta.modes.indexOf(key);
	const canMatch = (key) => modeIndex(key) >= 0 && store.net.status === 'ready' && !store.currentRoom;
	function match(key) {
		const mode = modeIndex(key);
		if (mode < 0) return;
		open = false;
		store.hooks.openQuick?.(mode);
	}
	const place = $derived(
		!user ? '' : live ? (live.place ? t('play.profile.place.room', { no: live.place }) : t('play.profile.place.lobby')) : user.lastLogin ? t('play.profile.lastSeen', { when: timeAgo(user.lastLogin) }) : t('play.profile.place.offline')
	);

	// Opening the window shows that player afresh.
	$effect(() => {
		if (open && userId) untrack(() => load(userId));
	});

	// Asked to open on 프로필 관리 while my own profile is already open: switch to it.
	$effect(() => {
		const s = start;
		untrack(() => {
			if (open && s.tab === 'box' && current === net.id) tab = 'box';
		});
	});

	async function load(id) {
		current = id;
		tab = start.tab === 'box' && id === net.id ? 'box' : 'profile';
		const mine = ++seq;
		remote = null;
		missing = false;
		stats = null;
		rows = [];
		hasNext = false;
		page = 1;
		failed = false;
		const here = net.users[id];
		if (!here?.robot) {
			const r = await fetchProfile(id);
			if (mine !== seq) return;
			remote = r;
			if (!r && !here) return void (missing = true);
		}
		const who = net.users[id] ?? remote;
		if (!who || who.robot || who.guest) return;
		loadingRows = true;
		// The record service may be unreachable: the game's own per-mode numbers still show, and only the games list says so.
		const [r, h] = await Promise.allSettled([fetchModeStats(id), fetchHistory(id, 1, PAGE)]);
		if (mine !== seq) return;
		stats = r.status === 'fulfilled' ? r.value : [];
		if (h.status === 'fulfilled') {
			rows = h.value.rows;
			hasNext = h.value.hasNext;
		} else failed = true;
		loadingRows = false;
	}

	async function more() {
		const mine = seq;
		loadingRows = true;
		try {
			const h = await fetchHistory(current, page + 1, PAGE);
			if (mine !== seq) return;
			rows = [...rows, ...h.rows];
			hasNext = h.hasNext;
			page += 1;
		} catch {
			if (mine === seq) failed = true;
		} finally {
			if (mine === seq) loadingRows = false;
		}
	}
	const dateOf = (ms) => new Intl.DateTimeFormat(i18n.language, { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' }).format(new Date(ms));

	async function copyId() {
		try {
			await navigator.clipboard.writeText(user.id);
			toast(t('play.profile.idCopied'), { kind: 'success' });
		} catch {
			toast(t('play.error.request', { code: 'copy' }), { kind: 'error' });
		}
	}
	async function editNote() {
		const v = await prompt({ message: t('play.profile.noteEdit'), value: note || '', max: MAX_NOTE });
		if (v !== null) setNote(user.id, v.trim());
	}
	$effect(() => {
		if (((open && isMe) || levelTableOpen) && !details) loadGoodDetails().then((d) => (details = d));
	});
	/** My bio was saved: what this window and the lobby's copy of me show. */
	function bioSaved(text) {
		if (remote) remote = { ...remote, exordial: text };
		if (net.users[net.id]) net.users[net.id].exordial = text;
	}
	const statusOptions = $derived(['online', 'idle', 'dnd', 'invisible'].map((v) => ({ value: v, label: t(`play.status.${v}`) })));
	const whisper = () => ((open = false), store.openDm(user.id));
	const report = () => store.hooks.openReport?.({ type: 'user', id: user.id, name: user.name });
	// 경기 내역: a game opens in the 리플레이 page (its sheet, and the player)
	const openGame = (gameId) => {
		open = false;
		store.hooks.openReplay?.(gameId);
	};
	async function handover() {
		if (!(await confirm({ message: t('play.confirm.handover') }))) return;
		store.handover(user.id);
		open = false;
	}
	function kick() {
		store.kick(user.id, user.robot);
		open = false;
	}

	const statColumns = $derived([
		{ key: 'mode', label: t('play.profile.col.mode'), nowrap: true, class: 'font-bold', pad: 'pl-2.5 pr-1.5 py-1.5' },
		{ key: 'games', label: t('play.profile.col.games'), align: 'right', nowrap: true, numeric: true },
		{ key: 'wld', label: t('play.profile.col.wld'), align: 'right', nowrap: true, numeric: true },
		{ key: 'rate', label: t('play.profile.stat.rate'), align: 'right', nowrap: true, numeric: true },
		{ key: 'hours', label: t('play.profile.col.hours'), align: 'right', nowrap: true, numeric: true },
		{ key: 'words', label: t('play.profile.stat.words'), align: 'right', nowrap: true, numeric: true },
		{ key: 'exp', label: t('play.profile.col.exp'), align: 'right', nowrap: true, numeric: true, pad: 'pl-2.5 pr-1.5 py-1.5' }
	]);
</script>

{#snippet cell(m, col)}
	{#if col.key === 'mode'}{modeName(m.key)}
	{:else if col.key === 'games'}{m.games.toLocaleString()}
	{:else if col.key === 'wld'}{m.wins}<span class="text-(color:--kk-muted)"> / {m.halves} / </span>{m.losses}
	{:else if col.key === 'rate'}{m.winRate.toFixed(1)}%
	{:else if col.key === 'hours'}{m.hours.toFixed(1)}
	{:else if col.key === 'words'}{m.acceptedWords.toLocaleString()}
	{:else}{m.exp.toLocaleString()}{/if}
{/snippet}


{#snippet iconBtn(icon, label, onclick, extra = '')}
	<Tooltip text={label}><button type="button" aria-label={label} {onclick} class="grid size-10 place-items-center rounded-xl bg-(--kk-surface-2) text-(color:--kk-muted) hover:bg-(--kk-accent-soft) hover:text-(color:--kk-text) {extra}"><Icon name={icon} size={20} /></button></Tooltip>
{/snippet}

<svelte:window bind:innerWidth={winW} />

{#snippet body()}
	{#if missing}
		<EmptyState title={t('play.profile.notFound')} />
	{:else if user}
		<!-- One size for every tab (and every player): the window never jumps; what does not fit scrolls inside, the avatar card stays in view. -->
		<div class={phone ? 'p-3' : 'h-[min(30rem,calc(100dvh-8rem))] overflow-y-auto'}>
		<div class="grid grid-cols-[minmax(0,1fr)] gap-4 md:grid-cols-[15rem_minmax(0,1fr)]">
			<!-- Who they are and what you can do with them -->
			<aside class="flex min-w-0 flex-col gap-3 self-start md:sticky md:top-0">
				<div class="overflow-hidden rounded-2xl border border-(color:--kk-line) bg-(--kk-surface)">
					<div class="relative flex h-56 flex-col items-center justify-end overflow-hidden bg-(--kk-surface-2) pb-3">
						{#if !hidden && !user.robot}<img src={banner} alt="" draggable="false" onerror={() => (bannerFailed = user.equip?.BAN ?? '')} class="absolute inset-0 size-full object-cover" />{/if}
						<Moremi equip={user.equip} shop={store.shop} {parts} size={132} label={user.name} frame={false} maskable={!isMe && !user.robot} class="relative" />
						{#if !user.robot && !user.guest && !(prefs.hideUsers && !isMe)}<StatusBubble message={user.statusMessage ?? ''} {isMe} mask={text} onsave={(m) => store.setStatusMessage(m)} />{/if}
						{#if isMe}
							<!-- the wardrobe, floating on the preview's top left corner -->
							<Tooltip text={store.dressLocked ? lockText : ''} placement="right"><button type="button" aria-disabled={store.dressLocked || undefined} onclick={() => !store.dressLocked && (wardrobeOpen = true)} class="{store.dressLocked ? 'cursor-not-allowed opacity-50' : ''} absolute top-2 left-2 z-10 inline-flex items-center gap-1 rounded-full bg-black/35 py-1.5 pr-3 pl-2 text-xs font-extrabold text-white shadow-md backdrop-blur-sm transition-colors hover:bg-black/55 focus-visible:bg-black/55"><Icon name="checkroom" size={17} />{t('play.wardrobe.open')}</button></Tooltip>
						{/if}
					</div>
					<div class="flex flex-col gap-2 p-3">
						<TitleBadge {user} shop={store.shop} size="md" maskable={!isMe && !user.robot} class="self-start" />
						<div class="flex items-center gap-2">
							{#if user.robot}
								<LevelIcon level={1} size={26} />
							{:else}
								<Tooltip text={t('play.levelTable.open')}><button type="button" aria-label={t('play.levelTable.open')} onclick={() => (levelTableOpen = true)} class="relative grid size-8 shrink-0 place-items-center rounded-lg hover:bg-(--kk-surface-2)"><LevelIcon level={user.lv} size={26} maskable={!isMe} /><span class="absolute -right-1 -bottom-1 grid size-4 place-items-center rounded-full bg-(--kk-surface-2) text-(color:--kk-muted) shadow-[0_1px_0_var(--kk-line)]"><Icon name="info" size={13} fill /></span></button></Tooltip>
							{/if}
							<Tooltip text={prefs.hideUsers && !isMe && !user.robot ? t('play.anonymous') : fullNick(user)}><h2 class="min-w-0 flex-1 truncate text-lg leading-tight font-black"><NickName {user} showCheck maskable={!isMe && !user.robot} /></h2></Tooltip>
							{#if isMe && !user.guest}<Tooltip text={store.dressLocked ? lockText : t('play.nick.edit')}><button type="button" aria-label={t('play.nick.edit')} aria-disabled={store.dressLocked || undefined} onclick={() => !store.dressLocked && (nickOpen = true)} class="{store.dressLocked ? 'cursor-not-allowed opacity-45' : 'hover:bg-(--kk-surface-2) hover:text-(color:--kk-text)'} grid size-8 shrink-0 place-items-center rounded-lg text-(color:--kk-muted)"><Icon name="edit" size={18} /></button></Tooltip>{/if}
						</div>
						{#if isMe && !user.guest}<Select dense label={t('play.status.label')} options={statusOptions} value={net.myStatus} onchange={(v) => store.setStatus(v)}>{#snippet leading(o)}<StatusDot status={o.value} size={13} />{/snippet}</Select>{/if}
						{#if user.robot}
							<p class="text-xs text-(color:--kk-muted)">{t('play.profile.robot')}</p>
						{:else}
							<div class="relative h-5 overflow-hidden rounded-full bg-(--kk-accent-soft)" role="progressbar" aria-label={t('play.profile.level', { level: user.lv })} aria-valuenow={Math.round(bar)} aria-valuemin="0" aria-valuemax="100">
								<div class="h-full rounded-full bg-(--kk-fill)" style="width:{bar}%"></div>
								<span class="absolute inset-0 grid place-items-center text-[11px] font-extrabold tabular-nums">{t('play.profile.level', { level: user.lv })} ({t('play.profile.exp', { score: user.score.toLocaleString(), next: isFinite(span.to) ? span.to.toLocaleString() : '∞' })})</span>
							</div>
							<div class="flex flex-wrap items-center gap-1.5 text-xs font-bold text-(color:--kk-muted)">
								<span class="inline-flex items-center gap-1"><Icon name={live ? 'location_on' : 'schedule'} size={15} />{place}</span>
								{#if member && user.rating != null}<Tooltip text={t(`play.rating.${user.rating}`)}><button type="button" onclick={() => openRating(user.rating, isMe)} class="inline-flex cursor-pointer items-center gap-1 rounded-full hover:underline hover:decoration-dotted hover:underline-offset-2"><RatingIcon rating={user.rating} size={14} />{t(`play.rating.label.${user.rating}`)}</button></Tooltip>{/if}
								{#if !isMe && live && live.status !== 'online'}<span class="inline-flex items-center gap-1"><StatusDot status={live.status} size={11} />{t(`play.status.${live.status}`)}</span>{/if}
								{#if user.rank}<span class="rounded-full bg-(--kk-accent-soft) px-2 py-0.5 font-extrabold text-(color:--kk-accent-strong)">{t('play.profile.rank', { n: user.rank.toLocaleString() })}</span>{/if}
								{#if friend}<span class="inline-flex items-center gap-0.5 rounded-full bg-(--kk-accent-soft) px-2 py-0.5 font-extrabold text-(color:--kk-accent-strong)"><Icon name={bestFriend ? 'favorite' : 'check'} size={13} fill />{t(bestFriend ? 'play.friend.best' : 'play.friend.isFriend')}</span>{/if}
							</div>
						{/if}
					</div>
				</div>

				{#if !user.robot}
					{#if note}<p class="flex items-start gap-1.5 text-xs text-(color:--kk-muted)"><Icon name="sticky_note_2" size={15} class="mt-px shrink-0" /><q class="break-words">{text(note)}</q></p>{/if}
				{/if}

				{#if social}
					{#if friend}
						<div class="flex gap-2">
							<button type="button" onclick={() => store.toggleBestFriend(user.id)} class="kk-btn inline-flex min-h-11 flex-1 items-center justify-center gap-1.5 text-sm"><Icon name="favorite" size={18} fill={bestFriend} />{t(bestFriend ? 'play.friend.bestOff' : 'play.friend.bestOn')}</button>
							{@render iconBtn('person_remove', t('play.friend.remove'), () => store.removeFriend(user.id))}
						</div>
					{:else}
						<button type="button" onclick={() => store.addFriend(user.id, { name: user.name })} class="kk-btn kk-btn-primary inline-flex min-h-11 items-center justify-center gap-1.5"><Icon name="person_add" size={20} fill />{t('play.friend.add')}</button>
					{/if}
				{/if}
				{#if !user.robot}
					<div class="flex flex-wrap gap-2">
						{#if social}
							{@render iconBtn('sticky_note_2', t('play.profile.note'), editNote)}
							{@render iconBtn('chat', t('play.profile.whisper'), whisper)}
							{@render iconBtn('block', t(shut ? 'play.block.unblock' : 'play.profile.block'), () => store.toggleBlock('shut', user.id), shut ? 'bg-(--kk-danger-soft) text-(color:--kk-danger)' : '')}
							{@render iconBtn('flag', t('play.profile.report'), report)}
						{:else if !isMe}
							{@render iconBtn('chat', t('play.profile.whisper'), whisper)}
							{@render iconBtn('block', t(shut ? 'play.block.unblock' : 'play.profile.block'), () => store.toggleBlock('shut', user.id), shut ? 'bg-(--kk-danger-soft) text-(color:--kk-danger)' : '')}
							{@render iconBtn('flag', t('play.profile.report'), report)}
						{/if}
						{#if member}{@render iconBtn('info', t('kkutu.dialog.profile.okgg'), openRecords)}{/if}
						{@render iconBtn('badge', t('play.profile.copyId'), copyId)}
					</div>
				{/if}
				{#if canManage}
					<div class="flex flex-wrap gap-2 border-t border-(color:--kk-line) pt-3">
						{#if user.robot}
							<button type="button" class="kk-btn inline-flex items-center gap-1.5 text-sm" onclick={() => { open = false; onai?.(user); }}><Icon name="tune" size={18} />{t('play.ai.title')}</button>
						{:else}
							<button type="button" class="kk-btn inline-flex items-center gap-1.5 text-sm" onclick={handover}><Icon name="workspace_premium" size={18} />{t('play.profile.handover')}</button>
						{/if}
						<button type="button" class="kk-btn kk-btn-danger inline-flex items-center gap-1.5 text-sm" onclick={kick}><Icon name="person_remove" size={18} />{t('play.profile.kick')}</button>
					</div>
				{/if}
			</aside>

			<!-- Their numbers (what the 전적 page's profile and statistics tabs used to show); for me also what I own -->
			<section class="flex min-w-0 flex-col gap-3">
				{#if member}
					<Tabs bind:value={tab} idPrefix="profile" label={t('play.profile.windowTitle')} {tabs} />
				{/if}
				{#if isMe && tab === 'box'}
					<div role="tabpanel" id="profile-panel-box" aria-labelledby="profile-tab-box"><ProfileBox {store} /></div>
				{:else if user.robot}
					<EmptyState compact size={110} title={t('play.profile.robot')} />
				{:else if user.guest}
					<EmptyState compact size={110} title={t('play.profile.guestNoRecord')} />
				{:else if tab === 'history'}
					<div role="tabpanel" id="profile-panel-history" aria-labelledby="profile-tab-history" class="flex flex-col gap-2">
						{#if rows.length}
							<ul class="flex flex-col gap-1.5">
								{#each rows as row (row.gameId)}
									{@const out = historyOutcome(row)}
									<li>
										<button type="button" onclick={() => openGame(row.gameId)} class="flex w-full items-center gap-3 rounded-xl border border-(color:--kk-line) bg-(--kk-surface) px-3 py-2 text-left shadow-[0_2px_0_var(--kk-line)]">
											<PlacementMedal placement={row.placement} won={row.won} outcome={out} size={40} />
											<span class="min-w-0 flex-1">
												<span class="block truncate text-sm font-extrabold">{modeName(row.modeName)}</span>
												<span class="block truncate text-xs text-(color:--kk-muted)">{text(row.roomTitle || t('play.profile.noTitle'))} · {row.placement}/{row.playerCount} · {timeAgo(row.startedAt)} · {dateOf(row.startedAt)}</span>
											</span>
											<span class="shrink-0 text-right text-sm font-extrabold tabular-nums">{t('play.profile.points', { n: Number(row.score).toLocaleString() })}</span>
										</button>
									</li>
								{/each}
							</ul>
							<div class="flex flex-wrap items-center gap-2">
								{#if hasNext}<button type="button" class="kk-btn text-sm" disabled={loadingRows} onclick={more}>{t('play.profile.more')}</button>{/if}
							</div>
						{:else if loadingRows}
							<p class="py-5 text-center text-sm text-(color:--kk-muted)">{t('play.profile.loading')}</p>
						{:else}
							<p class="rounded-xl bg-(--kk-surface-2) px-3 py-4 text-center text-sm text-(color:--kk-muted)">{t(failed ? 'play.profile.loadFailed' : 'play.profile.noHistory')}</p>
						{/if}
					</div>
				{:else if tab === 'stats'}
					<div role="tabpanel" id="profile-panel-stats" aria-labelledby="profile-tab-stats" class="flex flex-col gap-3">
						{#if modeStats.length}
							<DataTable caption={t('play.profile.tab.stats')} columns={statColumns} rows={modeStats} rowKey={(m) => m.key} dense {cell} />
						{:else if stats}
							<p class="rounded-xl bg-(--kk-surface-2) px-3 py-4 text-center text-sm text-(color:--kk-muted)">{t('play.profile.noStats')}</p>
						{:else}
							<p class="py-5 text-center text-sm text-(color:--kk-muted)">{t('play.profile.loading')}</p>
						{/if}
						<button type="button" class="kk-btn inline-flex items-center gap-1.5 self-start text-sm" onclick={() => (tab = 'history')}><Icon name="history" size={18} />{t('play.profile.toHistory')}</button>
					</div>
				{:else}
					<div role="tabpanel" id="profile-panel-profile" aria-labelledby="profile-tab-profile" class="flex flex-col gap-3">
						<BioCard bio={user.exordial} {isMe} mask={text} onsaved={bioSaved} />
						{#if isMe}
							<div class="grid gap-2.5 sm:grid-cols-2">
								<article class="flex flex-col gap-1 rounded-xl bg-(--kk-accent-soft) p-3">
									<h3 class="flex items-center gap-1.5 text-xs font-extrabold text-(color:--kk-accent-strong)"><Icon name="recommend" size={16} />{t('play.profile.insight.recommend')}</h3>
									{#if recommended}
										<p class="text-lg leading-tight font-black">{modeName(recommended.key)}</p>
										<p class="text-xs text-(color:--kk-muted)">{t('play.profile.insight.recommendLine', { rate: recommended.winRate.toFixed(2), games: recommended.games.toLocaleString(), hours: recommended.hours.toFixed(1) })}</p>
										{#if canMatch(recommended.key)}<button type="button" class="kk-btn kk-btn-primary mt-auto inline-flex items-center gap-1 self-start text-sm" onclick={() => match(recommended.key)}><Icon name="play_arrow" size={18} fill />{t('play.quick.start')}</button>{/if}
									{:else}
										<p class="text-sm text-(color:--kk-muted)">{t('play.profile.insight.recommendNone')}</p>
									{/if}
								</article>
								<article class="flex flex-col gap-1 rounded-xl bg-(--kk-surface-2) p-3">
									<h3 class="flex items-center gap-1.5 text-xs font-extrabold text-(color:--kk-muted)"><Icon name="school" size={16} />{t('play.profile.insight.needs')}</h3>
									{#if needsWork}
										<p class="text-lg leading-tight font-black">{modeName(needsWork.key)}</p>
										<p class="text-xs text-(color:--kk-muted)">{t('play.profile.insight.needsLine', { rate: needsWork.winRate.toFixed(2), games: needsWork.games.toLocaleString() })}</p>
										{#if canMatch(needsWork.key)}<button type="button" class="kk-btn kk-btn-primary mt-auto inline-flex items-center gap-1 self-start text-sm" onclick={() => match(needsWork.key)}><Icon name="play_arrow" size={18} fill />{t('play.quick.start')}</button>{/if}
									{:else}
										<p class="text-sm text-(color:--kk-muted)">{t('play.profile.insight.needsNone')}</p>
									{/if}
								</article>
							</div>
						{/if}
						<h3 class="text-sm font-extrabold">{t('play.profile.record')}</h3>
						{#if modeStats.length}
							<div class="grid gap-2 sm:grid-cols-3">
								{#each modeStats.slice(0, 3) as m (m.key)}
									<article class="flex flex-col gap-0.5 rounded-xl border border-(color:--kk-line) p-3 tabular-nums">
										<b class="truncate text-sm">{modeName(m.key)}</b>
										<span class="text-xl leading-tight font-black">{m.hours.toFixed(1)}<small class="ml-0.5 text-xs font-bold text-(color:--kk-muted)">{t('play.profile.col.hours')}</small></span>
										<span class="text-xs">{m.wins}<span class="text-(color:--kk-muted)"> / {m.halves} / </span>{m.losses}</span>
										<span class="text-xs text-(color:--kk-muted)">{t('play.profile.stat.rate')} {m.winRate.toFixed(1)}%</span>
									</article>
								{/each}
							</div>
						{:else if stats}
							<p class="rounded-xl bg-(--kk-surface-2) px-3 py-4 text-center text-sm text-(color:--kk-muted)">{t('play.profile.noStats')}</p>
						{:else}
							<p class="py-5 text-center text-sm text-(color:--kk-muted)">{t('play.profile.loading')}</p>
						{/if}
						<button type="button" class="kk-btn inline-flex items-center gap-1.5 self-start text-sm" onclick={() => (tab = 'history')}><Icon name="history" size={18} />{t('play.profile.toHistory')}</button>
					</div>
				{/if}
			</section>
		</div>
		</div>
	{:else}
		<p class="py-10 text-center text-sm text-(color:--kk-muted)">{t('play.profile.loading')}</p>
	{/if}
{/snippet}

{#if phone}
<PushScreen bind:open title={t('play.profile.windowTitle')}>{@render body()}</PushScreen>
{:else}
<FloatWindow bind:open id="profile" title={t('play.profile.windowTitle')} width="50rem">
	{@render body()}
</FloatWindow>
{/if}

<NicknameDialog bind:open={nickOpen} {store} />
<LevelTableDialog bind:open={levelTableOpen} table={net.levelTable} shop={store.shop} level={user?.lv ?? 0} nameOf={(id) => itemName(details, id)} />
<WardrobeDialog bind:open={wardrobeOpen} {store} nameOf={(id) => itemName(details, id)} />
