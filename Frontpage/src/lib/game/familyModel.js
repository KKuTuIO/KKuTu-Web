/**
 * 패밀리 (a guild): pure rules and helpers. No state here (see familyStore.svelte.js).
 *
 * A member is an ACCOUNT, not a game profile. One account has several profiles (multi-profile), so the family keeps the account as the
 * member, shows the account's 대표 프로필 in lists, and counts attendance / contribution once per account. The numbers below are
 * placeholders until the server owns them.
 */

export const FAMILY_NAME_MAX = 12;
export const FAMILY_NAME_MIN = 2;
export const FAMILY_INTRO_MAX_BYTES = 150; // Korean / symbols count 2, ASCII 1 (same rule as the reference)
export const FAMILY_BASE_CAPACITY = 20;
export const FAMILY_MAX_CAPACITY = 200;
export const FAMILY_CREATE_LEVEL = 20; // my level needed to found one
export const FAMILY_CREATE_COST = 1000; // 핑
export const FAMILY_WEEKLY_CONTRIBUTION_CAP = 35000; // per account per week
export const FAMILY_CHAT_MAX = 200;

/** Roles, strongest first. */
export const ROLES = ['master', 'deputy', 'member'];
export const roleRank = (role) => ROLES.indexOf(role);
export const canManage = (role) => role === 'master' || role === 'deputy'; // answer applications, kick members
export const canKick = (me, target) => canManage(me) && roleRank(me) < roleRank(target);

/** Length in bytes the way the intro limit counts it: 2 for anything that is not ASCII. */
export function introBytes(text) {
	let n = 0;
	for (const ch of String(text ?? '')) n += ch.codePointAt(0) < 0x80 ? 1 : 2;
	return n;
}

/** Cuts a text down to the byte limit without splitting a character. */
export function clampIntro(text, max = FAMILY_INTRO_MAX_BYTES) {
	let n = 0;
	let out = '';
	for (const ch of String(text ?? '')) {
		const w = ch.codePointAt(0) < 0x80 ? 1 : 2;
		if (n + w > max) break;
		n += w;
		out += ch;
	}
	return out;
}

/** Why a name cannot be used, or '' when it can. Returns the locale key suffix (play.family.nameError.*). */
export function nameProblem(name) {
	const v = String(name ?? '').trim();
	if (v.length < FAMILY_NAME_MIN) return 'short';
	if (v.length > FAMILY_NAME_MAX) return 'long';
	if (!/^[0-9A-Za-z가-힣 ]+$/u.test(v) || /\s{2,}/.test(v)) return 'chars';
	return '';
}

/** Family level from accumulated 명성치: each level needs a little more than the one before. */
export const levelNeed = (lv) => 1000 + (lv - 1) * 500;
export function familyLevel(fame) {
	let lv = 1;
	let left = Math.max(0, fame);
	while (left >= levelNeed(lv)) {
		left -= levelNeed(lv);
		lv++;
	}
	return { level: lv, into: left, need: levelNeed(lv) };
}

/** Capacity by level: starts at 20 and grows by 10 per level up to the cap. */
export const capacityOf = (level) => Math.min(FAMILY_MAX_CAPACITY, FAMILY_BASE_CAPACITY + (level - 1) * 10);

/** Where a member is, in the friend list's words: returns {key, vars}. `channelOf` turns a channel id into its name (감자, 냉이 ...). */
export function whereOf(member, server, channelOf = String) {
	if (!member.online) return { key: 'play.friend.offline' };
	if (member.status && member.status !== 'online') return { key: `play.status.${member.status}` };
	if (member.place) return { key: 'play.friend.inGame', vars: { room: member.place } };
	if (Number(member.server) === Number(server)) return { key: 'play.friend.lobby' }; // the server says "0", the page knows 0
	return { key: 'play.friend.channel', vars: { channel: channelOf(member.server) } };
}

/** Members to list: online first, then by role, then by name. */
export function sortMembers(list) {
	return [...list].sort((a, b) => Number(b.online) - Number(a.online) || roleRank(a.role) - roleRank(b.role) || a.name.localeCompare(b.name, 'ko'));
}
