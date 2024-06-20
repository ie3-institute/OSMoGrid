[out:xml][timeout:30];
// relation of Dortmund University: 6188406
// relation: add 3600000000
{{searcharea=area:3606188406}}
(
    node["boundary"]({{searcharea}});
  way["boundary"]({{searcharea}});
  relation["boundary"="administrative"]["admin_level"~"^(6|7|8|9)$"]({{searcharea}});


  node["building"]({{searcharea}});
  way["building"]({{searcharea}});
  relation["building"]({{searcharea}});

  node["highway"]({{searcharea}});
  way["highway"]({{searcharea}});
  relation["highway"]({{searcharea}});

  node["landuse"]({{searcharea}});
  way["landuse"]({{searcharea}});
  relation["landuse"]({{searcharea}});

  node["power"="substation"]({{searcharea}});
  way["power"="substation"]({{searcharea}});
  relation["power"="substation"]({{searcharea}});


);


// print results
out;
>;
out;
>;
out;
>;
out;
>;
out skel qt;
