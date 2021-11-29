# Concept

... of OSMoGrid and it's implementation.
Here, we want to focus on the overall structure of the tool and which part does serve which purpose.

## Actors
### OsmoGridGuardian
- Coordination of routine
- Error handling

### InputDataProvider
- Connects to OpenStreeMap (either via pbf file or API)
- Acquires needed data and filters it (dependent on the purpose)

### LvGenerator
#### Variant A
- Transferring OSM data into suitable graph representation
- Clustering of nodes to transformer areas
- Scissoring graph into sub graphs

### Variant B
- Scissoring OSM data along municipality boundaries
- Settlements only rarely cross municipality boundaries
- The concept of concessional agreements on serving a municipality incentive to not let grids cross boundaries
- Transferring OSM data into suitable graph representation

### LvGraphHandler
**Only in variant B**
- Clustering of nodes to transformer areas
- Scissoring graph into sub graphs

### LvGridGenerator
- Transfers a sub-graph into a sub-grid model
- Realized as worker pools held by `LvGenerator`

### ResultListener
- Collecting and consolidating electrical grid structures
- Persisting them to sinks
