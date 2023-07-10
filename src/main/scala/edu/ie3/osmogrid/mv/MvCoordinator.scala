/*
 * © 2023. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.mv

import akka.actor.typed.Behavior
import edu.ie3.datamodel.models.input.container.SubGridContainer
import edu.ie3.osmogrid.ActorStopSupportStateless

object MvCoordinator extends ActorStopSupportStateless {
  private val mvGrids: List[SubGridContainer] = List.empty

  def apply(): Behavior[MvRequest] = ???

  // should receive request to generate and return mv grids
  // should call awaitInputData
  def idle(): Behavior[MvRequest] = ???

  // should start voronoi algorithm
  // should start a VoronoiCoordinator (one for each voronoi polynomial)
  // should call awaitResults
  def processInputData(): Behavior[MvRequest] = ???

  // should receive mv graph
  // should call MvGridBuilder
  // should wait for mv grid
  // should send mv grid to awaitResult
  def convertGraphToGrid(): Behavior[MvRequest] = ???

  // should wait for input data (Lv SubGridContainer, Hv SubGridContainer, ...)
  // should send a message to processInputData
  def awaitInputData(): Behavior[MvRequest] = ???

  // should wait for all mv graphs
  // should call convertGraphToGrid for all mv graphs
  // should wait for all mv grids
  // should send all results to sendResultsToGuardian
  def awaitResults(): Behavior[MvResponse] = ???

  // should send all mv grids to guardian
  def sendResultsToGuardian(): Behavior[MvRequest] = ???

  // splits the given area into mv grid areas using VoronoiCoordinator
  def applyVoronoiAlgorithm(): List[VoronoiPolynomial] = ???

  /** Function to perform cleanup tasks while shutting down
    */
  override protected def cleanUp(): Unit = ???
}
