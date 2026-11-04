/** A nickname in full, tag included: what the tooltip of a truncated name says. */
export const fullNick = (u) => (u?.tag ? `${u.name}#${u.tag}` : (u?.name ?? ''));
