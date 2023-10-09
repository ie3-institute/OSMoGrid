/*
 * © 2023. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.mv

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior, PostStop}
import edu.ie3.osmogrid.ActorStopSupportStateless
import edu.ie3.osmogrid.graph.OsmGraph
import edu.ie3.util.osm.model.OsmEntity.Node
import utils.GridConversion.NodeConversion
import utils.VoronoiUtils.VoronoiPolygon
import utils.{GridConversion, MvUtils, Solver}

object VoronoiCoordinator extends ActorStopSupportStateless {

  /** Method for creating a [[VoronoiCoordinator]].
    * @param coordinator
    *   superior actor
    * @return
    *   a new [[Behavior]]
    */
  def apply(coordinator: ActorRef[MvRequest]): Behavior[MvRequest] = idle(
    coordinator: ActorRef[MvRequest]
  )

  /** Idle behavior of the [[VoronoiCoordinator]].
    * @param coordinator
    *   superior actor
    * @return
    *   a new [[Behavior]]
    */
  private def idle(coordinator: ActorRef[MvRequest]): Behavior[MvRequest] =
    Behaviors
      .receive[MvRequest] {
        case (
              ctx,
              StartGraphGeneration(nr, polygon, streetGraph, cfg)
            ) =>
          val (graph, nodeConversion) =
            generateMvGraph(nr, polygon, streetGraph, coordinator, ctx)

          // start conversion of nodes and lines
          ctx.self ! StartGraphConversion(nr, graph, nodeConversion, cfg)
          convertingGraphToPSDM(coordinator)
        case (ctx, MvTerminate) =>
          ctx.log.info(s"Got request to terminate.")
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

  /** @param nr
    *   subnet number
    * @param voronoiPolygon
    *   polygon with nodes
    * @param streetGraph
    *   complete osm street graph
    * @param coordinator
    *   superior actor
    * @param ctx
    *   context
    * @return
    *   a osm graph and the used node conversion object
    */
  private def generateMvGraph(
      nr: Int,
      voronoiPolygon: VoronoiPolygon,
      streetGraph: OsmGraph,
      coordinator: ActorRef[MvRequest],
      ctx: ActorContext[MvRequest]
  ): (OsmGraph, NodeConversion) = {
    ctx.log.debug(s"Start generation for grid $nr.")

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

    (graph, nodeConversion)
  }

  /** Conversion behaviour of the [[VoronoiCoordinator]]
    * @param coordinator
    *   superior actor
    * @return
    *   a new [[Behavior]]
    */
  private def convertingGraphToPSDM(
      coordinator: ActorRef[MvRequest]
  ): Behavior[MvRequest] = Behaviors
    .receive[MvRequest] {
      case (ctx, StartGraphConversion(nr, graph, nodeConversion, cfg)) =>
        ctx.log.debug(s"Starting conversion for the graph of the grid $nr.")

        // converting the graph
        val (nodes, lines) = GridConversion.convertMv(nr, graph, nodeConversion)

        // sending the finished data back to the coordinator
        coordinator ! FinishedMvGridData(nodes, lines)
        Behaviors.stopped
      case (ctx, MvTerminate) =>
        ctx.log.info(s"Got request to terminate.")
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
  override protected def cleanUp(): Unit = {
    /* Nothing to do here. At least until now. */
  }
}
