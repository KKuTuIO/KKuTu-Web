// KakaoTalk sharing, as the legacy client did it: the Kakao JS SDK (loaded on first use) sends the owner's message template.
const SDK = 'https://t1.kakaocdn.net/kakao_js_sdk/2.7.4/kakao.min.js';
const SDK_INTEGRITY = 'sha384-DKYJZ8NLiK8MN4/C5P2dtSmLQ4KwPaoqAfyA/DfmEc1VDxu4yyC7wy6K1Hs90nka';
const JS_KEY = '424a33dd14697c7a6fe8fce22fa4fb14'; // the public JavaScript key, the same one the legacy client ships
export const ROOM_TEMPLATE_ID = 118853;

let loading = null;
function loadSdk() {
	if (globalThis.Kakao) return Promise.resolve(globalThis.Kakao);
	loading ??= new Promise((resolve, reject) => {
		const s = document.createElement('script');
		s.src = SDK;
		s.integrity = SDK_INTEGRITY;
		s.crossOrigin = 'anonymous';
		s.onload = () => resolve(globalThis.Kakao);
		s.onerror = () => {
			loading = null;
			s.remove();
			reject(new Error('kakao sdk'));
		};
		document.head.append(s);
	});
	return loading;
}

/** Sends the room invitation through KakaoTalk. `args`: USERNAME, SERVERID, ROOMNUMBER, GAMETYPE. */
export async function shareRoomOnKakao(args) {
	const Kakao = await loadSdk();
	if (!Kakao.isInitialized()) Kakao.init(JS_KEY);
	Kakao.Share.sendCustom({ templateId: ROOM_TEMPLATE_ID, templateArgs: args });
}
