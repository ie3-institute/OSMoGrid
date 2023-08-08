/*
 * © 2023. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.mv

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior, PostStop}
import edu.ie3.osmogrid.ActorStopSupportStateless
import edu.ie3.osmogrid.model.OsmoGridModel.MvOsmoGridModel
import edu.ie3.osmogrid.mv.MvGraphBuilder.MvGraph
import utils.VoronoiUtils.VoronoiPolygon

object VoronoiCoordinator extends ActorStopSupportStateless {

  // should receive a voronoi polynomial
  def apply(
      voronoiPolygon: VoronoiPolygon,
      osmoGridModel: MvOsmoGridModel,
      coordinator: ActorRef[MvRequest],
      ctx: ActorContext[MvRequest]
  ): Behavior[MvRequest] = {
    ctx.self ! StartMvGraphGeneration
    generateMvGraph(voronoiPolygon, osmoGridModel, coordinator)
  }

  // should generate the mv grid for the given voronoi polynomial
  private def generateMvGraph(
      voronoiPolygon: VoronoiPolygon,
      osmoGridModel: MvOsmoGridModel,
      coordinator: ActorRef[MvRequest]
  ): Behavior[MvRequest] = Behaviors
    .receive[MvRequest] {
      case (ctx, StartMvGraphGeneration) =>
        val mvGraph: MvGraph = MvGraphBuilder.buildGraph(
          voronoiPolygon.transitionPointToHigherVoltLvl,
          voronoiPolygon.transitionPointsToLowerVoltLvl,
          osmoGridModel
        )

        coordinator ! FinishedMvGraph(mvGraph)

        Behaviors.same
      case (ctx, MvTerminate) =>
        terminate(ctx.log)
      case (ctx, unsupported) =>
        ctx.log.warn(
          s"Received unsupported message '$unsupported' in data awaiting state. Keep on going."
        )
        Behaviors.same
    }
    .receiveSignal { case (ctx, PostStop) =>
      postStopCleanUp(ctx.log)
    }

  /** Function to perform cleanup tasks while shutting down
    */
  override protected def cleanUp(): Unit = ???
}
