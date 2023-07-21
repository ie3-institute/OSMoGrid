/*
 * © 2023. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.mv

import akka.actor.typed.Behavior
import edu.ie3.osmogrid.ActorStopSupportStateless
import edu.ie3.osmogrid.model.OsmoGridModel
import edu.ie3.osmogrid.model.OsmoGridModel.MvOsmoGridModel
import edu.ie3.osmogrid.mv.MvGraphBuilder.MvConnections

object VoronoiCoordinator extends ActorStopSupportStateless {

  // should receive a voronoi polynomial
  def apply(): Behavior[MvRequest] = ???

  // should generate the mv grid for the given voronoi polynomial
  def generateMvGraph(): Behavior[MvRequest] = ???

  // should wait for result
  // should send result to the MVCoordinator
  def awaitResult(): Behavior[MvResponse] = ???

  // should return the closest connections for all nodes in this voronoi polynomial
  // the MvConnections can be used to build a mv graph
  private def findClosestConnections(
      osmoGridModel: MvOsmoGridModel
  ): List[MvConnections] = {
    val (highways, highwayNodes) =
      OsmoGridModel.filterForWays(osmoGridModel.highways)

    /*
      TODO: Change closest connection calculation after alternative is properly tested
      val streetGraph: OsmGraph = MvGraphBuilder.buildStreetGraph(highways.seq.toSeq, highwayNodes)
      MvGraphBuilder.findAllConnections(streetGraph, highwayNodes)
     */
    MvGraphBuilder.findAllConnections(highwayNodes)
  }

  /** Function to perform cleanup tasks while shutting down
    */
  override protected def cleanUp(): Unit = ???
}
