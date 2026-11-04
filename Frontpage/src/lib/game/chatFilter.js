/**
 * Which chat lines I don't get to see, the legacy rules in one place (chat, balloons and the chat tick all ask here):
 * blocked players, 손님 채팅 거부, 관전 채팅 무시 and, for whispers, the muted players and 귓속말 거부.
 * @param {{system?:boolean, mine?:boolean, uid?:string, spectator?:boolean, whisper?:boolean}} m a chat line
 * @param {{friendsData:Record<string, {bf:boolean}>}} net
 * @param {{ignoreSpectatorRule?:boolean}} [o] the chat tick (legacy) still plays for spectators I ignore
 */
import { prefs } from './prefs.svelte.js';
import { isMuted, isShut } from './blocks.svelte.js';

export function isHidden(m, net, { ignoreSpectatorRule = false } = {}) {
	if (!m || m.system || m.mine) return false;
	const uid = m.uid ?? '';
	if (isShut(uid)) return true;
	if (prefs.denyGuestChat && uid.startsWith('guest')) return true;
	if (!ignoreSpectatorRule && prefs.ignoreSpectators && m.spectator) return true;
	if (m.whisper) {
		if (isMuted(uid)) return true;
		if (prefs.whisperFrom === 'near' && !net.friendsData[uid]?.bf) return true;
		if (prefs.whisperFrom === 'friend' && !(uid in net.friendsData)) return true;
		if (prefs.whisperFrom === 'guest' && uid.startsWith('guest')) return true;
	}
	return false;
}
