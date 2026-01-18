
const MAX_LEVEL = 720;
let EXP = [];
var i = 0;

export function getRequiredScore(lv) {
    if (lv <= 240) return Math.round(
        (!(lv % 5) * 0.3 + 1) * (!(lv % 15) * 0.4 + 1) * (!(lv % 45) * 0.5 + 1) * (
            120 + Math.floor(lv / 5) * 60 + Math.floor(lv * lv / 225) * 120 + Math.floor(lv * lv / 2025) * 180
        )
    ); else if (lv <= 480) return Math.round(
        (!(lv % 5) * 0.3 + 1) * (!(lv % 15) * 0.4 + 1) * (!(lv % 45) * 0.5 + 1) * (
            120 + Math.floor(lv / 5) * 100 + Math.floor(lv * lv / 225) * 170 + Math.floor(lv * lv / 2025) * 240
        )
    ); else return Math.round(
        (!(lv % 5) * 0.3 + 1) * (!(lv % 15) * 0.4 + 1) * (!(lv % 45) * 0.5 + 1) * (
            120 + Math.floor(lv / 5) * 140 + Math.floor(lv * lv / 225) * 220 + Math.floor(lv * lv / 2025) * 300
        )
    );
}

EXP.push(getRequiredScore(1));
    for (i = 2; i < MAX_LEVEL; i++) {
        EXP.push(EXP[i - 2] + getRequiredScore(i));
    }
    EXP[MAX_LEVEL - 1] = Infinity;
    EXP.push(Infinity);

export function getLevel(score) {
    var i, l = EXP.length;

    for (i = 0; i < l; i++) if (score < EXP[i]) break;
    return i + 1;
}

export function getLevelImage(score) {
    const lv = getLevel(score) - 1;
    const columns = 25;
    const lX = (lv % columns) * -100;
    const lY = Math.floor(lv / columns) * -100;
    return `background-position: ${lX}% ${lY}%;`;
}