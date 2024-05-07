/*
 * © 2023. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.mv

import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.actor.typed.{ActorRef, Behavior, PostStop}
import edu.ie3.datamodel.models.input.NodeInput
import edu.ie3.datamodel.models.input.container.SubGridContainer
import edu.ie3.osmogrid.ActorStopSupportStateless
import edu.ie3.osmogrid.mv.MvGridGeneratorSupport.buildGrid
import utils.GridConversion
import MvGraphGeneratorSupport.generateMvGraph
import VoronoiPolygonSupport.VoronoiPolygon

/** Coordinator for [[VoronoiPolygon]]s. This actor will generate a mv graph
  * structure and convert the structure into a [[SubGridContainer]] and a
  * sequence of changed [[NodeInput]]s.
  */
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
              StartMvGraphGeneration(
                nr,
                polygon,
                mvSlackNode,
                streetGraph,
                assetInformation
              )
            ) =>
          val (graph, nodeConversion) =
            generateMvGraph(nr, polygon, streetGraph)

          // start conversion of nodes and lines
          ctx.self ! StartMvGraphConversion(
            nr,
            graph,
            mvSlackNode,
            nodeConversion,
            assetInformation
          )
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
      case (
            ctx,
            StartMvGraphConversion(
              nr,
              graph,
              mvSlackNode,
              nodeConversion,
              assetInformation
            )
          ) =>
        ctx.log.debug(s"Starting conversion for the graph of the grid $nr.")

        // converting the graph
        val (subgrid, nodes) =
          buildGrid(nr, graph, mvSlackNode, nodeConversion, assetInformation)

        // sending the finished data back to the coordinator
        coordinator ! WrappedMvResponse(
          FinishedMvGridData(subgrid, nodes)
        )
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
