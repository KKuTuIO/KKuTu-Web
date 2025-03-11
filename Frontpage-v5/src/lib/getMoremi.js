
let moremiData = [];
export async function getMoremi(uid){
    if (moremiData[uid]) return moremiData[uid];
    const res = await fetch(`/user/${uid}`);
    var data = await res.json();

    for (var key in data.equip) {
      if (data.equip[key] === null) {
        data.equip[key] = "default";
      }
      else if (data.equip[key] === "stars") {
        data.equip[key] = "stars.gif";
      }
      else {
        data.equip[key] = data.equip[key]+".png";
      }
    }

    moremiData[uid] = data.equip;
    return data.equip;
  }