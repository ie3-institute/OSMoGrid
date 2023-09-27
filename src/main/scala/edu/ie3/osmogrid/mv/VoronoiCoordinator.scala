/*
 * © 2023. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.mv

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior, PostStop}
import edu.ie3.osmogrid.ActorStopSupportStateless
import edu.ie3.osmogrid.cfg.OsmoGridConfig
import edu.ie3.osmogrid.graph.OsmGraph
import edu.ie3.osmogrid.routingproblem.Solver
import edu.ie3.util.osm.model.OsmEntity.Node
import utils.MvUtils
import utils.VoronoiUtils.VoronoiPolygon

object VoronoiCoordinator extends ActorStopSupportStateless {

  // should receive a voronoi polynomial and a street graph
  def apply(
      voronoiPolygon: VoronoiPolygon,
      streetGraph: OsmGraph,
      coordinator: ActorRef[MvRequest],
      ctx: ActorContext[MvRequest],
      cfg: OsmoGridConfig.Generation.Mv
  ): Behavior[MvRequest] = {
    ctx.self ! StartGraphGeneration(cfg)
    generateMvGraph(voronoiPolygon, streetGraph, coordinator)
  }

  // should generate the mv grid for the given voronoi polynomial
  private def generateMvGraph(
      voronoiPolygon: VoronoiPolygon,
      streetGraph: OsmGraph,
      coordinator: ActorRef[MvRequest]
  ): Behavior[MvRequest] = Behaviors
    .receive[MvRequest] {
      case (ctx, StartGraphGeneration(cfg)) =>
        // if this voronoi polygon contains a polygon, we can reduce the complete street graph in order to reduce the calculation time
        val reducedStreetGraph: OsmGraph = voronoiPolygon.polygon
          .map { polygon => streetGraph.subGraph(polygon) }
          .getOrElse(streetGraph)

        // creating necessary utility objects
        val (nodeConversion, connections) =
          MvUtils.createDefinitions(voronoiPolygon.allNodes, reducedStreetGraph)

        val transitionNode: Node = nodeConversion.getOsmNode(
          voronoiPolygon.transitionPointToHigherVoltLvl
        )

        // using the solver to solve the routing problem
        val graph: OsmGraph = Solver.solve(
          transitionNode,
          connections
        )

        // conversion from osm graph to PSDM grid model
        ctx.self ! StartGraphConversion(cfg)
        convertingGraphToPSDM(graph, coordinator)
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

  // conversion of the generated graph
  private def convertingGraphToPSDM(
      graph: OsmGraph,
      coordinator: ActorRef[MvRequest]
  ): Behavior[MvRequest] = Behaviors
    .receive[MvRequest] {
      case (ctx, StartGraphConversion(cfg)) =>
        ???
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
