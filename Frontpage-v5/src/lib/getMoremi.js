let moremiData = [];
export async function getMoremi(uid){
    if (moremiData[uid]) return moremiData[uid];
    const res = await fetch(`/user/${uid}`);
    const json = await res.json();
    const equip = json.equip || {};

    const proc = {};
    for (let key in equip) {
      if (!equip[key] || equip[key] === null) {
        proc[key] = "default.png";
      }
      else if (equip[key] === "stars") {
        proc[key] = "stars.gif";
      }
      else {
        proc[key] = val.endsWith('.png') ? val : val + ".png";
      }
    }

    moremiData[uid] = proc;
    return proc;
  }