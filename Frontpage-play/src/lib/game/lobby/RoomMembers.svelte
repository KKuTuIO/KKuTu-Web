<script>
	import { t } from '../i18n.svelte.js';
	import { prefs } from '../prefs.svelte.js';
	import { confirm } from '../ui/confirm.svelte.js';
	import Tooltip from '../ui/Tooltip.svelte';
	import Icon from './Icon.svelte';
	import LevelIcon from './LevelIcon.svelte';
	import RatingMark from './RatingMark.svelte';
	import { fullNick } from './nickTip.js';

	/**
	 * Who is in my room, in the side panel (it replaces the 접속자 list while a game runs, and until its result is closed): the players, the AIs and
	 * those watching. The room's master can remove a member from here (what the server allows: while the game runs only those not
	 * playing it, and no AI) or hand the room over (only while it waits). Clicking a row opens the same menu as a seat.
	 */
	let { store } = $props();
	const room = $derived(store.currentRoom);
	const playing = $derived(!!room?.playing);
	const seq = $derived(new Set(room?.game?.seq ?? []));
	const watching = (u) => u.game.form === 'S' || (playing && !u.robot && !seq.has(u.id));
	/** The master first, then the players and AIs, then those watching. */
	const members = $derived(
		[...store.roomPlayers].sort((a, b) => (b.id === room?.master) - (a.id === room?.master) || watching(a) - watching(b))
	);
	const status = (u) =>
		u.id === room?.master ? 'master' : u.robot ? 'ready' : watching(u) ? 'spectate' : u.game.practice ? 'practice' : playing ? null : u.game.ready ? 'ready' : 'wait';
	const canKick = (u) => store.isMaster && u.id !== store.net.id && (playing ? !u.robot && watching(u) : true);
	const canHandover = (u) => store.isMaster && !playing && !u.robot && u.id !== store.net.id;
	const name = (u) => (prefs.hideUsers && u.id !== store.net.id && !u.robot ? t('play.anonymous') : u.name);
	async function handover(u) {
		if (await confirm({ message: t('play.confirm.handover') })) store.handover(u.id);
	}
</script>

<ul class="min-h-0 flex-1 overflow-y-auto p-2" aria-label={t('play.side.room')}>
	{#each members as u (u.id)}
		{@const s = status(u)}
		<li class="flex items-center gap-1 rounded-lg hover:bg-(--kk-surface-2)">
			<button type="button" onclick={(e) => store.hooks.openUserMenu?.(u.id, e, 'roomUser')} class="flex min-h-10 min-w-0 flex-1 items-center gap-2.5 rounded-lg px-2 text-left">
				<LevelIcon level={u.lv} size={22} maskable={u.id !== store.net.id && !u.robot} />
				<Tooltip text={fullNick(u)}><span class="truncate">{name(u)}</span></Tooltip>
				{#if u.rating != null && u.rating < 2}<RatingMark rating={u.rating} size={15} mine={u.id === store.net.id} />{/if}
				{#if s}<span class="ml-auto shrink-0 rounded-md px-1.5 py-0.5 text-[11px] leading-none font-extrabold {s === 'practice' ? 'text-[#2c2200]' : 'text-white'}" style:background-color="var(--kk-st-{s})">{t(`play.room.status.${s}`)}</span>{/if}
			</button>
			{#if canHandover(u)}
				<Tooltip text={t('play.profile.handover')}><button type="button" aria-label="{t('play.profile.handover')}: {u.name}" onclick={() => handover(u)} class="grid size-8 shrink-0 place-items-center rounded-lg text-(color:--kk-muted) hover:bg-(--kk-accent-soft)"><Icon name="swap_horiz" size={20} /></button></Tooltip>
			{/if}
			{#if canKick(u)}
				<Tooltip text={t('play.profile.kick')}><button type="button" aria-label="{t('play.profile.kick')}: {u.name}" onclick={() => store.kick(u.id, u.robot)} class="mr-1 grid size-8 shrink-0 place-items-center rounded-lg text-(color:--kk-danger) hover:bg-(--kk-accent-soft)"><Icon name="exit_to_app" size={20} /></button></Tooltip>
			{/if}
		</li>
	{/each}
</ul>
