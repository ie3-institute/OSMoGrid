# Openstreetmap Input Data

## How to get Osm Input Data
### Overpass
Using [overpass](https://overpass-turbo.eu/)

### Converting to pbf with osmium

Once the OSM data has been downloaded, it must be converted into a .pbf file. This can be done using the osmium package.


```
sudo apt install osmium
```


```
osmium cat input.osm -o output.pbf
```


### Example query

```
[out:xml][timeout:10][bbox:52.0262,7.3008,52.03672,7.31806];
// gather results
(

  node["boundary"];
  way["boundary"];
  relation["boundary"];


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
out meta;
>;
out meta;
>;
out meta;
>;
out meta;
>;
out skel qt;

```
