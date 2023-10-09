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

sealed trait MvRequest
sealed trait MvResponse

object MvTerminate extends MvRequest

final case class StartGraphGeneration(
    nr: Int,
    polygon: VoronoiPolygon,
    streetGraph: OsmGraph,
    cfg: OsmoGridConfig.Generation.Mv
) extends MvRequest
final case class StartGraphConversion(
    nr: Int,
    graph: OsmGraph,
    nodeConversion: NodeConversion,
    cfg: OsmoGridConfig.Generation.Mv
) extends MvRequest
final case class FinishedMvGridData(
    nodes: Set[NodeInput],
    lines: Set[LineInput]
) extends MvRequest
