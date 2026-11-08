/**
 * Who speaks in a story or tutorial. Each character is a Moremi (the avatar the players have) dressed in items, with a name that is a
 * locale key. A script's lines say `who: 'haksamo'`; new characters (a story's cast) are added here.
 */
export const CAST = {
	haksamo: { name: 'play.mascot.haksamo', equip: { Mhead: 'haksamo' } }
};

/** The items the character wears, or none for someone who isn't in the cast. */
export const castEquip = (who) => CAST[who]?.equip ?? {};
