<script>
	import Moremi from '../lobby/Moremi.svelte';
	import { look } from './store.svelte.js';

	/** A person as their Moremi, which is their profile picture in this game. `name` is the one shown to people; the others wear the default look (the server does not send their wear yet). */
	let { name, size = 28, ring = 'var(--kk-surface)', class: cls = '' } = $props();
	const mine = $derived(!!look.name && name === look.name);
</script>

<span class="inline-flex shrink-0 rounded-full {cls}" style:box-shadow="0 0 0 2px {ring}">
	<Moremi equip={mine ? look.equip : {}} shop={look.shop} parts={look.parts} {size} label={name} animated={false} hide={['back', 'badge', 'lhand', 'rhand', 'front']} />
</span>
