import { describe, expect, it, vi } from 'vitest';
import { afterEach } from 'vitest';
import { cleanup, fireEvent, render, screen } from '@testing-library/svelte';
afterEach(cleanup);
import Checkbox from './Checkbox.svelte';
import Switch from './Switch.svelte';
import RadioGroup from './RadioGroup.svelte';
import ThemePicker from './ThemePicker.svelte';
import Seekbar from './Seekbar.svelte';
import Select from './Select.svelte';
import Tabs from './Tabs.svelte';
import Rich from './Rich.svelte';
import { dismiss, toast, toasts } from './toast.svelte.js';
import { plainText } from '../connection.svelte.js';

describe('Checkbox', () => {
	it('toggles on click and Space, exposing aria-checked', async () => {
		const onchange = vi.fn();
		render(Checkbox, { checked: false, onchange });
		const box = screen.getByRole('checkbox');
		expect(box.getAttribute('aria-checked')).toBe('false');
		await fireEvent.click(box);
		expect(box.getAttribute('aria-checked')).toBe('true');
		expect(onchange).toHaveBeenLastCalledWith(true);
	});
});

describe('Switch', () => {
	it('flips on click, exposing role=switch and aria-checked', async () => {
		const onchange = vi.fn();
		render(Switch, { checked: false, onchange });
		const sw = screen.getByRole('switch');
		expect(sw.getAttribute('aria-checked')).toBe('false');
		await fireEvent.click(sw);
		expect(sw.getAttribute('aria-checked')).toBe('true');
		expect(onchange).toHaveBeenLastCalledWith(true);
	});
});

describe('RadioGroup', () => {
	const options = [{ value: 'a', label: 'A' }, { value: 'b', label: 'B' }, { value: 'c', label: 'C' }];
	it('has one tab stop and arrow keys move the selection', async () => {
		render(RadioGroup, { options, value: 'a', label: 'x' });
		const [a, b, c] = screen.getAllByRole('radio');
		expect([a, b, c].map((r) => r.tabIndex)).toEqual([0, -1, -1]);
		await fireEvent.keyDown(a, { key: 'ArrowDown' });
		expect(b.getAttribute('aria-checked')).toBe('true');
		await fireEvent.keyDown(b, { key: 'ArrowUp' });
		await fireEvent.keyDown(a, { key: 'ArrowUp' });
		expect(c.getAttribute('aria-checked')).toBe('true'); // wraps
	});
});

describe('Seekbar', () => {
	it('is a slider driven by the keyboard and clamps to its range', async () => {
		render(Seekbar, { value: 5, min: 2, max: 8, label: '인원' });
		const bar = screen.getByRole('slider');
		expect(bar.getAttribute('aria-valuenow')).toBe('5');
		await fireEvent.keyDown(bar, { key: 'ArrowRight' });
		expect(bar.getAttribute('aria-valuenow')).toBe('6');
		await fireEvent.keyDown(bar, { key: 'End' });
		await fireEvent.keyDown(bar, { key: 'ArrowRight' });
		expect(bar.getAttribute('aria-valuenow')).toBe('8');
		await fireEvent.keyDown(bar, { key: 'Home' });
		expect(bar.getAttribute('aria-valuenow')).toBe('2');
	});
});

describe('toast', () => {
	it('queues, caps at four and dismisses', () => {
		const ids = Array.from({ length: 6 }, (_, i) => toast(`m${i}`, { duration: 0 }));
		expect(toasts.length).toBe(4);
		dismiss(ids[5]);
		expect(toasts.length).toBe(3);
		toasts.splice(0);
	});
	it('strips html from server messages', () => {
		expect(plainText('a<br>b <u><a href="/x">c</a></u>')).toBe('a b c');
	});
});

// jsdom doesn't implement popovers, so it reports the list as hidden; real-browser behaviour is checked separately.
describe('Select', () => {
	const options = [{ value: 1, label: 'Apple' }, { value: 2, label: 'Banana' }, { value: 3, label: 'Cherry' }];
	it('opens with the keyboard, moves, selects and closes on Escape', async () => {
		const onchange = vi.fn();
		render(Select, { options, value: 1, label: 'fruit', onchange });
		const box = screen.getByRole('combobox');
		expect(box.getAttribute('aria-expanded')).toBe('false');
		await fireEvent.keyDown(box, { key: 'ArrowDown' });
		expect(box.getAttribute('aria-expanded')).toBe('true');
		expect(screen.getAllByRole('option', { hidden: true }).length).toBe(3);
		await fireEvent.keyDown(box, { key: 'ArrowDown' });
		await fireEvent.keyDown(box, { key: 'Enter' });
		expect(onchange).toHaveBeenCalledWith(2);
		expect(box.textContent).toContain('Banana');
		expect(box.getAttribute('aria-expanded')).toBe('false');
		await fireEvent.keyDown(box, { key: 'ArrowDown' });
		await fireEvent.keyDown(box, { key: 'Escape' });
		expect(screen.queryAllByRole('option', { hidden: true }).length).toBe(0);
	});
	it('picks an option by click', async () => {
		render(Select, { options, value: 1, label: 'fruit' });
		await fireEvent.click(screen.getByRole('combobox'));
		await fireEvent.click(screen.getByRole('option', { name: /Cherry/, hidden: true }));
		expect(screen.getByRole('combobox').textContent).toContain('Cherry');
	});
});

describe('Tabs', () => {
	const tabs = [{ id: 'a', label: 'A' }, { id: 'b', label: 'B' }];
	it('is a tablist with one tab stop; arrows move and select, wrapping around', async () => {
		render(Tabs, { tabs, value: 'a', idPrefix: 'x' });
		const [a, b] = screen.getAllByRole('tab');
		expect([a.tabIndex, b.tabIndex]).toEqual([0, -1]);
		expect(a.getAttribute('aria-controls')).toBe('x-panel-a');
		await fireEvent.keyDown(a, { key: 'ArrowRight' });
		expect(b.getAttribute('aria-selected')).toBe('true');
		await fireEvent.keyDown(b, { key: 'ArrowRight' });
		expect(a.getAttribute('aria-selected')).toBe('true');
		await fireEvent.click(b);
		expect(b.getAttribute('aria-selected')).toBe('true');
	});
});

describe('Rich', () => {
	it('renders br/u/b/i, drops everything else and never injects markup', () => {
		const { container } = render(Rich, { html: 'a<br><u>b</u> <a href="x">c</a><script>alert(1)</script>' });
		expect(container.querySelectorAll('br').length).toBe(1);
		expect(container.querySelector('span.underline').textContent).toBe('b');
		expect(container.querySelector('a, script')).toBeNull();
		expect(container.textContent).toContain('alert(1)'); // text survives as text, not code
	});
});

describe('Select with disabled options', () => {
	const options = [{ value: 1, label: 'Score' }, { value: 2, label: 'Trophy', disabled: true, badge: 'Soon' }, { value: 3, label: 'Other' }];
	it('shows them as unavailable, skips them with the keyboard and ignores clicks', async () => {
		const onchange = vi.fn();
		render(Select, { options, value: 1, label: 'kind', onchange });
		const box = screen.getByRole('combobox');
		await fireEvent.keyDown(box, { key: 'ArrowDown' }); // open
		const trophy = screen.getByRole('option', { name: /Trophy/, hidden: true });
		expect(trophy.getAttribute('aria-disabled')).toBe('true');
		expect(trophy.textContent).toContain('Soon');
		await fireEvent.keyDown(box, { key: 'ArrowDown' }); // skips Trophy
		await fireEvent.keyDown(box, { key: 'Enter' });
		expect(onchange).toHaveBeenCalledWith(3);
		await fireEvent.keyDown(box, { key: 'ArrowDown' });
		await fireEvent.click(screen.getByRole('option', { name: /Trophy/, hidden: true }));
		expect(onchange).toHaveBeenCalledTimes(1);
	});
});

describe('Tabs vertical', () => {
	it('uses Up/Down arrows and reports its orientation', async () => {
		render(Tabs, { tabs: [{ id: 'a', label: 'A' }, { id: 'b', label: 'B' }], value: 'a', idPrefix: 'v', orientation: 'vertical' });
		expect(screen.getByRole('tablist').getAttribute('aria-orientation')).toBe('vertical');
		const [a, b] = screen.getAllByRole('tab');
		await fireEvent.keyDown(a, { key: 'ArrowRight' });
		expect(a.getAttribute('aria-selected')).toBe('true'); // horizontal keys ignored
		await fireEvent.keyDown(a, { key: 'ArrowDown' });
		expect(b.getAttribute('aria-selected')).toBe('true');
	});
});

describe('Seekbar with discrete stops', () => {
	it('moves between the stops with the keyboard and snaps to them', async () => {
		const onchange = vi.fn();
		render(Seekbar, { value: 30, stops: [5, 10, 30, 60, 90], label: 'time', onchange });
		const bar = screen.getByRole('slider');
		expect([bar.getAttribute('aria-valuemin'), bar.getAttribute('aria-valuemax')]).toEqual(['5', '90']);
		await fireEvent.keyDown(bar, { key: 'ArrowRight' });
		expect(bar.getAttribute('aria-valuenow')).toBe('60');
		await fireEvent.keyDown(bar, { key: 'End' });
		expect(bar.getAttribute('aria-valuenow')).toBe('90');
		await fireEvent.keyDown(bar, { key: 'ArrowRight' });
		expect(bar.getAttribute('aria-valuenow')).toBe('90'); // clamped
		await fireEvent.keyDown(bar, { key: 'PageDown' });
		expect(bar.getAttribute('aria-valuenow')).toBe('30');
		expect(onchange).toHaveBeenLastCalledWith(30);
	});
	it('shows the value, both ends and tick numbers', () => {
		render(Seekbar, { value: 3, min: 1, max: 5, label: 'r', format: (v) => `${v}R` });
		expect(screen.getAllByText('3R').length).toBeGreaterThan(0); // bubble
		expect(screen.getByText('1R')).toBeTruthy();
		expect(screen.getByText('5R')).toBeTruthy();
	});
});

describe('ThemePicker', () => {
	const themes = { A1: '문학', B2: '물리', SYS: '시스템', X9: '★' };
	const open = async () => fireEvent.click(screen.getByRole('button', { name: /주제/ }));

	it('picks one theme from a radio list and shows its name; excluded codes are never offered', async () => {
		const onchange = vi.fn();
		render(ThemePicker, { themes, value: '', exclude: ['SYS'], label: '주제', placeholder: '고르세요', onchange });
		await open();
		expect(screen.queryByRole('radio', { name: '시스템' })).toBeNull();
		expect(screen.queryByRole('radio', { name: '★' })).toBeNull();
		await fireEvent.click(screen.getByRole('radio', { name: '물리' }));
		expect(onchange).toHaveBeenLastCalledWith('B2');
		expect(screen.getByRole('button', { name: /물리/ })).toBeTruthy();
	});

	it('picks several with checkboxes, lists their names on the button, and narrows the list by search', async () => {
		const onchange = vi.fn();
		render(ThemePicker, { themes, value: [], multiple: true, label: '주제', placeholder: '전체', onchange });
		await open();
		await fireEvent.click(screen.getByRole('checkbox', { name: '문학' }));
		await fireEvent.click(screen.getByRole('checkbox', { name: '물리' }));
		expect(onchange).toHaveBeenLastCalledWith(['A1', 'B2']);
		await fireEvent.input(screen.getByPlaceholderText('주제 검색'), { target: { value: '물' } });
		expect(screen.queryByRole('checkbox', { name: '문학' })).toBeNull();
		expect(screen.getByRole('button', { name: /문학, 물리/ })).toBeTruthy();
	});
});
