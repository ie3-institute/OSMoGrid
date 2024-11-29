# OpenStreetMap (OSM) Input Data

## How to get OSM Input Data
### Overpass
OSM data can be easily collected by using [overpass](https://overpass-turbo.eu/) but needs to be converted into .pbf format before using OsmoGrid.

#### Remarks for using overpass
We require the following information in your input data:
  - Boundaries,
  - Buildings,
  - Streets,
  - Landuse,
  - Substations.

Thus filtering for nodes, ways and relations for these categories is required.

Since even small areas can result in huge grid data, we encourage to limit the export to limited areas. This can be done e.g. by boundary boxes or based on relations. For more options, please check [Overpass API User's Manual](https://dev.overpass-api.de/overpass-doc/en/).

#### Example query based on bounding box 

This example will return the OSM data for the area within given coordinates. They usually follow the standard format of: min longitude, min latitude, max longitude, max latitude. 

```
[out:xml][timeout:10][bbox:52.0262,7.3008,52.03672,7.31806];
// gather results
(
  relation["boundary"="administrative"]["admin_level"~"^(6|7|8|9|10|11|12)$"]({{bbox}});
  relation["boundary"="census"]["admin_level"~"^(11|12)$"]({{bbox}});

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

```

#### Example query based on OSM relation

This example will return the OSM data for the area of [TU Dortmund University](https://www.tu-dortmund.de/)

```
[out:xml][timeout:30];
// relation of Dortmund University: 6188406
// relation: add 3600000000
{{searcharea=area:3606188406}}
(  
  relation["boundary"="administrative"]["admin_level"~"^(6|7|8|9|10|11|12)$"]({{searcharea}});
  relation["boundary"="census"]["admin_level"~"^(11|12)$"]({{searcharea}});

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
```


### Converting to pbf with osmium

Once the OSM data has been downloaded (e.g. as raw OSM data), it needs to be converted into a .pbf file. This can be done using the osmium package. Please note that these commands are intended to work on Ubuntu and may vary depending on your operating system.

```
sudo apt install osmium-tool
```


```
osmium cat input.osm -o output.pbf
```
