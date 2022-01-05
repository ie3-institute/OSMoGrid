/*
 * © 2021. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.lv

import akka.actor.typed.{ActorRef, Behavior, SupervisorStrategy}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors, Routers}
import edu.ie3.datamodel.models.input.container.SubGridContainer
import edu.ie3.osmogrid.cfg.OsmoGridConfig
import edu.ie3.osmogrid.cfg.OsmoGridConfig.Generation.Lv
import edu.ie3.osmogrid.guardian.OsmoGridGuardian
import edu.ie3.osmogrid.lv.LvGridGenerator
import org.slf4j.Logger

import scala.math.ceil

object LvCoordinator {
  sealed trait Request
  final case class ReqLvGrids(
      cfg: OsmoGridConfig.Generation.Lv,
      replyTo: ActorRef[Response]
  ) extends Request
  final case class RegionCoordinatorResponse(
      response: LvRegionCoordinator.Response
  ) extends Request

  sealed trait Response
  final case class RepLvGrids(grids: Vector[SubGridContainer]) extends Response

  def apply(): Behavior[Request] = idle

  private def idle: Behavior[Request] = Behaviors.receive { (ctx, msg) =>
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
        ctx.log.debug("Request input data")
        /* TODO:
              1) Ask for OSM data
              2) Ask for asset data */

        awaitInputData(osmData = None, assetData = None)
      case unsupported =>
        ctx.log.error(s"Received unsupported message: $unsupported")
        Behaviors.stopped
    }
  }

  /** Await incoming input data and register it
    *
    * TODO: Adapt accordingly to have classes that collect the data
    *
    * @param osmData
    *   Osm data to await
    * @param assetData
    *   Asset data to await
    * @return
    *   Equivalent next state
    */
  private def awaitInputData(
      osmData: Option[Int] = None,
      assetData: Option[Int] = None
  ): Behavior[Request] = Behaviors.receive { case (ctx, msg) =>
    /* TODO: Register data according to message content */

    /* Check, if everything is in place */
    if (osmData.isDefined && assetData.isDefined) {
      /* Process the data */
      ctx.log.debug("All awaited data is present. Start processing.")

      /* Spawn the needed worker pools */
      val lvRegionCoordinator = spawnWorkerPools(ctx)
      lvRegionCoordinator ! LvRegionCoordinator.Partition(
        ctx.messageAdapter(msg => RegionCoordinatorResponse(msg))
      )

      /* Wait in idle for everything to come up */
      idle
    } else
      Behaviors.same // Wait for missing data
  }

  /** Spawn all needed worker pools and hand back the reference to the
    * [[LvRegionCoordinator]] pool
    *
    * @param ctx
    *   Actor context to spawn in
    * @return
    *   Reference to the [[LvRegionCoordinator]] pool
    */
  private def spawnWorkerPools(
      ctx: ActorContext[_]
  ): ActorRef[LvRegionCoordinator.Request] = {
    /* Determine the total amount of available processors */
    val availableProcessors = Runtime.getRuntime.availableProcessors()
    val lvRegionCoordinatorPoolSize =
      scala.math.max(ceil(availableProcessors * 0.05), 3).toInt
    val municipalityCoordinatorPoolSize =
      scala.math.max(ceil(availableProcessors * 0.05), 3).toInt
    val districtCoordinatorPoolSize =
      scala.math.max(ceil(availableProcessors * 0.2), 3).toInt
    val subDistrictCoordinatorPoolSize =
      scala.math.max(ceil(availableProcessors * 0.3), 3).toInt
    val lvGridGeneratorPoolSize =
      scala.math.max(ceil(availableProcessors * 0.4), 3).toInt

    ctx.log.debug(s"""Using the following worker pool sizes:
        |
        |region coordinator: $lvRegionCoordinatorPoolSize
        |municipality coordinator: $municipalityCoordinatorPoolSize
        |district coordinator: $districtCoordinatorPoolSize
        |sub district coordinator: $subDistrictCoordinatorPoolSize
        |lv grid generator: $lvGridGeneratorPoolSize
        |""".stripMargin)

    /* Launching worker pools */
    val lvGridGeneratorPool = Routers.pool(poolSize = lvGridGeneratorPoolSize) {
      // Restart workers on failure
      Behaviors
        .supervise(LvGridGenerator())
        .onFailure(SupervisorStrategy.restart)
    }
    val lvGridGeneratorProxy =
      ctx.spawn(lvGridGeneratorPool, "LvGridGeneratorPool")

    val subDistrictCoordinatorPool =
      Routers.pool(poolSize = subDistrictCoordinatorPoolSize) {
        // Restart workers on failure
        Behaviors
          .supervise(SubDistrictCoordinator(lvGridGeneratorProxy))
          .onFailure(SupervisorStrategy.restart)
      }
    val subDistrictCoordinatorProxy =
      ctx.spawn(subDistrictCoordinatorPool, "SubDistrictCoordinatorPool")

    val districtCoordinatorPool =
      Routers.pool(poolSize = districtCoordinatorPoolSize) {
        // Restart workers on failure
        Behaviors
          .supervise(DistrictCoordinator(subDistrictCoordinatorProxy))
          .onFailure(SupervisorStrategy.restart)
      }
    val districtCoordinatorProxy =
      ctx.spawn(districtCoordinatorPool, "DistrictCoordinatorPool")

    val municipalityCoordinatorPool =
      Routers.pool(poolSize = municipalityCoordinatorPoolSize) {
        // Restart workers on failure
        Behaviors
          .supervise(MunicipalityCoordinator(districtCoordinatorProxy))
          .onFailure(SupervisorStrategy.restart)
      }
    val municipalityCoordinatorProxy =
      ctx.spawn(municipalityCoordinatorPool, "MunicipalityCoordinatorPool")

    val regionCoordinatorPool =
      Routers.pool(poolSize = lvRegionCoordinatorPoolSize) {
        // Restart workers on failure
        Behaviors
          .supervise(LvRegionCoordinator(municipalityCoordinatorProxy))
          .onFailure(SupervisorStrategy.restart)
      }
    ctx.spawn(regionCoordinatorPool, "RegionCoordinatorPool")
  }

  private def awaitResults(
      awaitedGrids: Int = Int.MaxValue,
      subGrids: Set[SubGridContainer] = Set.empty
  ): Behavior[Request] = Behaviors.receive {
    case (
          ctx,
          RegionCoordinatorResponse(LvRegionCoordinator.Done(amountOfGrids))
        ) =>
      ctx.log.info(
        s"Grid generation succeeded. Awaiting $amountOfGrids in total."
      )
      finalizeIfReady(amountOfGrids, subGrids)
    case (ctx, unsupported) =>
      ctx.log.warn(s"Received unsupported message '$unsupported'.")
      Behaviors.stopped
  }

  /** Check if all awaited results are there and if yes, finalize their
    * collection
    *
    * @param awaitedGrids
    *   Amount of awaited grids
    * @param subGrids
    *   Yet collected sub grids
    * @return
    *   Next state
    */
  private def finalizeIfReady(
      awaitedGrids: Int,
      subGrids: Set[SubGridContainer],
      logger: Logger
  ) = {
    val missingResponses = awaitedGrids - subGrids.size
    if (missingResponses > 0) {
      logger.debug(
        s"Still ${if (missingResponses != Int.MaxValue) missingResponses
        else "some results"} pending."
      )
      awaitResults(awaitedGrids, subGrids)
    } else {
      logger.info(s"All awaited grids received. Good bye!")
      // TODO: Bring results together!
      Behaviors.stopped
    }
  }
}
