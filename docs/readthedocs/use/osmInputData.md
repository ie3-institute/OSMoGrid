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

Please note that the boundaries we are using here can be of type relation or of type way. The type needs to be specified. Belows code includes both, the boundary of TU Dortmund University as type relation and, currently comment out, an area in Dortmund which is limited by a way. To use the latter one, simply move the comment (`//`) before `w  way(id:37141772);` and place it before `relation(id:6188406);` to change from using relation's id to using the way's id.  

```
[out:xml][timeout:30];

// Get the way or relation and convert it to an area dynamically
// id of the relation of Dortmund University: 6188406
// id of an area limted as a way in Dortmund: 37141772
(
  //way(id:37141772);
  relation(id:6188406);
);
map_to_area -> .searcharea;

(
 node["building"](area.searcharea); 
  way["building"](area.searcharea); 
  relation["building"](area.searcharea); 

  node["highway"](area.searcharea); 
  way["highway"](area.searcharea); 
  relation["highway"](area.searcharea); 

  node["landuse"](area.searcharea); 
  way["landuse"](area.searcharea); 
  relation["landuse"](area.searcharea); 

  node["power"="substation"](area.searcharea); 
  way["power"="substation"](area.searcharea); 
  relation["power"="substation"](area.searcharea); 
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
