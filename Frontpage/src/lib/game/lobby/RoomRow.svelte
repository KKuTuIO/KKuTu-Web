<script>
	import { t } from '../i18n.svelte.js';
	import Icon from './Icon.svelte';
	import { modeGlyph, roomMeta } from '../lobbyModel.js';
	let { room, tile = false, onjoin } = $props();
	const full = $derived(room.cur >= room.max);
	const pct = $derived(`${Math.min(100, (room.cur / Math.max(room.max, 1)) * 100)}%`);
</script>

{#snippet lock()}
	{#if room.locked}
		<span role="img" aria-label={t('play.rooms.locked')} class="grid size-6 shrink-0 place-items-center rounded-lg bg-(--kk-text) text-(--kk-surface)">
			<Icon name="lock" size={15} fill />
		</span>
	{/if}
{/snippet}
{#snippet status()}
	{#if room.playing}
		<span class="inline-flex items-center gap-1.5 text-xs font-bold text-(color:--kk-muted)"><span class="size-2 rounded-full bg-(--kk-line-strong)"></span>{t('play.rooms.status.playing')}</span>
	{:else}
		<span class="inline-flex items-center gap-1.5 text-xs font-bold text-(color:--kk-accent-strong)"><span class="size-2 rounded-full bg-(--kk-accent)"></span>{t('play.rooms.status.waiting')}</span>
	{/if}
{/snippet}
{#snippet tags()}
	{#each room.tags as t}<span class="kk-chip">{t}</span>{/each}
{/snippet}
{#snippet seats()}
	<span class="flex items-center gap-2">
		<span class="kk-bar h-1.5 w-14"><span style:width={pct}></span></span>
		<span class="text-sm font-extrabold tabular-nums {full ? 'text-(color:--kk-warn)' : ''}">{room.cur}/{room.max}</span>
	</span>
{/snippet}

{#if tile}
	<button type="button" onclick={onjoin} class="kk-card group flex min-h-28 overflow-hidden text-left transition hover:-translate-y-0.5 hover:border-(color:--kk-accent) {room.playing ? 'opacity-70' : ''}">
		<span class="grid w-16 shrink-0 place-items-center bg-(--kk-accent-soft) text-xl font-black text-(color:--kk-accent-strong) tabular-nums">{room.no}</span>
		<span class="flex min-w-0 flex-1 flex-col gap-1.5 p-3">
			<span class="flex items-center gap-2"><span class="truncate font-extrabold">{room.title}</span>{@render lock()}<span class="ml-auto">{@render seats()}</span></span>
			<span class="text-sm font-bold">{room.mode}</span>
			<span class="flex flex-wrap gap-1">{@render tags()}</span>
			<span class="mt-auto flex items-center justify-between text-xs text-(color:--kk-muted)"><span>{roomMeta(room)}</span>{@render status()}</span>
		</span>
	</button>
{:else}
	<button type="button" onclick={onjoin} class="grid min-h-16 w-full grid-cols-[72px_minmax(220px,1.6fr)_190px_minmax(150px,1.4fr)_120px_110px_84px] items-center border-b border-(color:--kk-line) px-3.5 py-1.5 text-left hover:bg-(--kk-accent-soft) {room.playing ? 'opacity-70' : ''}">
		<span class="text-xl font-black text-(color:--kk-accent-strong) tabular-nums">{room.no}</span>
		<span class="flex min-w-0 items-center gap-2.5 pr-2">
			<span aria-hidden="true" class="grid size-9 shrink-0 place-items-center rounded-xl bg-(--kk-accent-soft) text-sm font-black text-(color:--kk-accent-strong)">{modeGlyph(room)}</span>
			<span class="truncate font-extrabold">{room.title}</span>{@render lock()}
		</span>
		<span class="text-sm font-bold">{room.mode}</span>
		<span class="flex flex-wrap gap-1 pr-2">{@render tags()}</span>
		<span class="text-sm text-(color:--kk-muted)">{roomMeta(room)}</span>
		<span>{@render seats()}</span>
		<span>{@render status()}</span>
	</button>
{/if}
