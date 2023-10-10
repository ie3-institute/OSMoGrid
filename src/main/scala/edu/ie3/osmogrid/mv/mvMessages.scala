/*
 * © 2023. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.mv

import edu.ie3.datamodel.models.input.NodeInput
import edu.ie3.datamodel.models.input.connector.LineInput
import edu.ie3.osmogrid.cfg.OsmoGridConfig
import edu.ie3.osmogrid.graph.OsmGraph
import utils.GridConversion.NodeConversion
import utils.VoronoiUtils.VoronoiPolygon

/** Trait for mv requests.
  */
sealed trait MvRequest

/** Trait for mv responses
  */
sealed trait MvResponse

/** Wraps a [[MvResponse]].
  * @param response
  *   to be wrapped
  */
final case class WrappedMvResponse(
    response: MvResponse
) extends MvRequest

/** Mv termination request.
  */
object MvTerminate extends MvRequest

/** Request for mv graph generation.
  * @param nr
  *   of the graph
  * @param polygon
  *   with components for the graph
  * @param streetGraph
  *   with all street nodes
  * @param cfg
  *   for mv generation
  */
final case class StartGraphGeneration(
    nr: Int,
    polygon: VoronoiPolygon,
    streetGraph: OsmGraph,
    cfg: OsmoGridConfig.Generation.Mv
) extends MvRequest

/** Request for mv graph conversion.
  * @param nr
  *   of the graph
  * @param graph
  *   with grid structure
  * @param nodeConversion
  *   for converting osm nodes into corresponding PSDM nodes
  * @param cfg
  *   for ,v generation
  */
final case class StartGraphConversion(
    nr: Int,
    graph: OsmGraph,
    nodeConversion: NodeConversion,
    cfg: OsmoGridConfig.Generation.Mv
) extends MvRequest

/** Response for a mv coordinator that contains the converted grid structure as
  * nodes and lines.
  * @param nodes
  *   of the subgrid
  * @param lines
  *   of the subgrid
  */
final case class FinishedMvGridData(
    nodes: Set[NodeInput],
    lines: Set[LineInput]
) extends MvResponse
