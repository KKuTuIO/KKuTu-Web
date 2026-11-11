<script>
	import { t } from '../i18n.svelte.js';
	import { CAST, castEquip } from '../story/cast.js';
	import Moremi from './Moremi.svelte';

	/**
	 * A character of the cast (default: 학사모레미, the tutorial's guide): the default Moremi wearing the character's items
	 * (학사모: `moremi/head/haksamo`). `shop` is the item catalogue; the cast's items are added to it when the catalogue doesn't list them,
	 * so the character never loses its cap.
	 */
	let { store, who = 'haksamo', size = 48, frame = false, animated = true, class: cls = '' } = $props();
	const equip = $derived(castEquip(who));
	const shop = $derived({ ...Object.fromEntries(Object.entries(equip).map(([slot, id]) => [id, { _id: id, group: slot, options: {} }])), ...(store?.shop ?? {}) });
	const parts = $derived(store?.net?.meta?.moremiParts ?? []);
</script>

<Moremi {equip} {shop} {parts} {size} {frame} {animated} label={t(CAST[who]?.name ?? 'play.mascot.haksamo')} class={cls} />
