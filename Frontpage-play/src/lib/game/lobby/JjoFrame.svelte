<script>
	import '../gameStage.css';
	import { IMAGE_CDN, IMAGE_VERSION } from '../images.js';

	/**
	 * JJo's container: the frog the board games (솎솎, 잇땅) have around their board, with his eyes and nose, the yellow bar above and the blue one
	 * below. What goes in is the board, in the 298 × 298 px of his mouth. It is drawn at its own 322 px, as the stage draws it (gameStage.css, the rules
	 * are under `.kkg`), and scaled to the width it has. `top` and `bottom` are the bars: `{pct, text, extreme?}` (a fill of `pct` per cent and the text
	 * over it; `extreme` turns the bottom one red), or null to leave one out. `reserve` is the height (px) the screen needs for everything else it shows
	 * (the header, the keys): the frog is scaled down so that he and the rest fit the window, never below 0.6.
	 * @type {{children:import('svelte').Snippet, top?:{pct:number, text?:string}|null, bottom?:{pct:number, text?:string, extreme?:boolean}|null, label?:string, reserve?:number}}
	 */
	let { children, top = null, bottom = null, label = '', reserve = 0 } = $props();

	const NATIVE_W = 322;
	let w = $state(NATIVE_W);
	let h = $state(380);
	// The frog's height: the floats inside him do not always count for his own box, so what his contents reach is read (as it changes), as the stage does.
	function measure(node) {
		const run = () => {
			const v = Math.max(node.offsetHeight, node.scrollHeight);
			if (v && v !== h) h = v;
		};
		run();
		const timer = setInterval(run, 200);
		const ro = new ResizeObserver(run);
		ro.observe(node);
		return { destroy: () => (clearInterval(timer), ro.disconnect()) };
	}
	let winH = $state(typeof window === 'undefined' ? 800 : window.innerHeight);
	const k = $derived(Math.max(0.6, Math.min(1.4, w / NATIVE_W, reserve ? (winH - reserve) / h : 9)));
</script>

<svelte:window bind:innerHeight={winH} />

<!-- the holder takes the scaled size; the frog inside keeps its own and is scaled from its corner -->
<div class="holder" bind:clientWidth={w} style:height="{Math.ceil(h * k)}px" role="group" aria-label={label || undefined}>
	<div class="kkg host" use:measure style:transform="scale({k})" style:left="{Math.max(0, (w - NATIVE_W * k) / 2)}px">
		<div class="jjoriping cw">
			<img class="jjoObj jjoEyeL" src="{IMAGE_CDN}jjoeyeL.svg{IMAGE_VERSION}" alt="" /><img class="jjoObj jjoNose" src="{IMAGE_CDN}jjonose.svg{IMAGE_VERSION}" alt="" /><img class="jjoObj jjoEyeR" src="{IMAGE_CDN}jjoeyeR.svg{IMAGE_VERSION}" alt="" />
			<div class="jjoDisplayBar">
				{#if top}
					<div class="graph jjo-turn-time bar"><div class="graph-bar" style:width="{Math.max(0, Math.min(100, top.pct))}%"></div>{#if top.text}<span class="bar-text">{top.text}</span>{/if}</div>
				{/if}
				<div class="jjo-display">
					<div class="content">{@render children()}</div>
				</div>
				{#if bottom}
					<div class="graph jjo-round-time bar"><div class="graph-bar {bottom.extreme ? 'round-extreme' : ''}" style:width="{Math.max(0, Math.min(100, bottom.pct))}%"></div>{#if bottom.text}<span class="bar-text">{bottom.text}</span>{/if}</div>
				{/if}
			</div>
		</div>
	</div>
</div>

<style>
	.holder {
		position: relative;
		flex: none; /* in a column of a screen it must keep the height the scale gives it, not shrink to what is left */
		width: 100%;
		max-width: 28rem;
		margin: 0 auto;
	}
	/* `.kkg` is the whole stage (a 1000 × 425 px box that floats every div and sizes by content); here it is only the namespace of the frog's rules */
	.host.kkg {
		position: absolute;
		top: 0;
		left: 0;
		width: 322px;
		height: auto;
		padding: 0;
		transform-origin: 0 0;
		background: none;
		border-radius: 0;
		font-size: 1rem;
		font-weight: 400;
		text-shadow: none;
	}
	/* the frog keeps no fixed box here (the legacy one grows and shrinks with his mouth); what he holds is clear of the floats */
	.host :global(.jjoriping) {
		display: flow-root;
		height: auto;
	}
	/* the board is not a float of the stage: the rules for the stage (float, content-box, light text with a shadow) stop at its edge */
	.content {
		position: relative;
		float: none;
		width: 298px;
		height: 298px;
		overflow: hidden;
	}
	.content,
	.content :global(*) {
		box-sizing: border-box;
	}
	.content :global(div) {
		float: none;
	}
	.content :global(*) {
		text-shadow: none;
	}
	.bar {
		position: relative;
	}
	/* the text stays on the bar whatever its fill is (the legacy bar writes it inside the fill, which is nothing when the fill is empty) */
	.bar-text {
		position: absolute;
		inset: 0;
		text-align: center;
		font-size: 11px;
		font-weight: 800;
		line-height: 20px;
		color: #fff;
		text-shadow: 0 1px 3px #141414;
		pointer-events: none;
	}
	.bar :global(.graph-bar) {
		transition: width 0.25s linear;
	}
	@media (prefers-reduced-motion: reduce) {
		.bar :global(.graph-bar) {
			transition: none;
		}
	}
</style>
