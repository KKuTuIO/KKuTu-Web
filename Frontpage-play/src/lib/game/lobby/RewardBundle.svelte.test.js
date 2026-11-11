import { afterEach, describe, expect, it } from 'vitest';
import { cleanup, fireEvent, render, screen, within } from '@testing-library/svelte';
import { tick } from 'svelte';
import RewardBundle from './RewardBundle.svelte';

afterEach(cleanup);
const details = { leaf: { name: '리프' }, wordToken: { name: '단어 토큰' }, fedora: { name: '페도라' } };
const ping = (q) => ({ type: 'ping', q });
const item = (name, q = 1) => ({ type: 'item', name, q });
const show = (rewards, extra = {}) => render(RewardBundle, { rewards, details, shop: {}, ...extra });

describe('RewardBundle', () => {
	it('shows an item as the one that stands for the bundle, even when 핑 comes first', () => {
		show([ping(300), item('wordToken', 3), item('leaf')]);
		const button = screen.getByRole('button', { name: /단어 토큰/ });
		expect(button.textContent).toContain('x3'); // its amount in the corner
		expect(button.textContent).toContain('+2'); // and the others as a count
	});

	it('shows 핑 only when there is nothing else', () => {
		show([ping(1500)]);
		const picture = screen.getByRole('img', { name: /핑 ×1,500/ });
		expect(picture.textContent).toContain('x1,500');
		expect(screen.queryByText(/^\+\d/)).toBeNull(); // a bundle of one has no badge
		expect(screen.queryByRole('button')).toBeNull(); // and nothing to open
	});

	it('shows event points when the only other thing is 핑', () => {
		show([ping(10), { type: 'ep', q: 5 }]);
		expect(screen.getByRole('button', { name: /행사 포인트 외 1개/ })).toBeTruthy();
	});

	it('writes the whole amount, not a short form', () => {
		show([item('wordToken', 12000)]);
		expect(screen.getByRole('img').textContent).toContain('x12,000');
	});

	it('hides the amount of a single piece', () => {
		show([item('leaf')]);
		expect(screen.getByRole('img').textContent).not.toContain('x1');
	});

	it('opens the list of everything in the bundle, with its amounts, when pressed', async () => {
		show([ping(300), item('wordToken', 3), item('leaf')]);
		expect(screen.queryByRole('dialog', { hidden: true })).toBeNull(); // not made until wanted
		await fireEvent.click(screen.getByRole('button', { name: /단어 토큰/ }));
		await tick();
		const dialog = screen.getByRole('dialog', { name: '구성품', hidden: true });
		const rows = within(dialog).getAllByRole('listitem');
		expect(rows).toHaveLength(3);
		expect(rows[0].textContent).toContain('핑');
		expect(rows[0].textContent).toContain('×300');
		expect(rows[1].textContent).toContain('×3');
	});

	it('is only a picture when it must sit inside a button', () => {
		show([item('wordToken', 3), item('leaf')], { plain: true });
		expect(screen.queryByRole('button')).toBeNull();
		expect(screen.getByText('+1')).toBeTruthy(); // the count is still there
	});

	it('draws nothing for an empty bundle', () => {
		const { container } = show([]);
		expect(container.textContent).toBe('');
	});

	it('can be dimmed once it is taken', () => {
		show([item('leaf')], { dim: true });
		expect(screen.getByRole('img').firstElementChild.className).toContain('opacity-45');
	});

	it('writes the amount upright, not slanted', () => {
		show([item('wordToken', 3)]);
		expect(screen.getByRole('img').querySelector('b').className).not.toContain('italic');
	});

	it('is 72px square unless told otherwise', () => {
		show([item('leaf')]);
		const face = screen.getByRole('img').firstElementChild;
		expect([face.style.width, face.style.height]).toEqual(['72px', '72px']);
	});

	it('makes a long amount smaller so that it fits the square, and keeps a short one big', () => {
		const sizeOf = (q) => {
			cleanup();
			show([item('wordToken', q)]);
			return parseInt(screen.getByRole('img').querySelector('b').style.fontSize, 10);
		};
		const short = sizeOf(3);
		const long = sizeOf(1_200_000);
		expect(short).toBe(14); // a fifth of the square
		expect(long).toBeLessThan(short);
		expect(long * 0.62 * 'x1,200,000'.length).toBeLessThanOrEqual(72 * 0.9 + 1); // the text fits inside the square
	});
});
