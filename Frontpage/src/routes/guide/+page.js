import { modes } from './modes.js';

// 레이아웃이 <meta name="description"> 에 쓴다 (+layout.svelte).
export const load = () => ({
	description: `설치 없이 브라우저에서 즐기는 온라인 단어 게임 끄투리오의 시작 방법과, 끝말잇기·쿵쿵따·앞말잇기·자음퀴즈·십자말풀이·타자 대결 등 ${modes.length}가지 게임 유형의 규칙, 방 옵션과 핑·평점 같은 게임 용어를 소개합니다.`
});
