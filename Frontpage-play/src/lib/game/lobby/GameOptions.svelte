<script>
	import { t } from '../i18n.svelte.js';
	import Checkbox from '../ui/Checkbox.svelte';
	import Select from '../ui/Select.svelte';
	import Tooltip from '../ui/Tooltip.svelte';
	import Rich from '../ui/Rich.svelte';
	import ThemeSection from './ThemeSection.svelte';
	import { needsThemes as rulePicks, ruleCodes } from '../lobbyModel.js';
	/**
	 * The option checkboxes a mode allows (each with its description on hover), optionally preceded by a simple
	 * mode dropdown (quick match). The create-room dialog brings its own categorized picker and sets showMode=false.
	 * With `withThemes` (create / edit room) it also offers the 단어장: modes that always need word themes (`ijp`) show the
	 * theme picker, modes where it is an option (`inp`) show it once that checkbox is on. `injpick` is the chosen codes.
	 */
	let { net, mode = $bindable(0), opts = $bindable({}), injpick = $bindable([]), withThemes = false, showMode = true, dense = false } = $props();
	const key = $derived(net.meta.modes[mode]);
	const rule = $derived(net.meta.rules[key]);
	const label = (k) => net.meta.labels[`game.mode.${k}.name`] ?? k;
	// `noguest` options are member-only; `req` options need another option switched on. "ijp" has no option
	// definition: it is the 단어장 that the mode always needs, so there is nothing to switch on (see `needsThemes`).
	// "inp" (InjeongPick) is the optional 단어장 of the other modes; without the theme picker (quick match) it is hidden.
	const optionList = $derived(
		ruleCodes(rule)
			.map((code) => ({ code, def: net.meta.optionDefs[code] }))
			.filter(({ code, def }) => def && !(def.noguest && net.guest) && (withThemes || code !== 'inp'))
			.map(({ code, def }) => ({
				code,
				k: def.name.toLowerCase(),
				text: net.meta.labels[`game.option.${def.name}.name`] ?? def.name,
				req: def.req ? net.meta.optionDefs[def.req]?.name.toLowerCase() : null,
				reqCode: def.req ?? null,
				group: def.group ?? null,
				expl: `game.option.${def.name}.expl`
			}))
	);
	// Legacy rules for the checkboxes: switching one on also switches on what it requires; switching one off switches off whatever
	// requires it; and the options of one group (one manner level, one proverb/long-text choice, ...) exclude each other.
	// Nothing is disabled: the other boxes simply follow.
	const defs = $derived(net.meta.optionDefs ?? {});
	const nameOf = (code) => defs[code]?.name.toLowerCase();
	function turnOff(code) {
		opts[nameOf(code)] = false;
		for (const [c, d] of Object.entries(defs)) if (d.req === code && opts[nameOf(c)]) turnOff(c);
	}
	function turnOn(code) {
		const def = defs[code];
		opts[nameOf(code)] = true;
		if (def.req && !opts[nameOf(def.req)]) turnOn(def.req);
		if (def.group) for (const [c, d] of Object.entries(defs)) if (c !== code && d.group === def.group && opts[nameOf(c)]) turnOff(c);
	}
	const choose = (o, on) => (on ? turnOn(o.code) : turnOff(o.code));

	// Options the new mode doesn't offer must not be sent along.
	$effect(() => {
		const allowed = new Set(optionList.map((o) => o.k));
		for (const k of Object.keys(opts)) if (!allowed.has(k)) delete opts[k];
		for (const o of optionList) if (o.req && !opts[o.req] && opts[o.k]) opts[o.k] = false;
	});

	// 단어장: which themes can be picked depends on the mode's language.
	const lang = $derived(rule?.lang === 'en' ? 'en' : 'ko');
	const pickable = $derived(net.meta.pickThemes?.[lang] ?? []);
	const needsThemes = $derived(withThemes && !!rule && rulePicks(rule, opts));
	// Only themes of this language that may be picked stay; nothing is kept when the mode doesn't use them.
	$effect(() => {
		const keep = needsThemes ? injpick.filter((c) => pickable.includes(c)) : [];
		if (keep.length !== injpick.length) injpick = keep;
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
					<Checkbox {dense} checked={!!opts[o.k]} onchange={(v) => choose(o, v)}>
						{o.text}
					</Checkbox>
				</Tooltip>
			{/each}
		</div>
	</fieldset>
{/if}
{#if needsThemes}
	<!-- Required: a game with no theme cannot start (server answers 413), so the form insists on at least one. -->
	<ThemeSection {net} {lang} bind:injpick title={net.meta.labels['game.option.InjeongPick.name'] ?? t('play.options.themes')} />
{/if}
