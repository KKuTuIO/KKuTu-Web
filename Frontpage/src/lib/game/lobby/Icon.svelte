<script>
	/** Material Symbols Outlined glyph (font loaded in app.html, same as the homepage). */
	let { name, size = 20, fill = false, class: cls = '' } = $props();
</script>

<span class="material-symbols-outlined leading-none select-none {cls}" aria-hidden="true" style="font-size:{size}px;font-variation-settings:'FILL' {fill ? 1 : 0}">{name}</span>
