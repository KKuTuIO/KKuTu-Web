<script>
	import { t } from '../i18n.svelte.js';
	import Checkbox from '../ui/Checkbox.svelte';
	import Select from '../ui/Select.svelte';
	import Tooltip from '../ui/Tooltip.svelte';
	import Rich from '../ui/Rich.svelte';
	/**
	 * The option checkboxes a mode allows (each with its description on hover), optionally preceded by a simple
	 * mode dropdown (quick match). The create-room dialog brings its own categorized picker and sets showMode=false.
	 */
	let { net, mode = $bindable(0), opts = $bindable({}), showMode = true, dense = false } = $props();
	const key = $derived(net.meta.modes[mode]);
	const rule = $derived(net.meta.rules[key]);
	const label = (k) => net.meta.labels[`game.mode.${k}.name`] ?? k;
	// "inj" has no option definition: it is the 단어장 (word-theme picker, InjeongPick). It is a planned
	// feature, so it is listed once as a disabled TBD entry. `noguest` options are member-only; `req`
	// options need another option switched on.
	const WORDBOOK = { k: 'injeongpick', req: null, tbd: true, expl: 'game.option.InjeongPick.expl' };
	const optionList = $derived(
		(rule?.opts ?? [])
			.map((code) => ({ code, def: net.meta.optionDefs[code] }))
			.filter(({ code, def }) => (def || code === 'inj') && !(def?.noguest && net.guest))
			.map(({ code, def }) =>
				code === 'inj'
					? { ...WORDBOOK, text: net.meta.labels['game.option.InjeongPick.name'] ?? t('game.option.InjeongPick.name') }
					: {
							k: def.name.toLowerCase(),
							text: net.meta.labels[`game.option.${def.name}.name`] ?? def.name,
							req: def.req ? net.meta.optionDefs[def.req]?.name.toLowerCase() : null,
							expl: `game.option.${def.name}.expl`,
							tbd: false
						}
			)
	);
	// Options the new mode doesn't offer must not be sent along.
	$effect(() => {
		const allowed = new Set(optionList.map((o) => o.k));
		for (const k of Object.keys(opts)) if (!allowed.has(k) || k === WORDBOOK.k) delete opts[k];
		for (const o of optionList) if (o.req && !opts[o.req] && opts[o.k]) opts[o.k] = false;
	});
</script>

{#if showMode}
	<div class="flex flex-col gap-1">
		<span class="text-xs font-bold text-(color:--kk-muted)">{t('play.options.mode')}</span>
		<Select label={t('play.options.mode')} bind:value={mode} options={net.meta.modes.map((k, i) => ({ value: i, label: label(k) }))} />
	</div>
{/if}
{#if optionList.length}
	<fieldset class={showMode ? 'mt-3' : ''}>
		<legend class="pb-1 text-xs font-bold text-(color:--kk-muted)">{t('play.options.rules')}</legend>
		<div class="grid grid-cols-2 gap-x-3">
			{#each optionList as o (o.k)}
				{@const expl = net.meta.labels[o.expl]}
				{#snippet tip()}<Rich html={expl} />{/snippet}
				<Tooltip content={expl ? tip : undefined} placement="top">
					<Checkbox {dense} checked={!!opts[o.k]} disabled={o.tbd || (!!o.req && !opts[o.req])} onchange={(v) => (opts[o.k] = v)}>
						{o.text}{#if o.tbd}<span class="kk-chip ml-1">{t('play.options.tbd')}</span>{/if}
					</Checkbox>
				</Tooltip>
			{/each}
		</div>
	</fieldset>
{/if}
