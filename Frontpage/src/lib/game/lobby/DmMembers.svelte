<script>
	import { t } from '../i18n.svelte.js';
	import DmAvatar from './DmAvatar.svelte';
	import NickName from './NickName.svelte';
	import Icon from './Icon.svelte';

	/**
	 * Who can be whispered to: a row per person online (avatar with presence dot, nickname, and their `name#tag` at the right), the one
	 * under the cursor / arrow keys highlighted. Used by the 새 대화 picker and by the autocomplete that opens after "/e ".
	 * @type {{store:any, rows:{id:string,user:any,handle:string,friend:boolean}[], active?:number, onpick:(row:any)=>void, id:string}}
	 */
	let { store, rows, active = -1, onpick, id } = $props();
	const status = (r) => (r.friend ? (store.net.friendsStatus[r.id] ?? 'online') : 'online');
</script>

<div role="listbox" {id} aria-label={t('play.dm.members')} class="flex flex-col gap-0.5">
	{#each rows as r, i (r.id)}
		<!-- mousedown, not click: the chat box must keep its focus while the list is used -->
		<button type="button" role="option" id="{id}-{i}" aria-selected={i === active} tabindex="-1" onmousedown={(e) => (e.preventDefault(), onpick(r))} class="flex min-h-12 w-full items-center gap-3 rounded-xl px-2.5 py-1.5 text-left {i === active ? 'bg-(--kk-surface-2)' : 'hover:bg-(--kk-surface-2)'}">
			<DmAvatar {store} user={r.user} size={36} status={status(r)} />
			<span class="min-w-0 flex-1 truncate text-sm font-extrabold"><NickName user={r.user} hideTag />{#if r.tag}<small class="ml-0.5 font-normal text-(color:--kk-muted)">#{r.tag}</small>{/if}</span>
			{#if r.friend}<Icon name="star" size={14} fill class="text-(color:--kk-warn)" />{/if}
		</button>
	{/each}
</div>
