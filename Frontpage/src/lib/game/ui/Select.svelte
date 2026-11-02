<script>
	import Icon from '../lobby/Icon.svelte';
	import { cubicOut } from 'svelte/easing';
	/**
	 * Custom dropdown (WAI-ARIA select-only combobox + listbox).
	 * The list lives in the browser's top layer (popover), so neither a scrolling nor an animated (transformed)
	 * dialog can clip or offset it, and it flips upward when there is no room below.
	 * Options may be `disabled` (shown, announced as unavailable, skipped by the keyboard) and carry a `badge`.
	 * @type {{options:{value:any,label:string,disabled?:boolean,badge?:string}[], value:any, label?:string, onchange?:(v:any)=>void}}
	 */
	let { options = [], value = $bindable(), label = '', onchange } = $props();
	let open = $state(false);
	let active = $state(0);
	let trigger = $state();
	let list = $state();
	let pos = $state({ left: 0, width: 0, top: 0, maxH: 240, up: false });
	// Drops from the trigger (or rises from it when flipped); leaving is quicker than arriving.
	function drop(node, { up = false, duration = 150 } = {}) {
		return {
			duration,
			easing: cubicOut,
			css: (t, u) => `opacity:${t};transform:translateY(${(up ? 6 : -6) * u}px) scale(${0.97 + 0.03 * t});transform-origin:${up ? 'bottom' : 'top'}`
		};
	}

	const uid = `kk-sel-${Math.random().toString(36).slice(2, 8)}`;
	let typed = '';
	let typedTimer;

	const selected = $derived(options.find((o) => o.value === value));
	const selectedIndex = $derived(Math.max(0, options.findIndex((o) => o.value === value)));

	function place() {
		const r = trigger.getBoundingClientRect();
		const below = window.innerHeight - r.bottom - 12;
		const above = r.top - 12;
		const up = below < 180 && above > below;
		const maxH = Math.min(280, up ? above : below);
		pos = { left: r.left, width: r.width, top: up ? r.top - 6 : r.bottom + 6, maxH, up };
	}
	function show() {
		if (open) return;
		active = selectedIndex;
		place();
		open = true;
		queueMicrotask(() => reveal(active));
	}
	function hide(refocus = true) {
		open = false;
		if (refocus) trigger?.focus();
	}
	function choose(i) {
		const o = options[i];
		if (!o || o.disabled) return;
		if (o.value !== value) {
			value = o.value;
			onchange?.(o.value);
		}
		hide();
	}
	// Scroll only the list itself. (scrollIntoView would also scroll the surrounding dialog,
	// which our own "close when the page moves" rule would then mistake for the user scrolling away.)
	function reveal(i) {
		const el = list?.children[i];
		if (!el) return;
		if (el.offsetTop < list.scrollTop) list.scrollTop = el.offsetTop - 4;
		else if (el.offsetTop + el.offsetHeight > list.scrollTop + list.clientHeight) list.scrollTop = el.offsetTop + el.offsetHeight - list.clientHeight + 4;
	}
	// Step over disabled options in the direction of travel.
	function move(i, dir = 1) {
		const n = options.length;
		let j = ((i % n) + n) % n;
		for (let tries = 0; tries < n && options[j]?.disabled; tries++) j = (j + dir + n) % n;
		if (options[j]?.disabled) return;
		active = j;
		reveal(active);
	}
	function key(e) {
		if (!open) {
			if (['ArrowDown', 'ArrowUp', 'Enter', ' '].includes(e.key)) {
				e.preventDefault();
				show();
			}
			return;
		}
		const k = e.key;
		if (k === 'ArrowDown') move(active + 1, 1);
		else if (k === 'ArrowUp') move(active - 1, -1);
		else if (k === 'Home') move(0, 1);
		else if (k === 'End') move(options.length - 1, -1);
		else if (k === 'Enter' || k === ' ') choose(active);
		else if (k === 'Escape') {
			e.stopPropagation(); // don't let a surrounding dialog close too
			hide();
		} else if (k === 'Tab') hide(false);
		else if (k.length === 1) {
			clearTimeout(typedTimer);
			typed += k.toLowerCase();
			typedTimer = setTimeout(() => (typed = ''), 600);
			const i = options.findIndex((o) => !o.disabled && o.label.toLowerCase().startsWith(typed));
			if (i >= 0) move(i);
			return;
		} else return;
		e.preventDefault();
	}
	// Promote the list to the top layer once it exists.
	$effect(() => {
		if (open && list) {
			try {
				if (!list.matches(':popover-open')) list.showPopover?.();
			} catch {
				/* no popover support: the fixed-position fallback below still works */
			}
		}
	});
	// Close on outside press or when the page moves under the list.
	$effect(() => {
		if (!open) return;
		const away = (e) => !trigger?.contains(e.target) && !list?.contains(e.target) && hide(false);
		const reflow = (e) => !list?.contains(e.target) && hide(false);
		document.addEventListener('pointerdown', away, true);
		window.addEventListener('resize', reflow);
		window.addEventListener('scroll', reflow, true);
		return () => {
			document.removeEventListener('pointerdown', away, true);
			window.removeEventListener('resize', reflow);
			window.removeEventListener('scroll', reflow, true);
		};
	});
</script>

<button
	bind:this={trigger}
	type="button"
	role="combobox"
	aria-label={label}
	aria-haspopup="listbox"
	aria-expanded={open}
	aria-controls="{uid}-list"
	aria-activedescendant={open ? `${uid}-${active}` : undefined}
	onclick={() => (open ? hide() : show())}
	onkeydown={key}
	class="kk-input flex h-11 w-full items-center justify-between gap-2 rounded-xl px-3 text-left"
>
	<span class="min-w-0 truncate">{selected?.label ?? ''}</span>
	<Icon name="expand_more" class="shrink-0 text-(color:--kk-muted) transition-transform {open ? 'rotate-180' : ''}" />
</button>

{#if open}
	<ul
		bind:this={list}
		id="{uid}-list"
		role="listbox"
		aria-label={label}
		popover="manual"
		class="kk-card m-0 overflow-y-auto rounded-xl p-1 text-(color:--kk-text)"
		in:drop={{ up: pos.up }}
		out:drop={{ up: pos.up, duration: 90 }}
		style="inset:auto;left:{pos.left}px;width:{pos.width}px;{pos.up ? `bottom:${window.innerHeight - pos.top}px` : `top:${pos.top}px`};max-height:{pos.maxH}px"
	>
		{#each options as o, i (o.value)}
			<!-- Keyboard is handled by the combobox trigger (aria-activedescendant), per the WAI-ARIA select-only pattern. -->
			<!-- svelte-ignore a11y_click_events_have_key_events -->
			<li
				id="{uid}-{i}"
				role="option"
				aria-selected={o.value === value}
				aria-disabled={o.disabled ? true : undefined}
				onpointerenter={() => !o.disabled && (active = i)}
				onpointerdown={(e) => e.preventDefault()}
				onclick={() => choose(i)}
				class="flex min-h-10 items-center justify-between gap-2 rounded-lg px-3 {o.disabled ? 'cursor-not-allowed opacity-55' : 'cursor-pointer'} {i === active && !o.disabled ? 'bg-(--kk-accent-soft)' : ''} {o.value === value ? 'font-extrabold text-(color:--kk-accent-strong)' : ''}"
			>
				<span class="flex min-w-0 items-center gap-2"><span class="truncate">{o.label}</span>{#if o.badge}<span class="kk-chip shrink-0">{o.badge}</span>{/if}</span>
				{#if o.value === value}<Icon name="check" size={18} />{/if}
			</li>
		{/each}
	</ul>
{/if}
