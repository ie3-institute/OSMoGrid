/*
 * © 2021. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.lv

import akka.actor.typed.{ActorRef, Behavior, SupervisorStrategy}
import akka.actor.typed.scaladsl.{Behaviors, Routers}
import edu.ie3.datamodel.models.input.container.SubGridContainer
import edu.ie3.osmogrid.cfg.OsmoGridConfig
import edu.ie3.osmogrid.cfg.OsmoGridConfig.Generation.Lv
import edu.ie3.osmogrid.lv.LvGenerator

import java.util.UUID

object LvCoordinator {
  sealed trait Request
  final case class ReqLvGrids(
      runId: UUID,
      cfg: OsmoGridConfig.Generation.Lv,
      replyTo: ActorRef[Response]
  ) extends Request

  sealed trait Response
  final case class RepLvGrids(runId: UUID, grids: Seq[SubGridContainer])
      extends Response

  def apply(): Behavior[Request] = idle

  private def idle: Behavior[Request] = Behaviors.receive { (ctx, msg) =>
    msg match {
      case ReqLvGrids(
            runId,
            Lv(
              amountOfGridGenerators,
              amountOfRegionCoordinators,
              distinctHouseConnections
            ),
            replyTo
          ) =>
        ctx.log.info("Starting generation of low voltage grids!")
        /* TODO:
              1) Ask for OSM data
              2) Ask for asset data
              3) Split up osm data at municipality boundaries
              4) start generation */

        /* Spawn a pool of workers to build grids from sub-graphs */
        val lvGeneratorPool =
          Routers.pool(poolSize = amountOfGridGenerators) {
            // Restart workers on failure
            Behaviors
              .supervise(LvGenerator())
              .onFailure(SupervisorStrategy.restart)
          }
        val lvGeneratorProxy = ctx.spawn(lvGeneratorPool, "LvGeneratorPool")

        /* Spawn a pool of workers to build grids for one municipality */
        val lvRegionCoordinatorPool =
          Routers.pool(poolSize = amountOfRegionCoordinators) {
            // Restart workers on failure
            Behaviors
              .supervise(LvRegionCoordinator(lvGeneratorProxy))
              .onFailure(SupervisorStrategy.restart)
          }
        val lvRegionCoordinatorProxy =
          ctx.spawn(lvRegionCoordinatorPool, "LvRegionCoordinatorPool")

        replyTo ! RepLvGrids(runId, Seq.empty[SubGridContainer])
        Behaviors.stopped
      case unsupported =>
        ctx.log.error(s"Received unsupported message: $unsupported")
        Behaviors.stopped
    }
  }
}
