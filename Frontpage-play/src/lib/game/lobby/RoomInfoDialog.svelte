<script>
	import { t } from '../i18n.svelte.js';
	import { plainText } from '../protocol.js';
	import { prefs } from '../prefs.svelte.js';
	import { maskBad } from '../badwords.js';
	import Dialog from './Dialog.svelte';
	import Icon from './Icon.svelte';
	import Moremi from './Moremi.svelte';
	import LevelIcon from './LevelIcon.svelte';
	import Tooltip from '../ui/Tooltip.svelte';
	import { toast } from '../ui/toast.svelte.js';

	/**
	 * A room's details (the legacy 방 정보): title, game, rounds, and who is in it: players, AIs and those watching, each with
	 * their team and whether they play or watch. From the list it can enter the room, show the master's profile, or report the
	 * room; the master of this very room can remove an observer from it while its game runs. Opened by `store.hooks.openRoomInfo(no)`.
	 */
	let { store, onjoin } = $props();
	let open = $state(false);
	let no = $state(null);
	const net = $derived(store.net);
	const room = $derived(no === null ? null : (net.rooms[no] ?? null));
	const mine = $derived(!!room && net.myRoom === room.no);
	const L = (k) => plainText(net.meta.labels[k] ?? '');
	const parts = $derived(net.meta.moremiParts);
	const seq = $derived(new Set(room?.game?.seq ?? []));
	const TEAM = ['', 'A', 'B', 'C', 'D'];

	export function show(n) {
		if (!net.rooms[n]) return toast(L('kkutu.js.error.466'), { kind: 'error' });
		no = n;
		open = true;
	}
	$effect(() => {
		if (open && !room) open = false; // the room is gone
	});

	// the master first, then those playing, then those watching
	const members = $derived(
		room
			? room.players
					.map((id) => net.users[id])
					.filter(Boolean)
					.map((u) => {
						const spectator = !!room.playing && !u.robot && !seq.has(u.id);
						return { u, form: u.robot ? 'J' : u.game.form === 'S' ? 'S' : spectator ? 'O' : 'J', spectator };
					})
					.sort((a, b) => (b.u.id === room.master) - (a.u.id === room.master) || (a.form === 'J' ? 0 : 1) - (b.form === 'J' ? 0 : 1))
			: []
	);
	const canKick = (m) => mine && store.isMaster && room.playing && m.spectator && !m.u.robot;
	const name = (u) => (prefs.hideUsers && u.id !== net.id && !u.robot ? t('play.anonymous') : u.name);
	const enter = () => {
		open = false;
		onjoin?.(room.no);
	};
	const masterInfo = () => store.hooks.openProfile?.(room.master);
</script>

<Dialog bind:open title={room ? `${room.no}${L('kkutu.js.sRoomInfo')}` : ''}>
	{#if room}
		<div class="flex w-[min(30rem,86vw)] flex-col gap-4">
			<dl class="grid grid-cols-[auto_1fr] items-center gap-x-4 gap-y-2 text-sm">
				<dt class="font-bold text-(color:--kk-muted)">{L('kkutu.dialog.roomInfo.roomTitle')}</dt>
				<dd class="flex min-w-0 items-center gap-1.5 font-extrabold"><span class="truncate">{maskBad(room.title)}</span>{#if room.locked}<Icon name="lock" size={17} fill class="shrink-0 text-(color:--kk-muted)" />{/if}</dd>
				<dt class="font-bold text-(color:--kk-muted)">{L('kkutu.dialog.roomInfo.gameMode')}</dt>
				<dd class="font-extrabold">{room.mode}</dd>
				<dt class="font-bold text-(color:--kk-muted)">{L('kkutu.dialog.roomInfo.rounds')}</dt>
				<dd class="font-extrabold tabular-nums">{room.rounds}, {t('play.create.timeValue', { n: room.time })}</dd>
			</dl>

			<section aria-label={L('kkutu.dialog.roomInfo.players')} class="flex min-h-0 flex-col gap-2">
				<h3 class="flex items-center justify-between border-t border-(color:--kk-line) pt-3 text-sm font-extrabold">
					{L('kkutu.dialog.roomInfo.players')}
					<span class="tabular-nums text-(color:--kk-muted)">{room.cur} / {room.limit}</span>
				</h3>
				<ul class="flex max-h-72 flex-col gap-1.5 overflow-y-auto">
					{#each members as m (m.u.id)}
						{@const u = m.u}
						<li class="flex items-center gap-1 rounded-xl border border-(color:--kk-line) bg-(--kk-surface) pr-1 shadow-[0_2px_0_var(--kk-line)]">
							<button type="button" disabled={u.robot} onclick={() => store.hooks.openProfile?.(u.id)} class="flex min-h-12 min-w-0 flex-1 items-center gap-2.5 rounded-xl px-2 py-1 text-left enabled:hover:bg-(--kk-surface-2)">
								<Moremi equip={u.equip} shop={store.shop} {parts} size={36} label={u.name} frame={false} maskable={!u.robot && u.id !== net.id} />
								<LevelIcon level={u.lv} size={20} maskable={!u.robot && u.id !== net.id} />
								<span class="min-w-0 flex-1 truncate font-bold">{name(u)}</span>
								{#if u.id === room.master}<span class="shrink-0 rounded-md px-1.5 py-0.5 text-[11px] leading-none font-extrabold text-white" style:background-color="var(--kk-st-master)">{t('play.room.status.master')}</span>{/if}
								{#if u.game.team > 0 && m.form !== 'S'}<span class="shrink-0 rounded-md px-1.5 py-0.5 text-[11px] leading-none font-extrabold text-[#111]" style:background-color="var(--kk-team-{u.game.team})">{TEAM[u.game.team]}</span>{/if}
								<span class="shrink-0 text-xs font-bold text-(color:--kk-muted)">{L(`kkutu.js.pform.${m.form}`)}</span>
							</button>
							{#if canKick(m)}
								<Tooltip text={L('kkutu.dialog.roomInfo.kickSpectator')}><button type="button" aria-label="{L('kkutu.dialog.roomInfo.kickSpectator')}: {u.name}" onclick={() => store.kick(u.id)} class="grid size-8 shrink-0 place-items-center rounded-lg text-(color:--kk-danger) hover:bg-(--kk-accent-soft)"><Icon name="exit_to_app" size={20} /></button></Tooltip>
							{/if}
						</li>
					{/each}
				</ul>
			</section>

			<div class="flex flex-wrap justify-end gap-2">
				<button type="button" onclick={() => ((open = false), store.hooks.openReport?.({ type: 'room', no: room.no }))} class="kk-btn inline-flex items-center gap-1.5 text-(color:--kk-danger)"><Icon name="flag" size={19} />{L('kkutu.dialog.roomInfo.reportBtn')}</button>
				{#if !mine}
					<button type="button" onclick={masterInfo} class="kk-btn inline-flex items-center gap-1.5"><Icon name="account_circle" size={19} />{L('kkutu.dialog.roomInfo.masterBtn')}</button>
					<button type="button" onclick={enter} class="kk-btn kk-btn-primary inline-flex items-center gap-1.5">{L('kkutu.dialog.roomInfo.joinBtn')}</button>
				{/if}
			</div>
		</div>
	{/if}
</Dialog>
