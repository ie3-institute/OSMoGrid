[out:xml][timeout:10][bbox:52.0262,7.3008,52.03672,7.31806];
// gather results
(

  node["boundary"];
  way["boundary"];
  relation["boundary"="administrative"]["admin_level"~"^(6|7|8|9)$"]({{bbox}});

  node["building"];
  way["building"];
  relation["building"];

  node["highway"];
  way["highway"];
  relation["highway"];

  node["landuse"];
  way["landuse"];
  relation["landuse"];

  node["power"="substation"];
  way["power"="substation"];
  relation["power"="substation"];
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
