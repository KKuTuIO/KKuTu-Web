/**
 * A small calculator engine for the 수학 대결 window: + − × ÷ and brackets, decimals, a leading minus. Nothing is evaluated
 * with eval(): the expression is read by a tiny recursive-descent parser.
 */
const OPS = { '+': '+', '-': '-', '−': '-', '×': '*', '*': '*', '÷': '/', '/': '/' };

/** @returns {number | null} null when the expression is not complete or cannot be worked out (e.g. a division by zero) */
export function evaluate(expr) {
	const src = String(expr).replace(/\s+/g, '');
	if (!src) return null;
	let i = 0;
	const peek = () => src[i];
	function number() {
		const m = /^\d*\.?\d+|^\d+\.?/.exec(src.slice(i));
		if (!m) throw new Error('number');
		i += m[0].length;
		return parseFloat(m[0]);
	}
	function factor() {
		if (peek() === '-' || peek() === '−') {
			i++;
			return -factor();
		}
		if (peek() === '+') {
			i++;
			return factor();
		}
		if (peek() === '(') {
			i++;
			const v = sum();
			if (peek() !== ')') throw new Error('bracket');
			i++;
			return v;
		}
		return number();
	}
	function product() {
		let v = factor();
		while (OPS[peek()] === '*' || OPS[peek()] === '/') {
			const op = OPS[src[i++]];
			const r = factor();
			if (op === '/' && r === 0) throw new Error('zero');
			v = op === '*' ? v * r : v / r;
		}
		return v;
	}
	function sum() {
		let v = product();
		while (OPS[peek()] === '+' || OPS[peek()] === '-') {
			const op = OPS[src[i++]];
			const r = product();
			v = op === '+' ? v + r : v - r;
		}
		return v;
	}
	try {
		const v = sum();
		if (i !== src.length || !Number.isFinite(v)) return null;
		return v;
	} catch {
		return null;
	}
}

/** The result as it is written: up to 12 significant digits, no floating-point tails (0.1+0.2 is 0.3). */
export const format = (v) => String(Number(v.toPrecision(12)));
