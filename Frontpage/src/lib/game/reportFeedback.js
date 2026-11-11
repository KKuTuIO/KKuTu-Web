/**
 * The results of the reports I made, as table rows. The server sends them as notifications of kind REPORT_RESOLVED whose message is one
 * paragraph per reported player ("최근 '<사유>'(으)로 신고한 사용자(<가린 닉네임>)를 확인한 결과 …"), a paragraph being followed by the
 * same thanks line each time; several notifications can be waiting at once. A paragraph that does not follow that wording is kept
 * as a plain line, so nothing is ever lost.
 */
const ITEM = /최근\s*'(.+?)'\s*\(으\)로\s*신고한\s*사용자\s*\((.+?)\)/;

/** @returns {{key:string, id:string, at:number, reason?:string, target?:string, raw?:string}[]} newest first */
export function reportRows(notifications) {
	const rows = [];
	for (const n of notifications ?? []) {
		if (n?.kind !== 'REPORT_RESOLVED') continue;
		const at = (Number(n.createdAt) || 0) * 1000;
		String(n.message ?? '')
			.split(/\n\s*\n/)
			.map((p) => p.trim())
			.filter(Boolean)
			.forEach((paragraph, i) => {
				const m = paragraph.match(ITEM);
				const key = `${n.id}:${i}`;
				rows.push(m ? { key, id: n.id, at, reason: m[1], target: m[2] } : { key, id: n.id, at, raw: paragraph.split('\n')[0] });
			});
	}
	return rows.sort((a, b) => b.at - a.at || (a.key < b.key ? -1 : a.key > b.key ? 1 : 0));
}
