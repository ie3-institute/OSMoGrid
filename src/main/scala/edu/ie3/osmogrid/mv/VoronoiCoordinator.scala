/*
 * © 2023. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.mv

import akka.actor.typed.Behavior
import edu.ie3.osmogrid.ActorStopSupportStateless
import edu.ie3.osmogrid.graph.OsmGraph
import edu.ie3.osmogrid.model.OsmoGridModel
import edu.ie3.osmogrid.model.OsmoGridModel.MvOsmoGridModel

object VoronoiCoordinator extends ActorStopSupportStateless {

  // should receive a voronoi polynomial
  def apply(): Behavior[MvRequest] = ???

  // should generate the mv grid for the given voronoi polynomial
  def generateMvGraph(): Behavior[MvRequest] = ???

  // should wait for result
  // should send result to the MVCoordinator
  def awaitResult(): Behavior[MvResponse] = ???

  // should return the closest connections for all nodes in this voronoi polynomial
  def findClosestConnections(
      osmoGridModel: MvOsmoGridModel
  ): List[MvConnections] = {
    val (highways, highwayNodes) =
      OsmoGridModel.filterForWays(osmoGridModel.highways)
    val streetGraph: OsmGraph =
      MvGraphBuilder.buildStreetGraph(highways.seq.toSeq, highwayNodes)
    MvGraphBuilder.findAllConnections(streetGraph, highwayNodes)
  }

  /** Function to perform cleanup tasks while shutting down
    */
  override protected def cleanUp(): Unit = ???
}
