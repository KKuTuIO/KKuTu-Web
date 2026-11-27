/**
 * The 말모이 집필면허증 as pictures (the card effects take an image): the face, laid out like a driver's license, with my Moremi as the ID photo,
 * the back with the oath, and the lanyard's print. A game item: every mark on it is our own, and nothing of a real ID is imitated.
 * Drawn on a 520 x 328 card, 2.5 times as fine.
 */
import { t } from '../i18n.svelte.js';
import { flushSync, mount, unmount } from 'svelte';
import MoremiVector from '../lobby/MoremiVector.svelte';
import { moremiLayers } from '../images.js';
import { LIMITS, oath } from './model.js';
// a copy of the CDN's img/bi/bi_vertical_white.png: a canvas cannot read the CDN's picture on every host (CORS)
import bi from './bi-white.png?inline';

export const CARD = { width: 520, height: 328 };
const HOLDER = { height: 370 }; // as wide as the card; the lanyard's landscape card is 2.25 x 1.6
const FINE = 2.5;
const FONT = '"NanumSquare Neo", sans-serif';
const INK = '#141a2e';
// A passport photo: no background, badge or things in the hands.
const HIDE = new Set(['back', 'badge', 'lhand', 'rhand', 'front']);

// ---- the Moremi ----

const loadSrc = (src) =>
	new Promise((resolve) => {
		const image = new Image();
		image.onload = () => resolve(image);
		image.onerror = () => resolve(null);
		image.src = src;
	});

/** A layer from the CDN. `no-store`: a copy a plain <img> left in the cache has no CORS headers and would be refused (see packTheme.js). */
async function remote(url) {
	try {
		const res = await fetch(url, { mode: 'cors', cache: 'no-store', credentials: 'omit' });
		return res.ok ? await createImageBitmap(await res.blob()) : null;
	} catch {
		return null;
	}
}

/** A vector part, drawn by the same component the screen uses, as a picture (eyes open). */
function vector(part) {
	const host = document.createElement('div');
	const view = mount(MoremiVector, { target: host, props: { part } });
	flushSync();
	const svg = host.querySelector('svg');
	svg.querySelectorAll('.blink-shut').forEach((n) => n.remove());
	svg.setAttribute('xmlns', 'http://www.w3.org/2000/svg');
	svg.setAttribute('width', '480');
	svg.setAttribute('height', '480');
	const text = new XMLSerializer().serializeToString(svg);
	unmount(view);
	return loadSrc(`data:image/svg+xml;charset=utf-8,${encodeURIComponent(text)}`);
}

async function portrait(equip, shop, parts) {
	const layers = moremiLayers(equip, shop, parts).filter((l) => !HIDE.has(l.part));
	const pictures = await Promise.all(layers.map((l) => (l.vector ? vector(l.vector) : remote(l.src))));
	const canvas = document.createElement('canvas');
	canvas.width = canvas.height = 480;
	const ctx = canvas.getContext('2d');
	layers.forEach((l, i) => {
		if (!pictures[i]) return; // a layer that cannot be had is left out, as on screen
		ctx.save();
		if (l.flip) ctx.translate(480, 0), ctx.scale(-1, 1);
		ctx.drawImage(pictures[i], 0, 0, 480, 480);
		ctx.restore();
	});
	return canvas;
}

// ---- drawing ----

function sheet() {
	const canvas = document.createElement('canvas');
	canvas.width = CARD.width * FINE;
	canvas.height = CARD.height * FINE;
	const ctx = canvas.getContext('2d');
	ctx.scale(FINE, FINE);
	return { canvas, ctx };
}

function write(ctx, text, x, y, size, { weight = 700, color = INK, align = 'left', spacing = 0, max } = {}) {
	ctx.font = `${weight} ${size}px ${FONT}`;
	ctx.letterSpacing = `${spacing}px`;
	ctx.fillStyle = color;
	ctx.textAlign = align;
	let s = String(text);
	if (max && ctx.measureText(s).width > max) {
		while (s.length > 1 && ctx.measureText(`${s}…`).width > max) s = s.slice(0, -1);
		s = `${s}…`;
	}
	ctx.fillText(s, x, y);
	const width = ctx.measureText(s).width;
	ctx.letterSpacing = '0px';
	return width;
}

// 지문 무늬 (guilloche): a rosette and waves, thin lines like the ones printed on cards.
function rosette(ctx, cx, cy, r, bumps, depth, turn) {
	ctx.beginPath();
	for (let i = 0; i <= 240; i++) {
		const a = (i / 240) * Math.PI * 2;
		const rr = r + depth * Math.sin(bumps * a + turn);
		ctx[i ? 'lineTo' : 'moveTo'](cx + rr * Math.cos(a), cy + rr * Math.sin(a));
	}
	ctx.closePath();
	ctx.stroke();
}
function wave(ctx, y, height, length, shift) {
	ctx.beginPath();
	for (let x = 0; x <= CARD.width; x += 5) ctx[x ? 'lineTo' : 'moveTo'](x, y + height * Math.sin(x / length + shift) + (height / 2) * Math.sin(x / (length / 2.7) - shift));
	ctx.stroke();
}

function paper(ctx) {
	ctx.fillStyle = '#eef2fb';
	ctx.fillRect(0, 0, CARD.width, CARD.height);
	ctx.fillStyle = '#e3f1dc';
	ctx.fill(new Path2D('M520 160 C430 150 380 250 250 262 L250 328 L520 328 Z'));
	ctx.lineWidth = 0.6;
	ctx.strokeStyle = 'rgb(53 83 160 / 0.2)';
	for (let i = 0; i < 14; i++) rosette(ctx, 330, 78, 20 + i * 6, 18, 5, i * 0.5);
	ctx.strokeStyle = 'rgb(47 143 91 / 0.22)';
	for (let i = 0; i < 22; i++) wave(ctx, 196 + i * 7, 9, 38, i * 0.42);
	ctx.strokeStyle = 'rgb(181 138 29 / 0.2)';
	for (let i = 0; i < 9; i++) rosette(ctx, 300, 232, 14 + i * 7, 8, 7, i * 0.35);
}

// The two icons are drawn as lines, not written with the icon font: without it (a host the CDN refuses) their names would be printed.
function lines(ctx, color, width, draw) {
	ctx.save();
	ctx.strokeStyle = color;
	ctx.lineWidth = width;
	ctx.lineCap = ctx.lineJoin = 'round';
	ctx.beginPath();
	draw();
	ctx.stroke();
	ctx.restore();
}

/** Our own mark, where a card carries its hologram. */
function mark(ctx, x, y, r) {
	ctx.save();
	const colors = ['#3553a0', '#35a0a0', '#8fbf4d', '#d3a62a', '#a8569f', '#3553a0'];
	let fill = '#3553a0';
	if (ctx.createConicGradient) {
		fill = ctx.createConicGradient(0.5, x, y);
		colors.forEach((c, i) => fill.addColorStop(i / (colors.length - 1), c));
	}
	ctx.globalAlpha = 0.85;
	ctx.fillStyle = fill;
	ctx.beginPath();
	ctx.arc(x, y, r, 0, Math.PI * 2);
	ctx.fill();
	ctx.strokeStyle = 'rgb(255 255 255 / 0.7)';
	ctx.lineWidth = 3;
	ctx.beginPath();
	ctx.arc(x, y, r - 1.5, 0, Math.PI * 2);
	ctx.stroke();
	ctx.globalAlpha = 1;
	// a stack of cards with a bookmark
	const at = (dx, dy) => [x + dx * r, y + dy * r];
	lines(ctx, '#fff', r * 0.1, () => {
		ctx.moveTo(...at(-0.46, -0.24));
		ctx.lineTo(...at(-0.46, 0.46));
		ctx.lineTo(...at(0.24, 0.46));
		ctx.roundRect(...at(-0.24, -0.46), r * 0.7, r * 0.7, r * 0.08);
		ctx.moveTo(...at(0.04, -0.46));
		ctx.lineTo(...at(0.04, -0.08));
		ctx.lineTo(...at(0.15, -0.17));
		ctx.lineTo(...at(0.26, -0.08));
		ctx.lineTo(...at(0.26, -0.46));
	});
	ctx.restore();
}

function stamp(ctx, x, y) {
	ctx.save();
	ctx.globalAlpha = 0.8;
	ctx.strokeStyle = '#d6382f';
	ctx.lineWidth = 2.5;
	ctx.beginPath();
	ctx.roundRect(x, y, 46, 46, 4);
	ctx.stroke();
	ctx.textBaseline = 'middle';
	[...t('play.malmoi.card.stamp')].forEach((c, n) => { const i = n % 2, j = Math.floor(n / 2); return write(ctx, c, x + 13 + i * 20, y + 14 + j * 19, 16, { weight: 800, color: '#d6382f', align: 'center' }); });
	ctx.restore();
}

const day = (at) => {
	const d = new Date(at);
	return `${d.getFullYear()}.${String(d.getMonth() + 1).padStart(2, '0')}.${String(d.getDate()).padStart(2, '0')}.`;
};

function face({ name, level, issued, until, no, code }, photo) {
	const { canvas, ctx } = sheet();
	paper(ctx);
	// classes
	[t('play.malmoi.card.classEdit'), level >= LIMITS.publishLevel && t('play.malmoi.card.classCreate')].filter(Boolean).forEach((line, i) => write(ctx, line, 20, 29 + i * 19, 12));
	const title = write(ctx, t('play.malmoi.card.title'), 190, 37, 24, { weight: 800, spacing: -0.5 });
	write(ctx, "(Writer's License)", 196 + title, 37, 11, { weight: 400, color: '#46506e' });
	write(ctx, no, 190, 73, 29, { weight: 800, spacing: 1 });
	// ID photo: the picture is as wide as the frame, so a wide hat is not cut off
	ctx.save();
	ctx.fillStyle = '#fff';
	ctx.fillRect(20, 84, 150, 200);
	ctx.beginPath();
	ctx.rect(20, 84, 150, 200);
	ctx.clip();
	ctx.drawImage(photo, 20, 148, 150, 150);
	ctx.restore();
	write(ctx, name, 190, 108, 22, { max: 212 });
	write(ctx, `Lv. ${level}`, 190, 134, 22);
	write(ctx, t('play.malmoi.card.issuer'), 190, 154, 13, { color: '#3c4663' });
	[[t('play.malmoi.card.issued'), day(issued)], [t('play.malmoi.card.period'), `~ ${day(until)}`], [t('play.malmoi.card.condition'), t('play.malmoi.card.perDay', { n: LIMITS.dailyEdits })]].forEach(([label, value], i) => {
		const y = 199 + i * 22;
		[...label].forEach((c, n, all) => write(ctx, c, n === 0 ? 190 : 234, y, 15, { align: n === 0 ? 'left' : 'right' }));
		write(ctx, value, 242, y, 15);
	});
	mark(ctx, 465, 119, 31);
	// the photo's faint second print
	ctx.save();
	ctx.globalAlpha = 0.5;
	ctx.fillStyle = '#fff';
	ctx.fillRect(432, 160, 66, 84);
	ctx.beginPath();
	ctx.rect(432, 160, 66, 84);
	ctx.clip();
	ctx.drawImage(photo, 432, 188, 66, 66);
	ctx.restore();
	write(ctx, code, 465, 262, 14, { align: 'center', spacing: 1 });
	lines(ctx, '#1f6b45', 1.8, () => {
		ctx.arc(28, 306.5, 7.5, 0, Math.PI * 2);
		ctx.moveTo(24.5, 306.8);
		ctx.lineTo(27, 309.3);
		ctx.lineTo(31.8, 304);
	});
	write(ctx, t('play.malmoi.card.oathDone'), 42, 311, 13, { weight: 800, color: '#1f6b45' });
	const team = write(ctx, t('play.malmoi.card.team'), 462, 311, 19, { weight: 800, align: 'right', spacing: 3 });
	write(ctx, day(issued), 454 - team, 311, 14, { align: 'right' });
	stamp(ctx, 462, 274);
	return canvas;
}

function back() {
	const { canvas, ctx } = sheet();
	paper(ctx);
	write(ctx, t('play.malmoi.card.oathHead'), 260, 48, 22, { weight: 800, align: 'center', spacing: 8 });
	let y = 92;
	oath().forEach((line, i) => {
		let row = '';
		ctx.font = `700 15px ${FONT}`;
		for (const word of `${i + 1}. ${line}`.split(' ')) {
			if (row && ctx.measureText(`${row} ${word}`).width > 460) {
				write(ctx, row, 30, y, 15);
				y += 22;
				row = word;
			} else row = row ? `${row} ${word}` : word;
		}
		write(ctx, row, 30, y, 15);
		y += 34;
	});
	mark(ctx, 52, 286, 22);
	write(ctx, t('play.malmoi.card.team'), 490, 298, 18, { weight: 800, align: 'right', spacing: 3 });
	return canvas;
}

/** The print along the strap: our BI, once per repeat (the strap's own colour shows around it). */
async function strapPrint() {
	const logo = await loadSrc(bi);
	if (!logo) return '';
	const canvas = document.createElement('canvas');
	canvas.width = 768;
	canvas.height = 192;
	const height = 100;
	const width = (height * logo.width) / logo.height;
	canvas.getContext('2d').drawImage(logo, (canvas.width - width) / 2, (canvas.height - height) / 2, width, height);
	return canvas.toDataURL('image/png');
}

/** A side in its badge holder, for the lanyard: a plain strip above the card, where the ring's slot is cut (so it does not go through the print). */
function holder(card) {
	const canvas = document.createElement('canvas');
	canvas.width = card.width;
	canvas.height = Math.round(HOLDER.height * FINE);
	const ctx = canvas.getContext('2d');
	ctx.fillStyle = '#3553a0';
	ctx.fillRect(0, 0, canvas.width, canvas.height);
	ctx.drawImage(card, 0, canvas.height - card.height);
	return canvas;
}

/**
 * @param {{name:string, level:number, issued:number, until:number, no:string, code:string, equip?:object, shop?:object, parts?:string[]}} data
 * @returns {Promise<{front:string, back:string, hung:{front:string, back:string}, strap:string}>} data URLs: the two sides, bare and in the holder, and the strap's print
 */
export async function paintLicense(data) {
	// a canvas draws with the fonts the page has at that moment, so they are asked for first
	const text = [t('play.malmoi.card.title'), t('play.malmoi.card.team'), t('play.malmoi.card.oathDone'), t('play.malmoi.card.issued'), t('play.malmoi.card.period'), t('play.malmoi.card.condition'), t('play.malmoi.card.stamp'), t('play.malmoi.card.classEdit'), t('play.malmoi.card.classCreate'), data.name, oath().join(' ')].join(' ');
	await Promise.all([400, 700, 800].map((w) => document.fonts?.load(`${w} 16px ${FONT}`, text))).catch(() => {});
	const photo = await portrait(data.equip ?? {}, data.shop ?? {}, data.parts ?? []);
	const sides = { front: face(data, photo), back: back() };
	const png = (c) => c.toDataURL('image/png');
	return { front: png(sides.front), back: png(sides.back), hung: { front: png(holder(sides.front)), back: png(holder(sides.back)) }, strap: await strapPrint() };
}
