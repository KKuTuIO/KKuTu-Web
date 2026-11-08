<script>
	import Icon from './Icon.svelte';
	import Moremi from './Moremi.svelte';

	/**
	 * A person in the 귓속말 lists: their moremi in the same round frame as everywhere else, or a silhouette when they are not here.
	 * `status` puts the presence dot on the corner.
	 * @type {{store:any, user?:any, size?:number, status?:''|'online'|'idle'|'dnd'}}
	 */
	let { store, user = null, size = 44, status = '' } = $props();
	const DOT = { online: 'bg-[#22c55e]', idle: 'bg-[#f59e0b]', dnd: 'bg-[#ef4444]' };
</script>

<span class="relative inline-block shrink-0" style:width="{size}px" style:height="{size}px">
	{#if user}
		<Moremi equip={user.equip} shop={store.shop} parts={store.net.meta.moremiParts} {size} label={user.name} />
	{:else}
		<span class="grid size-full place-items-center rounded-full bg-(--kk-surface-2) text-(color:--kk-muted)"><Icon name="person" size={Math.round(size * 0.55)} fill /></span>
	{/if}
	{#if status}<span class="absolute -right-0.5 -bottom-0.5 size-3 rounded-full border-2 border-(--kk-surface) {DOT[status]}"></span>{/if}
</span>
