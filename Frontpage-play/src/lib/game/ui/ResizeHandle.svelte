<script>
	/**
	 * A splitter between two areas: drag it (mouse, touch, pen), or focus it and use the arrow keys. Home/End jump to the
	 * limits and a double click goes back to the default. It only reports sizes; the page decides what is resized.
	 *
	 * - `orientation`: "vertical" = a vertical line you drag left/right (changes a width); "horizontal" = changes a height.
	 * - `grow`: +1 if moving the pointer right/down makes the area bigger, -1 if it makes it smaller.
	 * - `max` may be a function: the real limit depends on the window.
	 */
	let { value, min, max, def, orientation = 'vertical', grow = 1, label, onchange, class: cls = '' } = $props();
	const vertical = $derived(orientation === 'vertical');
	const limit = () => Math.max(min, typeof max === 'function' ? max() : max);
	const clampTo = (v) => Math.min(limit(), Math.max(min, Math.round(v)));
	let dragging = $state(false);
	let origin = 0;
	let startValue = 0;

	function down(e) {
		if (e.button !== undefined && e.button !== 0) return;
		e.currentTarget.setPointerCapture(e.pointerId);
		dragging = true;
		origin = vertical ? e.clientX : e.clientY;
		startValue = value;
		e.preventDefault();
	}
	function move(e) {
		if (!dragging) return;
		const delta = ((vertical ? e.clientX : e.clientY) - origin) * grow;
		onchange?.(clampTo(startValue + delta));
	}
	const up = () => (dragging = false);
	function key(e) {
		// Arrow keys follow the screen: on a vertical line right/left, on a horizontal one down/up.
		const step = e.shiftKey ? 48 : 16;
		const dir = { ArrowRight: 1, ArrowDown: 1, ArrowLeft: -1, ArrowUp: -1 }[e.key];
		const axisOk = vertical ? e.key === 'ArrowLeft' || e.key === 'ArrowRight' : e.key === 'ArrowUp' || e.key === 'ArrowDown';
		if (dir && axisOk) onchange?.(clampTo(value + dir * grow * step));
		else if (e.key === 'Home') onchange?.(min);
		else if (e.key === 'End') onchange?.(limit());
		else return;
		e.preventDefault();
	}
</script>

<!-- A separator that can be focused is an interactive widget (WAI-ARIA window splitter). -->
<!-- svelte-ignore a11y_no_noninteractive_tabindex, a11y_no_noninteractive_element_interactions -->
<div
	role="separator"
	tabindex="0"
	aria-orientation={vertical ? 'vertical' : 'horizontal'}
	aria-label={label}
	aria-valuenow={value}
	aria-valuemin={min}
	aria-valuemax={limit()}
	onpointerdown={down}
	onpointermove={move}
	onpointerup={up}
	onpointercancel={up}
	onlostpointercapture={up}
	onkeydown={key}
	ondblclick={() => onchange?.(clampTo(def))}
	class="group absolute z-20 touch-none select-none {vertical ? 'cursor-col-resize' : 'cursor-row-resize'} {cls}"
>
	<!-- the visible grip: hidden until the handle is hovered, focused or dragged -->
	<span
		aria-hidden="true"
		class="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 rounded-full transition-opacity duration-150 group-hover:opacity-100 group-focus-visible:opacity-100 {vertical ? 'h-12 w-1' : 'h-1 w-12'} {dragging ? 'bg-(--kk-accent) opacity-100' : 'bg-(--kk-line-strong) opacity-0 group-focus-visible:bg-(--kk-accent) group-hover:bg-(--kk-accent)'}"
	></span>
</div>
