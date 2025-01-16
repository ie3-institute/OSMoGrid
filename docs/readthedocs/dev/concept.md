# Concept

... of OSMoGrid and its implementation.
Here, we want to focus on the overall structure of the tool and which part does serve which purpose.

The computational heaviest part is connecting households / loads to the street graph as well as clustering the loads based on the distances following the street graph.
This is why we want to shift those parts to the smallest regions possible.
To achieve this, a given region of interest is partitioned heuristically as much as possible.
The concept is as follows:

![](../_static/figures/region_separation.png)

The region of interest is broken down according to the administrative boundaries until the level of municipality boundaries is reached.
Moreover, these municipal boundaries are further broken down to districts.
They are defined as local dense groups of houses, that do form disjoint groups.
However, if those dense groups are very big, computational complexity still can be too big.
So, if need be, the districts are further broken down into smaller sub-districts.
Based on those (sub-)districts, cluster of loads are build, that later form the regions of secondary substations.
Those clusters respect the distance between them according to the street graph, they are connected to.
Lastly, the street graph is converted into a grid model.

Following this concept, the following actor hierarchy is implemented:

## General 

Actors related to operations that are relevant to multiple or all voltage levels:

### OsmoGridGuardian
- Coordination of multi voltage level spanning routine
- Error handling
- Collection of lv grids received from `LvCoordinator` and assigning subnet numbers

#### Finite state representation
![](../_static/figures/puml/OsmoGridGuardian.svg)

### InputDataProvider
- Connects to OpenStreeMap (either via pbf file or API)
- Acquires needed data and filters it (on request and dependent on the purpose)

### ResultListener
- Persisting overall grid model to sinks

## Low voltage

Actors relevant to low voltage grid generation:

### LvCoordinator
- Coordinates the generation of the whole low voltage level
- Spawns an `LvRegionCoordinator` to split up the region of interest
- Collects results and checks completeness
- *Outcome*: Complete region of interest to treat

#### Finite state representation
![](../_static/figures/puml/LvCoordinator.svg)

### LvRegionCoordinator
- Splits up the region of interest according to a given administrative boundary
- If the lowest administrative level has **NOT** been reached:
  - Spawns new `LvRegionCoordinator`s per new subregion to split up for the next lower administrative boundary
  - *Outcome*: Subregions on administrative level `n` + the next lowest administrative level `n-1` 
- If the lowest administrative level has been reached:
  - Hand over the partitioned regions to a `LvGridGenerator` each
  - *Outcome*: Subregions on administrative level "municipality"

### MunicipalityCoordinator
- **Currently not implemented!**
- Coordinates the region partitioning within the administrative boundary of a municipality
- Determines dense and disjoint groups of houses as local "districts"
- *Outcome*: Districts within the given municipality

### DistrictCoordinator
- **Currently not implemented!**
- Breaks down districts even further, if still too big w.r.t. computational measures (to be defined)
- *Outcome*: (Sub)districts with suitable size

### SubDistrictCoordinator
- **Currently not implemented!**
- Responsible for handling a sub-district
- A sub-district can also be a district, if that isn't too big
- Derives loads from houses
- Connects loads to street graph
- Clusters loads while respecting the distance on the street graph
- Those clusters are the regions of secondary substations
- Hands over the graph to `GridGenerator`s
- *Outcome*: Street subgraphs per secondary substation region

### LvGridGenerator
- Detects unconnected sub graphs and further clusters LV nodes into smaller sub graphs
- Builds galvanically isolated lv sub grid model from sub graphs
- Hands back grid model to `LvCoordinator`
- *Outcome*: Grid model per secondary substation region

## Medium voltage

Actors relevant to medium voltage grid generation:

### MvCoordinator
- Coordinates the generation of the whole medium voltage level
- Requests and handles asset type information as well as LV and HV grid data
- Partitions MV nodes by using Voronoi areas defined by HV nodes once all required data has been received
- Starts VoronoiCoordinator and hands over partitioned data

### VoronoiCoordinator
- Generates a MV graph structure
- Builds a grid model based on graph structure
- Returns result to MvCoordinator
