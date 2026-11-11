import { afterEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen } from '@testing-library/svelte';
import { tick } from 'svelte';
import { createLobbyStore } from '../lobbyStore.svelte.js';
import { answer, question } from '../ui/confirm.svelte.js';
import MailMenu from './MailMenu.svelte';

afterEach(async () => {
	answer(false);
	await new Promise((r) => setTimeout(r, 0)); // `answer` clears the question on a timer
	cleanup();
});

const timed = (name) => ({ type: 'item', name, q: 1, expire: 259200 });
function setup(rewards) {
	const store = createLobbyStore(1);
	store.net.status = 'ready';
	store.net.mails = [{ _id: 'levelreward:1', name: '레벨 보상', content: '레벨 2~36 달성 보상이에요.', hasRewards: true, claimable: true, rewards }];
	store.requestMailbox = vi.fn();
	store.claimMail = vi.fn();
	return store;
}
async function openMailbox() {
	await fireEvent.click(screen.getByRole('button', { name: /우편함/ }));
	await fireEvent.click(await screen.findByRole('tab', { name: /우편함/ }));
}
const claimButton = () => screen.getByRole('button', { name: '받기' });

describe('MailMenu claiming', () => {
	it('asks first when the mail has items whose time starts on receipt, and says how many', async () => {
		const store = setup([timed('bonusEXP_5'), timed('bonusMNY_5'), { type: 'ping', q: 50 }]);
		render(MailMenu, { store });
		await openMailbox();
		await fireEvent.click(claimButton());
		await tick();
		expect(question.current?.message).toBe('수령하면 바로 기한이 시작되는 아이템이 2개 있어요. 정말 수령할까요?');
		expect(store.claimMail).not.toHaveBeenCalled();
		answer(true);
		await tick();
		await tick();
		expect(store.claimMail).toHaveBeenCalledWith('levelreward:1');
	});

	it('does not claim when the player says no', async () => {
		const store = setup([timed('bonusEXP_5')]);
		render(MailMenu, { store });
		await openMailbox();
		await fireEvent.click(claimButton());
		await tick();
		answer(false);
		await tick();
		await tick();
		expect(store.claimMail).not.toHaveBeenCalled();
	});

	it('claims at once when nothing in the mail runs out', async () => {
		const store = setup([{ type: 'ping', q: 50 }, { type: 'item', name: 'dictPage', q: 1 }]);
		render(MailMenu, { store });
		await openMailbox();
		await fireEvent.click(claimButton());
		await tick();
		expect(question.current).toBeNull();
		expect(store.claimMail).toHaveBeenCalledWith('levelreward:1');
	});
});
