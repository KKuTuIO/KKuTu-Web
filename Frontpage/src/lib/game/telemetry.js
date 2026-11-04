/**
 * Input telemetry for the game screen: where the legacy client's anti-cheat observation would plug in.
 *
 * It is switched OFF unless the build is a development one, and even then nothing leaves the browser: the trace is only
 * kept in memory (`trace`) so it can be looked at. Sending it to the server (`securityTelemetry`) stays disabled until
 * the owner has approved the policy for this client. The hook points are the ones the legacy client calls:
 *   turnStart(pkt)  - a turn begins (a new input epoch)
 *   input(value)    - the word field changed
 *   take()          - a word is being submitted: the sequence number to send as `lastInputSeq`
 */
export function createTelemetry({ enabled = false } = {}) {
	let seq = 0;
	let epoch;
	const trace = [];
	if (!enabled) {
		return { enabled: false, trace, turnStart() {}, input() {}, take: () => -1 };
	}
	return {
		enabled: true,
		trace,
		turnStart(pkt) {
			epoch = pkt.inputEpoch;
			seq = 0;
			trace.length = 0;
		},
		input(value) {
			trace.push({ seq: seq++, at: performance.now(), length: value.length, epoch });
			if (trace.length > 200) trace.shift();
		},
		take() {
			// Nothing is sent to the server yet, so the server must not be told there is a trace to look up.
			seq = 0;
			trace.length = 0;
			return -1;
		}
	};
}

export const telemetry = createTelemetry({ enabled: !!import.meta.env?.DEV });
