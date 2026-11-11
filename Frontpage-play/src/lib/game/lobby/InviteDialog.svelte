<script>
	import { t } from '../i18n.svelte.js';
	import Dialog from './Dialog.svelte';
	import LevelIcon from './LevelIcon.svelte';
	import NickName from './NickName.svelte';
	import EmptyState from './EmptyState.svelte';
	import Select from '../ui/Select.svelte';
	import { confirm } from '../ui/confirm.svelte.js';
	import { toast } from '../ui/toast.svelte.js';
	import Icon from './Icon.svelte';
	import { shareRoomOnKakao } from '../kakao.js';
	import ShareButtons from './ShareButtons.svelte';

	/** Invite someone from the lobby to this room, or add an AI. */
	let { store, open = $bindable(false) } = $props();
	let aiLevel = $state(3);
	$effect(() => {
		if (open) aiLevel = 3; // every opening starts from the defaults
	});
	// People standing in the lobby (not in a room), other than me. AI seats never appear here.
	const candidates = $derived(Object.values(store.net.users).filter((u) => u.place === 0 && u.id !== store.net.id && !u.robot).sort((a, b) => b.lv - a.lv));
	const levels = $derived([0, 1, 2, 3, 4, 5].map((n) => ({ value: n, label: `${t(`play.ai.level.${n}`)} ${t('play.ai.robot')}` })));

	// Share options: anyone (guests included) can pass the room's address on.
	const room = $derived(store.currentRoom);
	const link = $derived(typeof location === 'undefined' || !room ? '' : `${location.origin}${location.pathname}#${room.no}`);
	async function shareKakao() {
		const me = store.net.users[store.net.id];
		await shareRoomOnKakao({ USERNAME: me?.name ?? '', SERVERID: store.server, ROOMNUMBER: room.no, GAMETYPE: room.mode }); // (a failure is said by the buttons)
	}

	async function ask(u) {
		if (await confirm({ message: t('play.confirm.invite', { nick: u.name }) })) store.invite(u.id);
	}
</script>

<Dialog bind:open title={t('play.invite.title')}>
	<div class="flex flex-col gap-4">
		<div class="flex flex-col gap-2">
			<h3 class="text-sm font-extrabold">{t('play.invite.share')}</h3>
			<ShareButtons {link} title={t('play.invite.shareTitle', { nick: store.net.users[store.net.id]?.name ?? '' })} text={room ? t('play.invite.shareText', { room: room.no, mode: room.mode }) : ''} onkakao={shareKakao} />
		</div>
		<ul class="flex max-h-72 min-h-40 flex-col overflow-y-auto rounded-xl border-[1.5px] border-(color:--kk-line)">
			{#each candidates as u (u.id)}
				<li class="border-b border-(color:--kk-line) last:border-b-0">
					<button type="button" onclick={() => ask(u)} class="flex min-h-11 w-full items-center gap-2.5 px-3 text-left hover:bg-(--kk-accent-soft)"><LevelIcon level={u.lv} size={22} /><span class="truncate font-bold"><NickName user={u} showCheck /></span></button>
				</li>
			{:else}
				<li class="grid flex-1 place-items-center"><EmptyState compact size={110} title={t('play.invite.empty')} /></li>
			{/each}
		</ul>
		<div class="flex items-end gap-2">
			<div class="flex min-w-0 flex-1 flex-col gap-1"><span class="text-xs font-bold text-(color:--kk-muted)">{t('play.invite.aiLevel')}</span><Select label={t('play.invite.aiLevel')} bind:value={aiLevel} options={levels} /></div>
			<button type="button" class="kk-btn kk-btn-primary min-h-11 shrink-0 px-5" onclick={() => { store.invite('AI', aiLevel); open = false; }}>{t('play.invite.ai')}</button>
		</div>
	</div>
</Dialog>
