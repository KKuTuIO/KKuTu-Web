<script>
	import { t } from '../i18n.svelte.js';
	import { evaluate, format } from '../calc.js';
	import FloatWindow from './FloatWindow.svelte';
	import Icon from './Icon.svelte';

	/** The calculator of 수학 대결: a floating window that stays beside the game, like the dictionary. */
	let { open = $bindable(false) } = $props();
	let expr = $state('');
	let result = $state(null); // the last result shown after "="
	let failed = $state(false);

	$effect(() => {
		if (open) {
			expr = '';
			result = null;
			failed = false;
		}
	});

	const keys = [
		['C', 'clear'],
		['(', 'put'],
		[')', 'put'],
		['÷', 'put'],
		['7', 'put'],
		['8', 'put'],
		['9', 'put'],
		['×', 'put'],
		['4', 'put'],
		['5', 'put'],
		['6', 'put'],
		['−', 'put'],
		['1', 'put'],
		['2', 'put'],
		['3', 'put'],
		['+', 'put'],
		['0', 'put'],
		['.', 'put'],
		['back', 'back'],
		['=', 'equals']
	];
	const isOp = (k) => '÷×−+'.includes(k);

	function press(key, kind) {
		failed = false;
		if (kind === 'clear') {
			expr = '';
			result = null;
		} else if (kind === 'back') {
			expr = expr.slice(0, -1);
			result = null;
		} else if (kind === 'equals') {
			const v = evaluate(expr);
			if (v === null) failed = true;
			else {
				result = format(v);
				expr = result;
			}
		} else {
			// a digit typed after a result starts over; an operator carries the result on
			if (result !== null && !isOp(key)) expr = '';
			result = null;
			expr += key;
		}
	}

	/** The keyboard works too, while the window has focus. */
	function keydown(e) {
		const map = { '*': '×', '/': '÷', '-': '−' };
		if (/^[0-9().+]$/.test(e.key) || map[e.key]) press(map[e.key] ?? e.key, 'put');
		else if (e.key === 'Enter' || e.key === '=') press('=', 'equals');
		else if (e.key === 'Backspace') press('', 'back');
		else if (e.key === 'Escape' || e.key === 'Delete') return;
		else return;
		e.preventDefault();
	}
	const live = $derived(expr && result === null ? evaluate(expr) : null);
</script>

<FloatWindow bind:open id="calculator" title={t('play.calc.title')} width="19rem">
	<!-- svelte-ignore a11y_no_noninteractive_tabindex, a11y_no_noninteractive_element_interactions -->
	<div class="flex flex-col gap-3" role="group" aria-label={t('play.calc.title')} tabindex="0" onkeydown={keydown}>
		<div class="rounded-xl bg-(--kk-surface-2) px-3 py-2 text-right shadow-[inset_0_0_0_1.5px_var(--kk-line)]">
			<p class="min-h-6 text-sm font-bold text-(color:--kk-muted) tabular-nums" aria-live="polite">{failed ? t('play.calc.error') : live !== null && expr ? `= ${format(live)}` : ''}</p>
			<p class="min-h-9 text-3xl font-black break-all tabular-nums" data-calc-display>{expr || '0'}</p>
		</div>
		<div class="grid grid-cols-4 gap-2">
			{#each keys as [key, kind] (key + kind)}
				<button
					type="button"
					onclick={() => press(key, kind)}
					aria-label={kind === 'back' ? t('play.calc.back') : kind === 'clear' ? t('play.calc.clear') : kind === 'equals' ? t('play.calc.equals') : key}
					class="kk-btn grid h-12 min-h-0 place-items-center px-0 text-lg {kind === 'equals' ? 'kk-btn-primary' : isOp(key) || kind === 'clear' ? 'bg-(--kk-accent-soft)' : ''}"
				>
					{#if kind === 'back'}<Icon name="backspace" size={22} />{:else}{key}{/if}
				</button>
			{/each}
		</div>
	</div>
</FloatWindow>
