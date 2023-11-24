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


## Voltage
With the following configuration the voltage levels of the generated grids can be specified.
For all voltage levels three parameter can be set

1. id: name for the voltage level
2. vNom: optional array with multiple voltage values (in kV)
3. default: voltage value to use, if vNom is empty or not given (in kV)


#### Lv (`voltage.lv`)
Configuration of low voltage grids.

- Default id: `lv`
- Default voltage value: `0.4`


#### Mv (`voltage.mv`)
Configuration of medium voltage grids.

- Default id: `mv`
- Default voltage value: `10.0`

#### Hv (`voltage.hv`)
Configuration of high voltage grids.

- Default id: `hv`
- Default voltage value: `110.0`


## Generation
At least one of the following configuration blocks has to be defined.


#### Lv (`generation.lv`)
| Parameter                  | Allowed values | Note                                                                         |
|----------------------------|----------------|------------------------------------------------------------------------------|
| amountOfGridGenerators     | `Int`          | Amount of actors to build actual grids (default: 10)                         |
| amountOfRegionCoordinators | `Int`          | Amount of actors to coordinate grid generation per municipality (default: 5) |
| distinctHouseConnections   | `Boolean`      | Build distinct house connection lines? (default: `false`)                    |


#### Mv (`generation.mv`)

| Parameter           | Allowed values | Note                                                                                                                        |
|---------------------|----------------|-----------------------------------------------------------------------------------------------------------------------------|
| spawnMissingHvNodes | `Boolean`      | If no high voltage nodes are found for a given area, this parameter specifies if a node should be created (default: `true`) |
