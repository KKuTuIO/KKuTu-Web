import { describe, expect, it } from 'vitest';
import { framePath, titleOf } from './title.js';

const ok = { shape: 'tab', from: '#fff3b0', to: '#e9c94e', edge: '#fff8d6', text: '#7a5c00', ornament: 'ring' };

describe('titleOf', () => {
	it('reads the frame of an item', () => expect(titleOf({ options: { title: ok } })).toEqual({ ...ok, outline: null }));
	it('keeps an outline and drops an unknown ornament', () => expect(titleOf({ options: { title: { ...ok, outline: '#112233', ornament: 'x' } } })).toMatchObject({ outline: '#112233', ornament: null }));
	it('is null without a usable frame', () => {
		expect(titleOf(undefined)).toBeNull();
		expect(titleOf({ options: {} })).toBeNull();
		expect(titleOf({ options: { title: { ...ok, shape: 'star' } } })).toBeNull();
		expect(titleOf({ options: { title: { ...ok, text: 'red' } } })).toBeNull();
	});
});

describe('framePath', () => {
	it('is a closed path for each shape', () => {
		for (const s of ['pill', 'tab', 'plate']) expect(framePath(s, 80, 20)).toMatch(/^M.*Z$/);
	});
	it('gives the tab a step in its top edge', () => expect(framePath('tab', 100, 20)).toContain('L'));
});
