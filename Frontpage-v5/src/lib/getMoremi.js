const moremiData = {};

export async function getMoremi(uid){
    if (moremiData[uid]) return moremiData[uid];
    try {
      const res = await fetch(`/user/${uid}`);
      if (!res.ok) return {};

      const json = await res.json();
      const equip = json.equip || {};
      if (typeof equip === "string") {
        try {
          equip = JSON.parse(equip);
        } catch (e) {
          equip = {};
        }
      }

      const proc = {};
      for (const key in equip) {
        const val = equip[key];
        if (!val || val === null) proc[key] = "default.png";
        else if (val === "stars") {
          proc[key] = "stars.gif";
        } else proc[key] = val.endsWith('.png') ? val : val + ".png";
      }

      moremiData[uid] = proc;
      return proc;
    } catch (err) {
      return {};
    }
}