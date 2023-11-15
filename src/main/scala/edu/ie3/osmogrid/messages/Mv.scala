/*
 * © 2023. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.messages

import edu.ie3.datamodel.models.input.NodeInput
import edu.ie3.datamodel.models.input.container.SubGridContainer
import edu.ie3.osmogrid.graph.OsmGraph
import edu.ie3.osmogrid.io.input.AssetInformation
import utils.GridConversion.NodeConversion
import utils.VoronoiUtils.VoronoiPolygon

object Mv {

  /** Trait for mv requests.
    */
  sealed trait MvRequest

  /** Trait for mv responses
    */
  sealed trait MvResponse

  /** Wraps a [[MvResponse]].
    *
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
    *
    * @param nr
    *   of the graph
    * @param polygon
    *   with components for the graph
    * @param streetGraph
    *   with all street nodes
    */
  final case class StartGraphGeneration(
      nr: Int,
      polygon: VoronoiPolygon,
      streetGraph: OsmGraph,
      assetInformation: AssetInformation
  ) extends MvRequest

  /** Request for mv graph conversion.
    *
    * @param nr
    *   of the graph
    * @param graph
    *   with grid structure
    * @param nodeConversion
    *   for converting osm nodes into corresponding PSDM nodes
    */
  final case class StartGraphConversion(
      nr: Int,
      graph: OsmGraph,
      nodeConversion: NodeConversion,
      assetInformation: AssetInformation
  ) extends MvRequest

  /** Response for a mv coordinator that contains the converted grid structure
    * as nodes and lines.
    *
    * @param subGridContainer
    *   container with the generated grid
    * @param nodeChanges
    *   nodes that were changed during mv generation
    */
  final case class FinishedMvGridData(
      subGridContainer: SubGridContainer,
      nodeChanges: Seq[NodeInput]
  ) extends MvResponse
}
