<script>
	import { t } from '../i18n.svelte.js';
	import { groupModes } from '../lobbyModel.js';
	import Tabs from '../ui/Tabs.svelte';
	import RadioGroup from '../ui/RadioGroup.svelte';
	import Rich from '../ui/Rich.svelte';
	import { plainText } from '../connection.svelte.js';

	/** Categorized game picker: language tabs (한국어 / 영어 / 기타), the games of the open tab, and the chosen game's description. */
	let { net, mode = $bindable(0), idPrefix = 'mode' } = $props();
	const labels = $derived(net.meta.labels);
	const groups = $derived(groupModes(net.meta.modes, net.meta.rules));
	const modeGroup = $derived(groups.find((g) => g.modes.some((m) => m.index === mode))?.id ?? groups[0]?.id);
	let opened = $state(null); // the category the user last opened; otherwise follow the chosen game
	const category = $derived(opened && groups.some((g) => g.id === opened) ? opened : modeGroup);
	const current = $derived(groups.find((g) => g.id === category) ?? groups[0]);
	const tabs = $derived(groups.map((g) => ({ id: g.id, label: t(`play.create.group.${g.id}`) })));

	// Inside a category the language prefix is redundant ("한국어 끝말잇기" -> "끝말잇기").
	function shortName(key) {
		const full = labels[`game.mode.${key}.name`] ?? key;
		const prefix = t(`play.create.group.${current.id}`) + ' ';
		return full.startsWith(prefix) ? full.slice(prefix.length) : full;
	}
	const options = $derived(
		(current?.modes ?? []).map((m) => ({ value: m.index, label: shortName(m.key), tip: plainText(labels[`game.mode.${m.key}.x`]) }))
	);
	const description = $derived(labels[`game.mode.${net.meta.modes[mode]}.x`] ?? '');
</script>

<div class="flex flex-col gap-3">
	<Tabs bind:value={() => category, (v) => (opened = v)} {tabs} idPrefix="{idPrefix}-cat" label={t('play.options.mode')} />
	<div role="tabpanel" id="{idPrefix}-cat-panel-{category}" aria-labelledby="{idPrefix}-cat-tab-{category}" class="min-h-[8.5rem]">
		<RadioGroup variant="chips" label={t('play.options.mode')} bind:value={mode} {options} />
	</div>
	{#if description}
		<p class="min-h-[5rem] rounded-xl bg-(--kk-surface-2) px-3 py-2 text-xs leading-relaxed text-(color:--kk-muted)"><Rich html={description} /></p>
	{/if}
</div>
