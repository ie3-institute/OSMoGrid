/*
 * © 2021. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.io.input

import edu.ie3.util.osm.OsmEntities.{Node, OpenWay, Relation, Way}
import edu.ie3.util.osm.OsmModel
import org.locationtech.jts.geom.Polygon

class OsmoGridModel(
                     nodes: List[Node],
                     ways: List[Way],
                     relations: Option[List[Relation]],
                     capturedArea: Polygon,
                     highways: List[Way],
                     buildings: List[Way],
                     landuses: List[Way]
                   ) extends OsmModel(nodes, ways, relations, capturedArea) {}

object OsmoGridModel {
}
