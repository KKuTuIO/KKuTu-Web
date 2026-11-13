/**
 * 운영진 표시: the legacy client printed `[개발팀]` and the like in front of a name through `::before` rules on the NIK items
 * (`x-gm`, `x-mod`, ...). Here the item id picks a real badge (`lobby/StaffTag.svelte`) whose words come from the locale (`play.staff.<id>`),
 * so it is read by screen readers, follows the theme, and is left out when the players are hidden.
 * `color` is the legacy colour; the badge derives its tint and border from it.
 */
export const STAFF = {
	gm: { color: '#008AED' },
	mod: { color: '#039BE5' },
	word: { color: '#3949AB' },
	minwon: { color: '#43A047' },
	dev: { color: '#FB8C00' },
	media: { color: '#8E24AA' },
	plan: { color: '#C43162' }
};

/** The staff team a worn name skin (`equip.NIK`) stands for, or null. */
export const staffOf = (nik) => (typeof nik === 'string' && Object.hasOwn(STAFF, nik) ? { id: nik, ...STAFF[nik] } : null);
