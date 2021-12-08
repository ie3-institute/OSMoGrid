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
import edu.ie3.osmogrid.guardian.OsmoGridGuardian
import edu.ie3.osmogrid.guardian.OsmoGridGuardian.{
  OsmoGridGuardianEvent,
  RepLvGrids
}
import edu.ie3.osmogrid.lv.LvGenerator

object LvCoordinator {
  sealed trait LvCoordinatorEvent
  final case class ReqLvGrids(
      cfg: OsmoGridConfig.Generation.Lv,
      replyTo: ActorRef[OsmoGridGuardianEvent]
  ) extends LvCoordinatorEvent
  final case class RepLvGrids(grids: Vector[SubGridContainer])
      extends LvCoordinatorEvent

  def apply(): Behavior[LvCoordinatorEvent] = idle

  private def idle: Behavior[LvCoordinatorEvent] = Behaviors.receive {
    (ctx, msg) =>
      msg match {
        case ReqLvGrids(
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
              4) start generation (register, how many requests have been sent!) */

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

          /* Wait for the incoming data and check, if all replies are received. */
          awaitReplies(0)
        case unsupported =>
          ctx.log.error(s"Received unsupported message: $unsupported")
          Behaviors.stopped
      }
  }

  private def awaitReplies(
      awaitedReplies: Int,
      guardian: ActorRef[OsmoGridGuardianEvent],
      collectedGrids: Vector[SubGridContainer] = Vector.empty
  ): Behaviors.Receive[LvCoordinatorEvent] = Behaviors.receive {
    case (ctx, RepLvGrids(grids)) =>
      val stillAwaited = awaitedReplies - 1
      ctx.log.debug(
        s"Received another ${grids.length} sub grids. ${if (stillAwaited == 0) "All requests are answered."
        else s"Still awaiting $stillAwaited replies."}."
      )
      val updatedGrids = collectedGrids ++ grids
      if (stillAwaited == 0) {
        ctx.log.info(
          s"Received ${updatedGrids.length} sub grid containers in total. Join and send them to the guardian."
        )
        guardian ! OsmoGridGuardian.RepLvGrids(updatedGrids)
        Behaviors.stopped
      } else
        awaitedReplies(stillAwaited, updatedGrids)
    case (ctx, unsupported) =>
      ctx.log.error(s"Received unsupported message: $unsupported")
      Behaviors.stopped
  }
}
