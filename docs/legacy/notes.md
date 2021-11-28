# Note collection for legacy code
## OSM handling
- Ignore relations
- Buildings outside of the specified land use areas are neglected

### Implementation details
1. Extraction of buildings
- Buildings are ways with tag `building`
- Substations are ways with key / value `building:transformer_tower` or `power:substation`
2. Extracting land use areas
- Land use areas are ways with tag `landuse`
- Only ways with values of `residential`, `commercial`, `retail`, `farmyard` of tag `landuse` are accounted for
3. Extracting highways
- Highways are ways with key `highway`
- Only ways with values of `residential`, `unclassified`, `secondary`, `tertiary`, `living_street`, `footway`, `path`, `primary`, `service`, `cycleway`, `proposed`, `bus_stop`, `steps`, `track`, `traffic_signals`, `turning_cycle` of tag `highway` are accounted for
4. Building of highway distance matrix
- Idea: Create a squared grid over the area of interest (currently 0.005 degree width / height) and assign all starting / ending highways to that cell

## TODOS
- [ ] Figure out, what `cutArea` and `plot` (both boolean) mean in runtime config
- [ ] Define a clear distinction between the graph obtained by OSM-data and the transformed one, that we need
- [ ] Refactoring of perpendicular matrix calculation to utilize sensible variable names
