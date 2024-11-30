# The intricacies of OSM data

Since OpenStreetMap data is user-generated, there is no guarantee that it follows stringent formatting and defined structures.
In order to still make grid generation work, we hard-coded some things that bypass failures that we encountered.
These are some of them:

## Tiny pieces of unconnected highways

Some small unconnected highways need to be discarded, although buildings might have been assigned to them.

[![Example 1](../_static/images/osm_highway_orphan1.png)](https://www.openstreetmap.org/way/57840227)

[![Example 2](../_static/images/osm_highway_orphan2.png)](https://www.openstreetmap.org/way/332552981)
