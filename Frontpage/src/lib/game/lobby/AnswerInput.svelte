<svelte:options css="injected" />

<script>
	/**
	 * The text field an answer is typed into, living inside a closed shadow root (see ../shadowField.js). Everything the page's own styles
	 * said about `#game-input` / `#cw-q-input` (gameStage.css) is repeated here, because page styles do not reach into a shadow root;
	 * only inherited things (font, colour variables such as --kk-line) come through.
	 *
	 * The props are accessors (the host binds `value` through getters/setters, see shadowField.js).
	 */
	let { value = $bindable(''), variant = 'answer', placeholder = '', label = '', onkeydown, oninput, oncompositionend, onblur, attach } = $props();

	/** Hands the field to the host (focus, guard, trace) and returns what the guard needs to be told when the field goes. */
	function ready(node) {
		return attach?.(node);
	}
</script>

<input
	use:ready
	bind:value
	class={variant}
	{placeholder}
	aria-label={label}
	{onkeydown}
	{oninput}
	{oncompositionend}
	{onblur}
	autocomplete="off"
	autocorrect="off"
	autocapitalize="off"
	spellcheck="false"
/>

<style>
	input {
		display: block;
		box-sizing: content-box; /* the stage is content-box (legacy) */
		font-family: inherit;
		outline: none;
		color: var(--kk-text);
	}
	input.answer {
		width: 478px;
		height: 40px;
		padding: 5px;
		border: 1px solid var(--kk-line);
		border-radius: 10px;
		font-size: 20px;
		background-color: var(--kk-surface-2);
	}
	/* a phone: as wide as the stage, tall enough to hit, and 18px so the browser does not zoom the page when it is focused */
	input.answerPhone {
		box-sizing: border-box;
		width: 100%;
		height: 52px;
		padding: 0 14px;
		border: 1.5px solid var(--kk-line-strong);
		border-radius: 14px;
		font-size: 18px;
		background-color: var(--kk-surface);
	}
	input.crossword {
		box-sizing: border-box;
		width: 100%;
		height: 30px;
		padding: 5px;
		border: 1px solid var(--kk-line-strong);
		border-radius: 10px;
		font-size: 15px;
		background: var(--kk-surface);
	}
	input:focus {
		border-color: var(--kk-accent);
		box-shadow: 0 0 0 3px color-mix(in srgb, var(--kk-accent) 28%, transparent);
	}
</style>
