<script>
	import { t } from '../i18n.svelte.js';
	import Icon from '../lobby/Icon.svelte';

	/** 주인장 (a crown) or 상위 기여자 (a trophy), told by the icon; the words are for a screen reader and the tooltip. */
	let { role, size = 20 } = $props();
	const info = $derived(role === 'owner' ? { icon: 'crown', label: t('play.malmoi.role.owner'), color: '#d99a06' } : { icon: 'emoji_events', label: t('play.malmoi.role.top'), color: '#d99a06' });
</script>

<span role="img" aria-label={info.label} title={info.label} class="inline-flex shrink-0" style:color={info.color}><Icon name={info.icon} {size} fill /></span>
