<script>
	import { t } from '../i18n.svelte.js';
	import Tooltip from '../ui/Tooltip.svelte';
	import RatingIcon from './RatingIcon.svelte';
	import { openRating } from './ratingDialog.svelte.js';

	/**
	 * A 평점 mark that opens the tiers window when pressed. It is a `span role=button` (not a <button>) because it sits inside rows that
	 * are buttons themselves; the press does not reach the row.
	 */
	let { rating, size = 15, mine = false } = $props();
	function open(e) {
		e.stopPropagation();
		e.preventDefault();
		openRating(rating, mine);
	}
</script>

<Tooltip text={t(`play.rating.${rating}`)}>
	<span role="button" tabindex="0" aria-label={t(`play.rating.label.${rating}`)} onclick={open} onkeydown={(e) => (e.key === 'Enter' || e.key === ' ') && open(e)} class="inline-flex shrink-0 cursor-pointer rounded-full outline-offset-2 hover:scale-110 focus-visible:outline-2 focus-visible:outline-(--kk-accent)"><RatingIcon {rating} {size} /></span>
</Tooltip>
