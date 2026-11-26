<script>
	import { t } from '../i18n.svelte.js';
	import Tabs from '../ui/Tabs.svelte';
	import Icon from '../lobby/Icon.svelte';
	import Cover from './Cover.svelte';
	import { BG_COLORS, EMOJIS, ICON_COLORS, SYMBOLS, contrast, firstEmoji } from './model.js';

	let { cover = $bindable() } = $props();
	// svelte-ignore state_referenced_locally
	let tab = $state(cover.type);
	let typed = $state('');
	const faint = $derived(cover.type === 'symbol' && contrast(cover.color, cover.bg) < 2);
	const pick = (type, icon) => (cover = { ...cover, type, icon });
	function typeEmoji() {
		const e = firstEmoji(typed);
		if (e) pick('emoji', e);
	}
	const cell = (on) => `grid size-11 place-items-center rounded-lg border-2 ${on ? 'border-(color:--kk-accent) bg-(--kk-accent-soft)' : 'border-transparent hover:bg-(--kk-surface-2)'}`;
</script>

{#snippet swatches(label, colors, value, set)}
	<div class="flex flex-col gap-1.5" role="group" aria-label={label}>
		<span class="text-xs font-bold text-(color:--kk-muted)">{label}</span>
		<div class="flex flex-wrap items-center gap-2">
			{#each colors as c (c)}
				<button type="button" aria-label="{label} {c}" aria-pressed={value === c} onclick={() => set(c)} class="size-8 rounded-full border border-black/15 {value === c ? 'outline-3 outline-offset-2 outline-(--kk-accent)' : ''}" style:background-color={c}></button>
			{/each}
			<label class="kk-btn relative inline-flex min-h-9 cursor-pointer items-center gap-1 px-2.5 text-xs">
				<Icon name="palette" size={16} />{t('play.malmoi.cover.pickOwn')}
				<input type="color" value={value} oninput={(e) => set(e.currentTarget.value)} class="absolute inset-0 cursor-pointer opacity-0" aria-label={t('play.malmoi.cover.pickAria', { label })} />
			</label>
		</div>
	</div>
{/snippet}

<div class="flex flex-col gap-4">
	<div class="flex flex-col items-center gap-3 sm:flex-row sm:items-start sm:gap-4">
		<Cover {cover} size={96} />
		<div class="w-full min-w-0 sm:flex-1">
			<Tabs bind:value={tab} idPrefix="malmoi-cover" label={t('play.malmoi.cover.kind')} tabs={[{ id: 'symbol', label: t('play.malmoi.cover.symbol') }, { id: 'emoji', label: t('play.malmoi.cover.emoji') }]} />
			<div id="malmoi-cover-panel-{tab}" role="tabpanel" aria-labelledby="malmoi-cover-tab-{tab}" class="mt-2 flex flex-col gap-2">
				<div class="flex max-h-36 flex-wrap gap-1 overflow-y-auto p-0.5">
					{#if tab === 'symbol'}
						{#each SYMBOLS as s (s)}<button type="button" aria-label={s} aria-pressed={cover.type === 'symbol' && cover.icon === s} onclick={() => pick('symbol', s)} class={cell(cover.type === 'symbol' && cover.icon === s)}><Icon name={s} size={24} /></button>{/each}
					{:else}
						{#each EMOJIS as e (e)}<button type="button" aria-label={e} aria-pressed={cover.type === 'emoji' && cover.icon === e} onclick={() => pick('emoji', e)} class="{cell(cover.type === 'emoji' && cover.icon === e)} text-2xl">{e}</button>{/each}
					{/if}
				</div>
				{#if tab === 'emoji'}
					<input bind:value={typed} oninput={typeEmoji} maxlength="16" placeholder={t('play.malmoi.cover.otherEmoji')} aria-label={t('play.malmoi.cover.otherEmoji')} class="h-10 kk-input px-3" />
				{/if}
			</div>
		</div>
	</div>
	{#if cover.type === 'symbol'}
		{@render swatches(t('play.malmoi.cover.iconColor'), ICON_COLORS, cover.color, (c) => (cover = { ...cover, color: c }))}
	{/if}
	{@render swatches(t('play.malmoi.cover.bgColor'), BG_COLORS, cover.bg, (c) => (cover = { ...cover, bg: c }))}
	{#if faint}<p class="text-xs font-bold text-(color:--kk-danger)" role="status">{t('play.malmoi.cover.faint')}</p>{/if}
</div>
