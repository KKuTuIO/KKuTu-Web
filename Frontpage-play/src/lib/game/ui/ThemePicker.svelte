<script>
	import { t } from '../i18n.svelte.js';
	import Dialog from '../lobby/Dialog.svelte';
	import Icon from '../lobby/Icon.svelte';
	import Checkbox from './Checkbox.svelte';
	import RadioGroup from './RadioGroup.svelte';

	/**
	 * Picks word themes (주제). One control for every place that needs them:
	 *  - `multiple={false}`: choose one (radio list), `value` is a code or '';
	 *  - `multiple`: choose any number (check list), `value` is an array of codes.
	 * It shows a button with the choice, and opens a searchable list.
	 * @type {{themes:Record<string,string>, value:string|string[], multiple?:boolean, exclude?:string[], label?:string, placeholder?:string, onchange?:(v:any)=>void}}
	 */
	let { themes = {}, value = $bindable(), multiple = false, exclude = [], label = '', placeholder = '', onchange } = $props();
	let open = $state(false);
	let query = $state('');

	const skip = $derived(new Set(exclude));
	const all = $derived(
		Object.entries(themes)
			.filter(([code, name]) => !skip.has(code) && String(name).trim() !== '★')
			.map(([code, name]) => ({ value: code, label: String(name) }))
			.sort((a, b) => a.label.localeCompare(b.label, 'ko'))
	);
	const q = $derived(query.trim().toLowerCase());
	const shown = $derived(q ? all.filter((o) => o.label.toLowerCase().includes(q) || o.value.toLowerCase().includes(q)) : all);
	const selected = $derived(multiple ? (Array.isArray(value) ? value : []) : value ? [value] : []);
	const nameOf = (code) => themes[code] ?? code;
	// The button says what is chosen ("기계, 고적"), cut with … when it is too long for the field.
	const summary = $derived(!selected.length ? placeholder : selected.map(nameOf).join(', '));

	function set(v) {
		value = v;
		onchange?.(v);
	}
	const toggle = (code, on) => set(on ? [...selected.filter((c) => c !== code), code] : selected.filter((c) => c !== code));

	$effect(() => {
		if (open) query = '';
	});
</script>

<div class="min-w-0">
	<button type="button" aria-haspopup="dialog" aria-label={label ? `${label}: ${summary}` : summary} onclick={() => (open = true)} class="kk-input flex h-11 w-full items-center justify-between gap-2 px-3 text-left">
		<span class="min-w-0 truncate {selected.length ? 'font-bold' : 'text-(color:--kk-muted)'}">{summary}</span>
		<Icon name="expand_more" size={22} class="shrink-0 text-(color:--kk-muted)" />
	</button>
</div>

<Dialog bind:open title={label || t('play.theme.pick')}>
	<div class="flex flex-col gap-3">
		<div class="flex items-center gap-2">
			<input bind:value={query} aria-label={t('play.theme.search')} placeholder={t('play.theme.search')} autocomplete="off" class="h-11 min-w-0 flex-1 kk-input px-3" />
			<button type="button" class="kk-btn min-h-11 px-3 text-sm" disabled={!selected.length} onclick={() => set(multiple ? [] : '')}>{t('play.theme.clear')}</button>
		</div>
		<div class="max-h-[min(24rem,55dvh)] overflow-x-hidden overflow-y-auto rounded-xl border-[1.5px] border-(color:--kk-line) p-2">
			{#if !shown.length}
				<p class="py-8 text-center text-sm text-(color:--kk-muted)">{t('play.theme.none')}</p>
			{:else if multiple}
				<ul class="grid grid-cols-1 gap-x-3 sm:grid-cols-[repeat(2,minmax(0,1fr))]">
					{#each shown as o (o.value)}
						<li class="min-w-0 [&>button]:max-w-full"><Checkbox dense checked={selected.includes(o.value)} onchange={(on) => toggle(o.value, on)}><span class="min-w-0 truncate">{o.label}</span></Checkbox></li>
					{/each}
				</ul>
			{:else}
				<RadioGroup label={label || t('play.theme.pick')} {value} options={shown} onchange={(v) => ((open = false), set(v))} />
			{/if}
		</div>
		<div class="flex items-center justify-between gap-2">
			<span class="text-xs text-(color:--kk-muted)">{multiple ? t('play.theme.count', { n: selected.length }) : ''}</span>
			<button type="button" class="kk-btn kk-btn-primary min-h-11 px-8" onclick={() => (open = false)}>{t('play.theme.done')}</button>
		</div>
	</div>
</Dialog>
