/*
 * © 2023. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.mv

import edu.ie3.datamodel.graph.DistanceWeightedEdge
import edu.ie3.datamodel.models.input.NodeInput
import edu.ie3.datamodel.models.input.container.SubGridContainer
import edu.ie3.osmogrid.model.OsmoGridModel.MvOsmoGridModel
import edu.ie3.util.osm.model.OsmEntity.Node
import org.jgrapht.GraphPath
import tech.units.indriya.ComparableQuantity

import javax.measure.quantity.Length

sealed trait MvRequest
sealed trait MvResponse

final case class MvInputData(
    lvGrids: List[SubGridContainer],
    hvGrids: List[SubGridContainer],
    osmData: MvOsmoGridModel
) extends MvRequest

// result of a voronoi algorithm
final case class VoronoiPolynomial(
    areaNumber: Int,
    hvNode: NodeInput,
    mvNodes: List[NodeInput]
)

final case class NodeConversion(
    node: NodeInput,
    osmNode: Node
)

final case class MvConnections(
    nodeA: Node,
    nodeB: Node,
    distances: ComparableQuantity[Length],
    path: GraphPath[Node, DistanceWeightedEdge]
)
