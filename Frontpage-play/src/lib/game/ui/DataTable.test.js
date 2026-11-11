import { afterEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render } from '@testing-library/svelte';
import { createRawSnippet } from 'svelte';
import DataTable from './DataTable.svelte';

afterEach(cleanup);
const columns = [
	{ key: 'name', label: '이름', class: 'font-bold' },
	{ key: 'score', label: '점수', align: 'right', numeric: true, width: 'w-20' },
	{ key: 'act', label: '동작', hideLabel: true }
];
const rows = [{ id: 'a', name: '가', score: 10 }, { id: 'b', name: '나', score: 20 }];
const snippet = (fn) => createRawSnippet((...args) => ({ render: () => fn(...args.map((a) => a())) }));

describe('DataTable', () => {
	it('draws a header, a row per entry and the value of each column, aligned as asked', () => {
		const { container } = render(DataTable, { columns, rows, caption: '목록' });
		expect([...container.querySelectorAll('thead th')].map((th) => th.textContent.trim())).toEqual(['이름', '점수', '동작']);
		expect(container.querySelector('caption').classList.contains('sr-only')).toBe(true);
		const first = container.querySelector('tbody tr').children;
		expect(first[0].textContent).toBe('가');
		expect(first[0].classList.contains('font-bold')).toBe(true);
		expect(first[1].classList.contains('text-right') && first[1].classList.contains('tabular-nums')).toBe(true);
		expect(container.querySelector('thead th:nth-child(2)').classList.contains('w-20')).toBe(true);
		expect(container.querySelectorAll('tbody tr')).toHaveLength(2);
	});
	it('keeps a hidden header label for screen readers only', () => {
		const { container } = render(DataTable, { columns, rows });
		const th = container.querySelector('thead th:nth-child(3)');
		expect(th.textContent.trim()).toBe('동작');
		expect(th.querySelector('.sr-only')).not.toBeNull();
	});
	it('lets the caller draw a cell, and gives the dense look its own sizes', () => {
		const cell = snippet((row, col) => `<b>${col.key}:${row.id}</b>`);
		const { container } = render(DataTable, { columns: columns.slice(0, 2), rows, cell, dense: true });
		expect(container.querySelector('tbody tr td b').textContent).toBe('name:a');
		expect(container.querySelector('table').classList.contains('text-[13px]')).toBe(true);
		expect(container.querySelector('thead').classList.contains('text-[11px]')).toBe(true);
	});
	it('turns a whole row into a button when asked: mouse, Enter and Space, but not a key pressed inside it', async () => {
		const onrowclick = vi.fn();
		const cell = snippet((row, col) => (col.key === 'act' ? '<button type="button" id="inner">x</button>' : String(row[col.key])));
		const { container } = render(DataTable, { columns, rows, onrowclick, cell });
		const tr = container.querySelector('tbody tr');
		expect(tr.getAttribute('tabindex')).toBe('0');
		await fireEvent.click(tr);
		await fireEvent.keyDown(tr, { key: 'Enter' });
		await fireEvent.keyDown(tr, { key: ' ' });
		await fireEvent.keyDown(tr, { key: 'a' });
		expect(onrowclick.mock.calls.map((c) => c[0].id)).toEqual(['a', 'a', 'a']);
		await fireEvent.keyDown(container.querySelector('#inner'), { key: 'Enter' });
		expect(onrowclick).toHaveBeenCalledTimes(3);
	});
	it('does not make rows focusable when they are not clickable', () => {
		const { container } = render(DataTable, { columns, rows });
		expect(container.querySelector('tbody tr').hasAttribute('tabindex')).toBe(false);
	});
	it('shows a wide row as one cell across the table', () => {
		const wide = snippet((row) => `<i>${row.id}</i>`);
		const { container } = render(DataTable, { columns, rows, isWide: (r) => r.id === 'b', wide });
		const trs = container.querySelectorAll('tbody tr');
		expect(trs[0].children).toHaveLength(3);
		expect(trs[1].children).toHaveLength(1);
		expect(trs[1].children[0].getAttribute('colspan')).toBe('3');
		expect(trs[1].textContent).toBe('b');
	});
	it('dims the body while busy and shows the empty part under the table', () => {
		const empty = snippet(() => '<p id="none">없음</p>');
		const { container } = render(DataTable, { columns, rows: [], busy: true, empty, maxHeight: 'max-h-40' });
		expect(container.querySelector('tbody').classList.contains('opacity-60')).toBe(true);
		expect(container.querySelector('#none').textContent).toBe('없음');
		expect(container.querySelector('[data-table]').classList.contains('max-h-40')).toBe(true);
	});
});
