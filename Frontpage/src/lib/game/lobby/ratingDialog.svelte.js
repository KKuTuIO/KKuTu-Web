/** What the 평점 window shows: whose tier (0 very low … 4 very high) and whether it is mine (then it also says how to improve it). */
export const ratingView = $state({ open: false, rating: 2, mine: false });

export function openRating(rating, mine = false) {
	ratingView.rating = Number.isInteger(rating) ? rating : 2;
	ratingView.mine = !!mine;
	ratingView.open = true;
}

/** Each tier's colour (the mark's own), used to tint the current tier's row. */
export const RATING_COLORS = ['#ef4444', '#f59e0b', '#22c55e', '#3b82f6', '#7c6cf2'];
