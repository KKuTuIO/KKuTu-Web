/**
 * The legacy client's chat flood guard, unchanged. Each line costs points (more for repeats); lines arriving slowly
 * let the score drain. At 100 points chatting is muted for 5 seconds, and trying anyway while muted extends it.
 * Commands ("/…") are exempt.
 */
export function createSpamGuard() {
	let score = 0;
	let lastTime = null;
	let muteUntil = 0;
	let violations = 0;
	let recents = [];

	/**
	 * @returns {{allow: boolean, notice?: string}} `notice` is a key of the message to show (with `seconds` when it has a countdown)
	 */
	return function check(value, now = Date.now()) {
		lastTime ??= now;
		const elapsed = now - lastTime;
		lastTime = now;

		if (muteUntil > now) {
			violations++;
			if (violations >= 3) {
				muteUntil += 3000;
				violations = 0;
				return { allow: false, notice: 'play.chat.spam.extended', seconds: Math.ceil((muteUntil - now) / 1000) };
			}
			if (violations === 1) return { allow: false, notice: 'play.chat.spam.muted', seconds: Math.ceil((muteUntil - now) / 1000) };
			return { allow: false };
		}
		score = Math.max(0, score - (elapsed / 1000) * 15);

		let normalized = value.replace(/[\s0-9!@#$%^&*()_+={}[\]:;"'<>,.?/\\|`~-]/g, '');
		if (normalized === '') normalized = value.trim();

		let penalty = 20;
		if (recents.length > 0) {
			if (recents[0] === normalized) penalty += 20;
			else if (recents.includes(normalized)) penalty += 10;
		}
		score += penalty;

		if (score >= 100) {
			muteUntil = now + 5000;
			violations = 0;
			score = 50;
			return { allow: false, notice: 'play.chat.spam.detected' };
		}
		recents.unshift(normalized);
		if (recents.length > 4) recents.pop();
		return { allow: true };
	};
}
