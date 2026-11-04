<script>
	import { onMount } from 'svelte';
	import { t } from '../i18n.svelte.js';
	import { seatLayout } from '../lobbyModel.js';
	import Icon from './Icon.svelte';
	import PlayerSlot from './PlayerSlot.svelte';
	import RadioGroup from '../ui/RadioGroup.svelte';
	import Tooltip from '../ui/Tooltip.svelte';
	import { toast } from '../ui/toast.svelte.js';
	import Rich from '../ui/Rich.svelte';
	import { prefs } from '../prefs.svelte.js';
	import { notice } from '../protocol.js';
	import InviteDialog from './InviteDialog.svelte';
	import AiDialog from './AiDialog.svelte';
	import KickVoteDialog from './KickVoteDialog.svelte';
	import { quintOut } from 'svelte/easing';
	import { ms } from '../motion.js';

	/** The waiting room: who is in, ready/start, teams, spectate, settings. (The game itself is not built yet.) */
	let { store, onsettings } = $props();
	const room = $derived(store.currentRoom);
	const players = $derived(store.roomPlayers);
	const me = $derived(store.myGame);
	const net = $derived(store.net);
	// The seat grid sizes itself to the room: cards fill the area, whatever the limit is.
	let areaW = $state(0);
	let areaH = $state(0);
	const layout = $derived(areaW && areaH ? seatLayout(areaW, areaH, room?.limit ?? 1) : { cols: 1, rows: 1, cardW: 0, cardH: 0 });
	const compact = $derived(layout.cardH < 150); // short cards switch to a side-by-side seat
	const moremiSize = $derived(
		Math.round(compact ? Math.max(44, Math.min(layout.cardH - 24, layout.cardW * 0.38)) : Math.max(48, Math.min(168, layout.cardH * 0.52, layout.cardW * 0.5)))
	);
	const empty = $derived(Math.max(0, (room?.limit ?? 0) - players.length));
	const spectating = $derived(me?.form === 'S');
	const ready = $derived(!!me?.ready);
	const teamOptions = $derived([
		{ value: 0, label: t('play.room.team.solo'), color: 'var(--kk-team-0)' },
		...[1, 2, 3, 4].map((n) => ({ value: n, label: 'ABCD'[n - 1], color: `var(--kk-team-${n})` }))
	]);
	// The profile window (on the page) asks for AI seat settings through here.
	onMount(() => {
		store.hooks.openAi = (p) => {
			aiTarget = p;
			aiOpen = true;
		};
		return () => delete store.hooks.openAi;
	});
	const aiMode = $derived(!!net.meta.rules[net.meta.modes[room?.modeIndex]]?.ai);

	// ---- dialogs opened from here ----
	let inviteOpen = $state(false);
	let aiOpen = $state(false);
	let aiTarget = $state(null);
	const openProfile = (p, e) => store.hooks.openUserMenu?.(p.id, e, 'roomUser');
	const editAi = (p) => {
		aiTarget = p;
		aiOpen = true;
	};
	function practice() {
		if (aiMode) {
			aiTarget = null;
			aiOpen = true;
		} else store.practice();
	}
	function invite() {
		inviteOpen = true;
	}

	// ---- automatic behaviours of the legacy room ----
	// Auto-ready: once per room entry, if the player asked for it.
	let autoReadied = false;
	$effect(() => {
		if (!prefs.autoReady || autoReadied || !room || store.isMaster || !me || me.ready || me.form === 'S' || room.playing) return;
		autoReadied = true;
		store.toggleReady();
	});

	// Idle master: when everyone else is ready and the master does nothing, warn after 5 s and leave 15 s later.
	// Not for admins, and not for private rooms (they may be waiting for a specific person).
	const everyoneReady = $derived.by(() => {
		let anyReady = false;
		for (const p of players) {
			if (p.id === room?.master) continue;
			if (p.game.form === 'S') continue;
			if (p.game.ready || p.robot) {
				if (!p.robot) anyReady = true;
			} else return false;
		}
		return anyReady;
	});
	$effect(() => {
		if (!store.isMaster || net.admin || room?.locked || room?.playing || !everyoneReady) return;
		let leaveTimer;
		const warn = setTimeout(() => {
			store.pushNotice(t('play.room.notice.subJamsu'));
			leaveTimer = setTimeout(() => {
				store.leaveRoom();
				toast(t('play.room.masterJamsu'), { kind: 'error', duration: 8000 });
			}, 15000);
		}, 5000);
		return () => {
			clearTimeout(warn);
			clearTimeout(leaveTimer);
		};
	});

	// ---- header: which settings just changed ----
	let blink = $state({});
	let seen = null;
	$effect(() => {
		if (!room) return;
		const now = { title: room.title, mode: room.mode + room.tags.join(), limit: room.limit, round: room.rounds, time: room.time };
		if (seen) {
			const changed = Object.fromEntries(Object.keys(now).filter((k) => now[k] !== seen[k]).map((k) => [k, true]));
			if (Object.keys(changed).length) {
				blink = changed;
				setTimeout(() => (blink = {}), 3200);
			}
		}
		seen = now;
	});
	const tbd = (key) => toast(t(key), { kind: 'info' });

	// The room slides up from underneath the chat panel (which stays on top), as one piece with its seats.
	function rise(node, { duration = 520 } = {}) {
		return { duration: ms(duration), easing: quintOut, css: (t, u) => `transform: translateY(${u * 100}%)` };
	}

	async function copyNumber() {
		try {
			await navigator.clipboard.writeText(String(room.no));
			toast(t('play.room.numberCopied', { no: room.no }), { kind: 'success' });
		} catch {
			toast(t('play.error.request', { code: 'copy' }), { kind: 'error' });
		}
	}
</script>

{#if room}
	<!-- Rises from behind the chat when you join. (No exit animation: the lobby is shown at once, so nothing overlaps.) -->
	<section
		aria-label={t('play.room.label')}
		in:rise|global
		class="flex min-h-0 flex-1 flex-col gap-3"
	>
		<!-- Header: number, title, rules and the room's numbers -->
		<header class="group kk-card flex flex-wrap items-center gap-x-4 gap-y-2 p-3 pl-4">
			<!-- Plain number: click to copy. Private rooms blur it until you point at it, so it isn't shown to a stream or screenshot by accident. -->
			<Tooltip text={t('play.room.copyNumber')} placement="bottom">
				<button type="button" onclick={copyNumber} aria-label="{t('play.room.copyNumber')}: {room.no}" class="rounded-lg px-1 text-xl font-black tabular-nums transition-[filter] {prefs.hideRoom ? 'invisible group-hover:visible group-focus-within:visible' : ''} duration-150 hover:bg-(--kk-surface-2) {room.locked ? 'blur-[5px] hover:blur-none focus-visible:blur-none' : ''}">{room.no}</button>
			</Tooltip>
			<h2 class="flex min-w-0 items-center gap-1.5 text-lg font-extrabold {prefs.hideRoom ? 'invisible group-hover:visible group-focus-within:visible' : ''}"><span class="truncate {blink.title ? 'kk-blink' : ''}">{room.title}</span>
				{#if room.locked}<Icon name="lock" size={18} fill class="shrink-0 text-(color:--kk-muted)" />{/if}
			</h2>
			<div class="flex min-w-0 flex-wrap items-center gap-1.5">
				{#snippet modeTip()}<Rich html={net.meta.labels[`game.mode.${room.modeKey}.x`] ?? ''} />{/snippet}
				<Tooltip content={net.meta.labels[`game.mode.${room.modeKey}.x`] ? modeTip : undefined} placement="bottom"><span class="text-sm font-bold {blink.mode ? 'kk-blink' : ''}">{room.mode}</span></Tooltip>
				{#each room.rules as rule (rule.key)}
					{#snippet ruleTip()}<Rich html={rule.expl} />{/snippet}
					<Tooltip content={rule.expl ? ruleTip : undefined} placement="bottom"><span class="kk-chip {blink.mode ? 'kk-blink' : ''}">{rule.label}</span></Tooltip>
				{/each}
				{#if room.injpick?.length}
					{@const names = room.injpick.map((c) => net.meta.labels[`word.theme.${c}`] ?? c).join(', ')}
					<Tooltip text={names} placement="bottom"><span class="inline-flex max-w-[16rem] items-center gap-1 text-xs font-bold text-(color:--kk-muted)"><Icon name="sell" size={16} /><span class="truncate">{names}</span></span></Tooltip>
				{/if}
			</div>
			<ul class="ml-auto flex items-center gap-3 text-sm font-bold text-(color:--kk-muted)">
				<li><Tooltip text={t('play.create.limit')}><span class="inline-flex items-center gap-1 {blink.limit ? 'kk-blink' : ''}"><Icon name="group" size={18} />{room.cur}/{room.limit}</span></Tooltip></li>
				<li><Tooltip text={t('play.create.round')}><span class="inline-flex items-center gap-1 {blink.round ? 'kk-blink' : ''}"><Icon name="repeat" size={18} />{room.rounds}</span></Tooltip></li>
				<li><Tooltip text={t('play.create.time')}><span class="inline-flex items-center gap-1 {blink.time ? 'kk-blink' : ''}"><Icon name="timer" size={18} />{t('play.create.timeValue', { n: room.time })}</span></Tooltip></li>
			</ul>
		</header>

		<!-- Seats (+ teams on the left, as in the legacy screen) -->
		<div class="kk-card flex min-h-0 flex-1 overflow-hidden">
			<div class="flex w-20 shrink-0 flex-col gap-2 border-r border-(color:--kk-line) bg-(--kk-panel) p-2">
				<RadioGroup variant="chips" vertical label={t('play.room.teams')} value={me?.team ?? 0} options={teamOptions} disabled={ready || spectating || room.playing} onchange={(v) => store.setTeam(v)} />
			</div>

			<div class="min-h-0 flex-1 overflow-hidden p-3" bind:clientWidth={areaW} bind:clientHeight={areaH}>
					<ul class="grid h-full gap-3" style="grid-template-columns:repeat({layout.cols},minmax(0,1fr));grid-template-rows:repeat({layout.rows},minmax(0,1fr))">
						{#each players as p (p.id)}
							<PlayerSlot player={p} {store} isMaster={p.id === room.master} isMe={p.id === net.id} size={moremiSize} {compact} onopen={openProfile} />
						{/each}
						{#each Array(empty) as _, i (i)}
							<!-- An open seat: just a dashed outline (an SVG stroke, so the dash and gap lengths are ours to set). -->
							<li data-empty-seat aria-hidden="true" class="relative h-full min-h-0 text-(color:--kk-line-strong)">
								<svg class="absolute inset-0 size-full" fill="none"><rect x="1.5" y="1.5" rx="15" style="width:calc(100% - 3px);height:calc(100% - 3px)" stroke="currentColor" stroke-width="2" stroke-dasharray="10 8" /></svg>
							</li>
						{/each}
					</ul>
			</div>
		</div>

		<!-- Actions -->
		<div class="flex flex-wrap items-center gap-2">
			<Tooltip text={spectating ? t('play.room.unspectate') : t('play.room.spectate')} placement="top">
				<button type="button" aria-pressed={spectating} disabled={room.playing || ready} onclick={() => store.setSpectate(!spectating)} class="kk-btn inline-flex items-center gap-1.5 {spectating ? 'bg-(--kk-accent-soft)' : ''}"><Icon name="visibility" size={20} fill={spectating} />{t('play.room.spectate')}</button>
			</Tooltip>
			<button type="button" disabled={room.playing || spectating} onclick={practice} class="kk-btn inline-flex items-center gap-1.5"><Icon name="edit" size={20} />{t('play.room.practice')}</button>
			<button type="button" onclick={invite} class="kk-btn inline-flex items-center gap-1.5"><Icon name="person_add" size={20} />{t('play.room.invite')}</button>
			{#if store.isMaster}
				<button type="button" disabled={room.playing} onclick={onsettings} class="kk-btn inline-flex items-center gap-1.5"><Icon name="tune" size={20} />{t('play.room.settings')}</button>
			{/if}

			<div class="ml-auto flex flex-wrap items-center gap-2">
				<button type="button" onclick={() => store.leaveRoom()} class="kk-btn kk-btn-danger inline-flex min-h-14 items-center gap-1.5 px-5"><Icon name="logout" size={22} />{t('play.room.leave')}</button>
				{#if store.isMaster}
					<button type="button" disabled={room.playing} onclick={() => store.start()} class="kk-btn kk-btn-hero kk-btn-lg w-44"><span class="kk-btn-icon"><Icon name="play_arrow" size={28} fill /></span><span class="kk-btn-label">{t('play.room.start')}</span></button>
				{:else}
					<button type="button" disabled={room.playing || spectating} onclick={() => store.toggleReady()} class="kk-btn kk-btn-lg w-44 {ready ? 'kk-btn-amber' : 'kk-btn-hero'}"><span class="kk-btn-icon"><Icon name={ready ? 'close' : 'check'} size={28} /></span><span class="kk-btn-label">{ready ? t('play.room.unready') : t('play.room.ready')}</span></button>
				{/if}
			</div>
		</div>
	</section>

<InviteDialog {store} bind:open={inviteOpen} />
<AiDialog {store} bind:open={aiOpen} target={aiTarget} />
<KickVoteDialog {store} />
{/if}
