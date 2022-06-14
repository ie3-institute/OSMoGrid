[out:xml][timeout:25][bbox:51.5191,7.3099,51.5242,7.3225];
// gather results
(
  node["building"];
  way["building"];
  relation["building"];
  
  node["highway"];
  way["highway"];
  relation["highway"];
  
  node["landuse"];
  way["landuse"];
  relation["landuse"];
  
  relation["boundary"="administrative"]["admin_level"~"^(6|7|8|9)$"];
  
  node["power"="substation"];
  way["power"="substation"];
  relation["power"="substation"];
);
// print results
out body;
>;
out skel qt;
