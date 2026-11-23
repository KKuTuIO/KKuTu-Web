<script>
	import { t } from '../i18n.svelte.js';
	import { confirm } from '../ui/confirm.svelte.js';
	import Icon from './Icon.svelte';

	/**
	 * 프로필에 올려 둔 업적(최대 2개). 내 것이면 누르면 내릴지 묻고, 2개 미만이면 점선 칸으로 자랑할 업적을 고르러 가게 한다.
	 * 남의 프로필에서는 올려 둔 것만 보이고, 없으면 아무것도 그리지 않는다.
	 * @type {{items: {k:string,n:string}[], isMe: boolean, canPick?: boolean, onpick?: () => void, onremove?: (key: string) => void}}
	 */
	let { items = [], isMe = false, canPick = false, onpick, onremove } = $props();
	async function remove(item) {
		if (await confirm({ message: t('play.achievement.showcase.confirmRemove', { name: item.n }) })) onremove?.(item.k);
	}
</script>

{#if items.length || (isMe && canPick)}
	<div class="grid grid-cols-2 gap-2.5">
		{#each items as item (item.k)}
			{#if isMe}
				<button type="button" onclick={() => remove(item)} class="flex min-w-0 items-center gap-2 rounded-xl border border-(color:--kk-line) bg-(--kk-surface) p-2.5 text-left hover:bg-(--kk-surface-2)" title={t('play.achievement.showcase.removeHint')}>
					<Icon name="emoji_events" size={22} fill class="shrink-0 text-(color:--kk-accent)" />
					<b class="line-clamp-2 min-w-0 text-sm leading-snug break-keep">{item.n}</b>
				</button>
			{:else}
				<div class="flex min-w-0 items-center gap-2 rounded-xl border border-(color:--kk-line) bg-(--kk-surface) p-2.5">
					<Icon name="emoji_events" size={22} fill class="shrink-0 text-(color:--kk-accent)" />
					<b class="line-clamp-2 min-w-0 text-sm leading-snug break-keep">{item.n}</b>
				</div>
			{/if}
		{/each}
		{#if isMe && canPick && items.length < 2}
			<button type="button" onclick={() => onpick?.()} class="flex items-center justify-center gap-1.5 rounded-xl border border-dashed border-(color:--kk-line) p-2.5 text-sm font-bold text-(color:--kk-muted) hover:bg-(--kk-surface-2)">
				<Icon name="add" size={20} />{t('play.achievement.showcase.add')}
			</button>
		{/if}
	</div>
{/if}
