import { afterEach, describe, expect, it } from 'vitest';
import { cleanup, fireEvent, render, screen } from '@testing-library/svelte';
import { tick } from 'svelte';
import ConfirmHost from './ConfirmHost.svelte';
import { notify } from './confirm.svelte.js';

afterEach(cleanup);

const TEMPLATE = '{words} 단어는 어인정에서 사용 가능한 단어예요. 방 설정에서 <b>어인정</b>을 켜고 사전에 없는 다양한 단어를 사용해보세요!';

describe('notify (the 어인정 nudge dialog)', () => {
	it('shows one button, the <b> part bold, and the words as plain text', async () => {
		render(ConfirmHost);
		const done = notify({ message: TEMPLATE, slots: { words: '가가, 나나, 다다' } });
		await tick();
		const dialog = await screen.findByRole('dialog');
		expect(dialog.textContent).toContain('가가, 나나, 다다 단어는 어인정에서 사용 가능한 단어예요.');
		expect([...dialog.querySelectorAll('.font-extrabold')].map((e) => e.textContent)).toEqual(['어인정']);
		expect(dialog.querySelectorAll('button.kk-btn-primary')).toHaveLength(1);
		expect(screen.queryByText('취소')).toBeNull();
		await fireEvent.click(dialog.querySelector('button.kk-btn-primary'));
		expect(await done).toBe(true);
	});

	it('never turns what a player typed, or other markup, into elements', async () => {
		render(ConfirmHost);
		notify({ message: TEMPLATE + '<img src=x onerror="window.__pwned=1"><script>window.__pwned=1</script>', slots: { words: '<img src=x onerror="window.__pwned=1">, <b>굵게</b>, </p><script>1</script>' } });
		await tick();
		const dialog = await screen.findByRole('dialog');
		expect(dialog.querySelector('img, script')).toBeNull();
		expect(window.__pwned).toBeUndefined();
		// the typed words stay literal text, tags and all, and are not bold
		expect(dialog.textContent).toContain('<img src=x onerror="window.__pwned=1">, <b>굵게</b>, </p><script>1</script>');
		expect([...dialog.querySelectorAll('.font-extrabold')].map((e) => e.textContent)).toEqual(['어인정']);
	});
});
