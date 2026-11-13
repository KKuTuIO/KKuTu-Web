/**
 * Player settings kept in this browser, one entry per option of the legacy settings dialog.
 * Every change is saved at once. Options for features that aren't built yet (whispers, friend requests, the game
 * screen) are stored already and take effect when those features arrive.
 */
const KEY = 'kkutu-play-prefs';

/** name -> default. Booleans are checkboxes; the rest list the values they accept. */
export const DEFAULTS = {
	autoReady: false, // 자동 준비
	denyInvite: false, // 요청 거부: 초대
	denyGuestChat: false, // 요청 거부: 손님 채팅
	denyFriend: false, // 요청 거부: 친구 추가
	whisperFrom: 'off', // 귓속말 거부: off | guest | friend | near
	filter: 'blind', // 필터 설정: blind (♥) | off
	vibration: true, // 떨림 효과
	spectatorTag: true, // 관전자 구분
	dmAlerts: true, // 귓속말 알림: a notice and the unread badge for a new whisper (it is received either way)
	hideRoom: false, // 방 제목 숨기기
	hideUsers: false, // 참여자 숨기기
	ignoreSpectators: false, // 관전 채팅 무시
	hideRecords: false, // 전적 숨기기
	userSort: 'entry', // 접속자 정렬: entry | score | name
	itemDock: 'answer', // 아이템전 아이템 막대 위치: answer | top | bottom | left | right
	hand: 'right', // 주로 사용하는 손: right | left (left puts the lobby's side bar on the right)
	chatFloating: false, // 채팅창 띄우기: the chat is a floating sheet (opened by a button) on every screen instead of a panel fixed under the room list
	chatStyle: 'messenger' // 대화 표시 방식: messenger (bubbles) | classic (the legacy client's one line: name, text, time)
};
export const CHOICES = {
	whisperFrom: ['off', 'guest', 'friend', 'near'],
	filter: ['blind', 'off'],
	userSort: ['entry', 'score', 'name'],
	hand: ['right', 'left'],
	itemDock: ['answer', 'top', 'bottom', 'left', 'right'],
	chatStyle: ['messenger', 'classic']
};
export const USER_SORTS = CHOICES.userSort;

function clean(name, value) {
	const d = DEFAULTS[name];
	if (typeof d === 'boolean') return typeof value === 'boolean' ? value : d;
	return CHOICES[name].includes(value) ? value : d;
}

function load() {
	let o = {};
	try {
		o = JSON.parse(localStorage.getItem(KEY) ?? 'null') ?? {};
	} catch {
		/* private mode or damaged data: start from the defaults */
	}
	return Object.fromEntries(Object.keys(DEFAULTS).map((k) => [k, clean(k, o[k])]));
}

export const prefs = $state(load());

export function setPref(name, value) {
	if (!(name in DEFAULTS)) return;
	prefs[name] = clean(name, value);
	try {
		localStorage.setItem(KEY, JSON.stringify({ ...prefs }));
	} catch {
		/* private mode */
	}
}
