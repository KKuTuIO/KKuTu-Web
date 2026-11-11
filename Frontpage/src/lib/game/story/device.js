/**
 * A script for the device in hand. Lines may have a phone's wording under the key `<key>.phone` (the desktop one talks of the left side, the
 * mouse and the Enter key); a node with `noPhone` is a part of the page that a phone has not got.
 * @param {{nodes:any[]}} script
 * @param {boolean} phone
 * @param {(key:string)=>boolean} has whether a message exists
 */
export function forDevice(script, phone, has) {
	if (!phone) return script;
	return {
		...script,
		nodes: script.nodes
			.filter((n) => !n.noPhone)
			.map((n) => (n.say && has(`${n.say.text}.phone`) ? { ...n, say: { ...n.say, text: `${n.say.text}.phone` } } : n))
	};
}
