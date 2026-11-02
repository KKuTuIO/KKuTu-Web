<script>
	import { t } from '../i18n.svelte.js';
	import Moremi from './Moremi.svelte';
	import LevelIcon from './LevelIcon.svelte';
	let { store } = $props();
	const room = $derived(store.currentRoom);
	const parts = $derived(store.net.meta.moremiParts);
</script>

<!-- Interim in-room view: the full room/game screens come later; this keeps a player from being stranded. -->
{#if room}
	<section aria-label={t('play.room.label')} class="fixed inset-0 z-30 grid place-items-center bg-(--kk-bg) p-4">
		<div class="flex w-full max-w-lg flex-col gap-4 kk-card p-5">
			<div class="flex items-center justify-between gap-3">
				<div class="min-w-0"><p class="text-xs text-(color:--kk-muted)">[{room.no}] {room.mode}</p><h2 class="truncate text-lg font-bold">{room.title}</h2></div>
				<span class="font-bold">{room.cur} / {room.max}</span>
			</div>
			<ul class="grid grid-cols-2 gap-2">
				{#each room.players as id (id)}
					{@const u = store.net.users[id]}
					<li class="flex items-center gap-2 rounded-lg border border-(color:--kk-line) p-2">
						<Moremi equip={u?.equip} shop={store.shop} {parts} size={40} label={u?.name} />
						<span class="min-w-0"><span class="flex items-center gap-1"><LevelIcon level={u?.lv ?? 1} size={18} /><span class="truncate font-bold">{u?.name ?? id}</span></span>
							{#if id === room.master}<span class="text-xs text-(color:--kk-accent-strong)">{t('play.room.master')}</span>{/if}</span>
					</li>
				{/each}
			</ul>
			<p class="text-xs text-(color:--kk-muted)">{t('play.room.notReady')}</p>
			<button type="button" onclick={() => store.leaveRoom()} class="kk-btn min-h-12">{t('play.room.leave')}</button>
		</div>
	</section>
{/if}
