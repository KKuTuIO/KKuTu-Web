/**
 * 운영진 표시: staff always wear a staff badge (`b1_gm`, `b1_dev`, ... in the shop group BDG1), and the legacy client printed `[개발팀]` and the like
 * in front of their name for it. Here the worn badge picks a real tag (`lobby/StaffTag.svelte`) whose words come from the locale
 * (`play.staff.<id>`), so it is read by screen readers, follows the theme, and is left out when the players are hidden.
 * `color` is the legacy colour; the tag derives its tint and border from it.
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

/** The tag of a staff team by its id (`gm`, `dev`, ...), or null. */
export const staffOf = (id) => (typeof id === 'string' && Object.hasOwn(STAFF, id) ? { id, ...STAFF[id] } : null);

/**
 * The staff team of a worn staff badge (`b1_<team>`), or null. A badge is worn in `equip.BDG` by the new client; the legacy client wrote it
 * under its own group (`equip.BDG1`), so both are read. Badges of teams without a tag (`b1_seek`) give null.
 */
export function staffIdOf(equip) {
	for (const key of ['BDG', 'BDG1']) {
		const item = equip?.[key];
		const id = typeof item === 'string' && item.startsWith('b1_') ? item.slice(3) : null;
		if (id && Object.hasOwn(STAFF, id)) return id;
	}
	return null;
}

/** Every staff team wears the same level badge (`kkutu/lv/gmlv.png`), whatever the team. */
export const STAFF_LEVEL_BADGE = 'gm';
