# SubDistrictCoordinator

## General Idea### 

- Responsible for handling a sub-district
- A sub-district can also be a district, if that isn't too big
- Derives loads from houses
- Connects loads to street graph
- Clusters loads while respecting the distance on the street graph
- Those clusters are the regions of secondary substations
- Hands over the graph to GridGenerators

-> Outcome###  Street subgraphs per secondary substation region

## Functionality

### Build raw street graph

> formerly `OsmDataProvider.createRawGraph()`

Functionality:
- get each way that is a "highway" 
  - a highway is formerly described by having one of the `highwayValues`
- iterate through every way and sequentially add 
  - all nodes as vertices
  - weighted edges between the nodes where edge corresponds to haversine distance between the nodes 

Input:
- every way

Output:
- a weighted Graph of all ways


### Build highway distance matrix

> formerly `OsmDataProvider.createHighwayDistanceMatrix()`

Functionality:
- divides area into cells lat/lon cells
  - specifies `minCellDistance` (in lat/lon)
    - make configurable? 
  - divides area evenly into rows and columns 
    - by dividing the max/min lat and lon difference with the `minimalCellDistance`
- for every way
  - assign it to every cell it "passes"
    - a way "passes" a cell if one of the nodes lie within the cell
- fill every cell that is empty with all ways of the 4 surrounding cells

_Fix this_ 
- here some weird stuff happens by getting some center in relation to mysterius building at index 5 and adding the surrounding ways

Input:
- every way
- min/max lon/lat
- minlCellDistnace

Output:
- a cell matrix where 
  - each entry (cell) captures a `minCellDistance` distance in lon and lat direction
  - each entry houses all ways that have at least one node that is located within the cell area

### Define power density
- Load per area of a house
- configuration parameter

### Calculate perpendicular distance matrix

> formerly `GraphControlloer.calcPerpendicularDistanceMatrix`

Functionality: 
- for every building
  - check if `isSubstation`
  - get center of the building
  - check if the building is within a residential area 
    - if yes go on otherwise ignore the building
  - get the corresponding cell with highways of the `highwayDistanceMatrix`
  - get all ways in the corresponding cell and the 4 cells around it
  - 
  
### Update edges with correct node assignment

### Remove land uses without any building

### Update the edge weights

### Filter out dead ends from graph

### Create clusters

### Create sub-graphs
- Neglects clusters with a\nload sum beneath\nconfigured threshold

### Clean up all sub-graphs

### Calculate cluster load
