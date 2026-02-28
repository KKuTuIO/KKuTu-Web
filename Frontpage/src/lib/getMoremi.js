const moremiData = {};

export async function getMoremi(uid){
    if (moremiData[uid]) return moremiData[uid];
    try {
      const res = await fetch(`/user/${uid}`);
      if (!res.ok) return {};

      const data = await res.json(); 
      if (data.error || !data.equip) return {};

      for (const key in data.equip) { 
        const item = data.equip[key];
        if (item === null) data.equip[key] = "default";
        else if (item === "stars") {
          data.equip[key] = "stars.gif";
        } else data.equip[key] = item + ".png";
      }

      moremiData[uid] = data.equip;
      return data.equip;
    } catch (err) {
      return {};
    }
}