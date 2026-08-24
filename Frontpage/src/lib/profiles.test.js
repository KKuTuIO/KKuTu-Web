import {describe, expect, it} from 'vitest';
import {uniqueProfiles} from './profiles.js';

describe('profile list normalization', () => {
    it('keeps the first row for each profile identity', () => {
        const first = {id: 'profile-1', nickname: 'first'};
        const duplicate = {id: 'profile-1', nickname: 'duplicate'};

        expect(uniqueProfiles([first, duplicate, {id: 'profile-2'}])).toEqual([
            first,
            {id: 'profile-2'}
        ]);
    });

    it('rejects missing identities and normalizes numeric identity comparisons', () => {
        expect(uniqueProfiles([
            null,
            {},
            {id: ''},
            {id: 7, nickname: 'numeric'},
            {id: '7', nickname: 'same identity'}
        ])).toEqual([{id: 7, nickname: 'numeric'}]);
    });

    it('returns an empty list for malformed API payloads', () => {
        expect(uniqueProfiles(null)).toEqual([]);
        expect(uniqueProfiles({id: 'not-a-list'})).toEqual([]);
    });
});
