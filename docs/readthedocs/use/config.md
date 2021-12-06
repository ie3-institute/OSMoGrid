# Configuration options for OSMoGrid

## Input 
### OpenStreetMaps
At least one of the following configuration blocks has to be defined.

#### *.pbf-File (`input.osm.pbf`)
| Parameter | Allowed values | Note                     |
| --------- | -------------- |--------------------------|
| file      | `String`       | Path to input *.pbf-File |

### Assets
At least one of the following configuration blocks has to be defined.

#### File (`input.asset.file`)
| Parameter   | Allowed values | Note                                                         |
|-------------|----------------|--------------------------------------------------------------|
| directory   | `String`       | Path to *.csv input files                                    |
| hierarchic  | `Boolean`      | If files are given in directory hierarchy (default: `false`) |

## Output
At least one of the following configuration blocks has to be defined.

#### File (`output.file`)
| Parameter   | Allowed values | Note                                                         |
|-------------|----------------|--------------------------------------------------------------|
| directory   | `String`       | Path for *.csv output files                                  |
| hierarchic  | `Boolean`      | If files are given in directory hierarchy (default: `false`) |

## Generation
At least one of the following configuration blocks has to be defined.

#### Lv (`generation.lv`)
| Parameter                | Allowed values | Note                                                      |
|--------------------------|----------------|-----------------------------------------------------------|
| distinctHouseConnections | `Boolean`      | Build distinct house connection lines? (default: `false`) |
