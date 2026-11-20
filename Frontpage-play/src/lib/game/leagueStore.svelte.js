/**
 * 트로피 리그 state, over the game server's `league` packet (see KKuTu-Game-Private/server/game/league/state.ts).
 * The server decides everything: this asks for the state and keeps the last answer. `state` is null until the first answer comes.
 *
 *   state { now, season: {no, preseason, name, crop, number, period: {from, to}}, phase, dayKind, rewardMultiplier, percentileUnlocked,
 *           mine: {trophies, tier, tierId, games, wins} }
 */
import { t } from './i18n.svelte.js';
import { toast } from './ui/toast.svelte.js';
import { soundMatched } from './leagueSound.js';

class LeagueStore {
	/** @type {object|null} */
	state = $state(null);
	/** 매칭 큐: { state: 'idle' | 'queued' | 'off' | 'closed', since }. 서버가 보내 주는 대로만 바꾼다. */
	queue = $state({ state: 'idle', since: 0, localAt: 0 });
	/** 매칭이 성사됐을 때 서버가 보낸 판 정보(규칙과 자리). 아직 방 생성은 이어 붙지 않았다. */
	matched = $state(null);
	/** 규칙 릴 연출이 화면에 떠 있는지(성사되면 켜지고, 연출이 끝나면 RuleReel이 끈다) */
	reelOpen = $state(false);
	/** 리그 로비 화면이 열려 있는지(위 가운데의 내 등급을 가리는 말풍선들이 이걸 보고 접힌다) */
	lobbyOpen = $state(false);
	/** 매칭이 성사된 뒤 판이 끝날 때까지(취소할 수 없고, 매칭 버튼도 다시 누를 수 없는 구간) */
	found = $state(false);
	/** 방금 끝난 판의 결과 { placement, delta, before, trophies, tierBefore, tier, size }. 결과 띠가 보여 주고 지운다. */
	result = $state(null);
	/** 방금 끝난 판에서 신고할 수 있는 상대: [{placement, level, name(판 안에서 쓴 임시 닉네임), token}] */
	rivals = $state([]);
	_foundTimer = 0;
	_send = null;

	attach(send) {
		this._send = send;
	}

	detach() {
		this._send = null;
		this.state = null;
		this.queue = { state: 'idle', since: 0, localAt: 0 };
		this.matched = null;
		this.reelOpen = false;
		this.found = false;
		this.result = null;
		this.rivals = [];
		clearTimeout(this._foundTimer);
	}

	/** 판에서 돌아왔을 때(또는 판이 못 만들어졌을 때): 다시 매칭할 수 있게 한다. */
	endMatch() {
		this.found = false;
		clearTimeout(this._foundTimer);
	}

	/** 대기 중인지(취소 경고, 나가기 확인, 창 닫기 경고가 이걸 본다). */
	get queued() {
		return this.queue.state === 'queued';
	}

	/** 매칭 큐에 들어간다. `modes`는 켜 둔 모드 코드(비면 전부). */
	enqueue(modes = []) {
		this.matched = null;
		this.result = null;
		this.rivals = [];
		this.endMatch();
		this._send?.('league', { op: 'queue', modes });
	}

	/** 큐에서 나간다. 평점이 깎이는 것은 서버가 정하고, 호출하기 전에 사용자에게 먼저 확인해야 한다. */
	cancel() {
		this._send?.('league', { op: 'cancel' });
	}

	/** Asks for the state (the answer replaces `state`). */
	fetch() {
		this._send?.('league', { op: 'get' });
	}

	/** Takes a packet from the master server. */
	receive(pkt) {
		if (pkt?.type === 'league' && pkt.state && typeof pkt.state === 'object') this.state = pkt.state;
		else if (pkt?.type === 'leagueQueue') {
			// 대기 시간 표시는 이 기기의 시계로 센다(서버와 시계가 달라도 0초부터 맞게 보이도록). 이미 대기 중이면 시작 시각을 유지한다.
			const localAt = pkt.state === 'queued' ? (this.queue.state === 'queued' ? this.queue.localAt : Date.now()) : 0;
			this.queue = { state: pkt.state, since: Number(pkt.since) || 0, localAt };
			if (pkt.state === 'off') toast(t('play.league.queue.off'), { kind: 'info' });
			else if (pkt.state === 'closed') toast(t('play.league.queue.closed'), { kind: 'info' });
			else if (pkt.state === 'failed') (this.endMatch(), toast(t('play.league.queue.failed'), { kind: 'error' }));
			else if (pkt.state === 'cooldown') toast(t('play.league.queue.cooldown', { m: Math.max(1, Math.ceil((Number(pkt.until) - Date.now()) / 60000)) }), { kind: 'info' });
			else if (pkt.state === 'idle' && pkt.penalty) toast(t('play.league.queue.penalty'), { kind: 'info' }); // 몇 점인지는 알리지 않는다(평점은 내부 관리 사항)
		} else if (pkt?.type === 'leagueMatched' && Array.isArray(pkt.seats)) {
			this.queue = { state: 'idle', since: 0, localAt: 0 };
			this.matched = { matchId: pkt.matchId, rules: pkt.rules, seats: pkt.seats, me: pkt.me };
			this.found = true;
			clearTimeout(this._foundTimer);
			this._foundTimer = setTimeout(() => (this.found = false), 120_000); // 방이 끝내 안 만들어졌을 때의 안전장치
			this.reelOpen = true;
			soundMatched(); // 매칭 효과음과 mission 효과음이 동시에
		} else if (pkt?.type === 'leagueNormal') {
			// 일반 경기가 끝나고 트로피가 조금 움직였다(−3 ~ +4). 값은 서버가 정했고 여기서는 알리고 내 정보만 새로 받는다.
			const d = Math.trunc(Number(pkt.delta)) || 0;
			if (d) toast(t(d > 0 ? 'play.league.normal.up' : 'play.league.normal.down', { n: Math.abs(d) }), { kind: 'info' });
			this.fetch();
		} else if (pkt?.type === 'leagueResult') {
			this.result = {
				placement: Number(pkt.placement) || 0,
				delta: Number(pkt.delta) || 0,
				before: Number(pkt.before) || 0,
				trophies: Number(pkt.trophies) || 0,
				tierBefore: Number(pkt.tierBefore),
				tier: Number(pkt.tier),
				size: Number(pkt.size) || 0
			};
			// 신고할 수 있는 상대(이름·ID 없이 등수·레벨과, 서버만 읽는 토큰). 사람인지 봇인지는 알 수 없다. 결과 띠가 사라져도 남고, 다음 매칭을 잡을 때 비운다.
			this.rivals = (Array.isArray(pkt.rivals) ? pkt.rivals : []).filter((x) => x && typeof x.token === 'string').map((x) => ({ placement: Number(x.placement) || 0, level: Number(x.level) || 1, name: String(x.name ?? ''), token: x.token }));
		}
	}
}

export const league = new LeagueStore();
