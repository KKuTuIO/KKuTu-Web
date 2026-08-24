/**
 * Keep profile lists safe for keyed Svelte rendering. Profile identifiers are
 * the public identity of a row, so malformed rows and later duplicates must
 * not reach a keyed each block.
 *
 * @param {unknown} value
 * @returns {Array<Record<string, unknown>>}
 */
export function uniqueProfiles(value) {
    if (!Array.isArray(value)) return [];

    const seen = new Set();
    return value.filter(profile => {
        if (!profile || typeof profile !== 'object' || Array.isArray(profile)) return false;
        const id = String(profile.id ?? '').trim();
        if (!id || seen.has(id)) return false;
        seen.add(id);
        return true;
    });
}
