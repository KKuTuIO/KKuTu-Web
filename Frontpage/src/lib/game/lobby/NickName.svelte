<script>
	import Icon from './Icon.svelte';
	import { prefs } from '../prefs.svelte.js';
	import { t } from '../i18n.svelte.js';
	/** A nickname the way the legacy client shows it: name, then a small grey #tag; fixed nicknames (no tag) can show a check mark. */
	let { user, showCheck = false, hideTag = false, maskable = false } = $props();
	// 참여자 숨기기: other players show up under the service's name (legacy: "끄투리오").
	const masked = $derived(maskable && prefs.hideUsers);
	// The name skin they wear (`NIK`): the legacy `x-<id>` class on the name, everywhere a name is shown.
	const skin = $derived(!masked && user.equip?.NIK ? `x-${user.equip.NIK}` : '');
</script>

<span class={skin}>{#if masked}{t('play.anonymous')}{:else}{user.name}{#if user.tag && !hideTag}<small class="font-normal text-(color:--kk-muted)">#{user.tag}</small>{:else if user.fixed && showCheck}<Icon name="check" size={14} class="ml-0.5 align-[-1px] text-(color:--kk-accent-strong)" />{/if}{/if}</span>
