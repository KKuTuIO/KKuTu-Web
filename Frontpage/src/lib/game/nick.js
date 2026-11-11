/**
 * Changing my nickname in the game (the legacy `nickChange` message on the master socket; the game server judges everything and
 * answers with an error-style code: 630 is the success). Mirrors its rules only to enable the button and to say why it is off:
 * 2~15 characters of the allowed set, once every 7 days, and fixing it (no #tag, nobody else can take the name) costs 100 ping, free for members.
 */
export const NICK_MIN = 2;
export const NICK_MAX = 15;
export const FIXED_COST = 100;
export const NICK_PATTERN = /^[가-힣ㄱ-ㅎa-zA-Z0-9][가-힣ㄱ-ㅎa-zA-Z0-9 _-]*[가-힣ㄱ-ㅎa-zA-Z0-9 _-]$/;

/** @returns {null|'length'|'chars'} */
export function nickProblem(value) {
	const v = String(value ?? '').trim();
	if (v.length < NICK_MIN || v.length > NICK_MAX) return 'length';
	return NICK_PATTERN.test(v.replace(' ', '')) ? null : 'chars';
}

/**
 * What the editor shows for the current input.
 * @param {{value:string, base:string, fixed:boolean, meFixed:boolean, member:boolean, money:number, nextChangeAt?:number|null, now?:number}} s
 */
export function nickState({ value, base, fixed, meFixed, member, money, nextChangeAt = null, now = Date.now() }) {
	const v = String(value ?? '').trim();
	const problem = v === base ? null : nickProblem(v); // keeping the name (only fixing it) is fine
	// the same name again changes nothing, unless it is being fixed now (legacy: `name != current || fixed`)
	const unchanged = v === base && (!fixed || meFixed);
	const cost = fixed && !member ? FIXED_COST : 0;
	const poor = cost > money;
	const cooling = !!nextChangeAt && nextChangeAt > now;
	return { value: v, problem, unchanged, cost, poor, cooling, canSubmit: !problem && !unchanged && !poor && !cooling && v.length >= NICK_MIN };
}
