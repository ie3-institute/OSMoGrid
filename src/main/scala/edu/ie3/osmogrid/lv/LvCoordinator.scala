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
import edu.ie3.osmogrid.lv.LvGenerator

object LvCoordinator {
  sealed trait Request
  final case class ReqLvGrids(
      cfg: OsmoGridConfig.Generation.Lv,
      replyTo: ActorRef[Response]
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
      val (
        municipalityCoordinator,
        districtCoordinator,
        subDistrictCoordinator,
        lvGridGenerator
      ) = spawnWorkerPools

      /* Check if further partitioning is needed or directly hand over to municipality handling */
      administrativeBoundaries() match {
        case maybeBoundaries if isOnMunicipalLevel(maybeBoundaries) =>
        /* There either is no boundary at all or the area only contains municipal boundaries and less */
        // TODO: Leave out the splitting logic and directly spawn a handler for municipalities
        case Some(level) =>
        /* TODO: Initiate further separation */
      }

      /* Wait in idle for everything to come up */
      idle
    } else
      Behaviors.same // Wait for missing data
  }

  private def spawnWorkerPools
      : (ActorRef[_], ActorRef[_], ActorRef[_], ActorRef[_]) = {
    /* TODO:
     *   1) MunicipalityCoordinator
     *   2) DistrictCoordinator
     *   3) SubDistrictCoordinator
     *   4) LvGridGenerator */

    //        /* Spawn a pool of workers to build grids from sub-graphs */
    //        val lvGeneratorPool =
    //          Routers.pool(poolSize = amountOfGridGenerators) {
    //            // Restart workers on failure
    //            Behaviors
    //              .supervise(LvGenerator())
    //              .onFailure(SupervisorStrategy.restart)
    //          }
    //        val lvGeneratorProxy = ctx.spawn(lvGeneratorPool, "LvGeneratorPool")
    //
    //        /* Spawn a pool of workers to build grids for one municipality */
    //        val lvRegionCoordinatorPool =
    //          Routers.pool(poolSize = amountOfRegionCoordinators) {
    //            // Restart workers on failure
    //            Behaviors
    //              .supervise(LvRegionCoordinator(lvGeneratorProxy))
    //              .onFailure(SupervisorStrategy.restart)
    //          }
    //        val lvRegionCoordinatorProxy =
    //          ctx.spawn(lvRegionCoordinatorPool, "LvRegionCoordinatorPool")
    ???
  }

  /** Determine available administrative boundaries within given osm data
    *
    * TODO: Adapt, when data model is fully clear
    *
    * @return
    *   Option onto the highest administrative boundary level
    */
  private def administrativeBoundaries(): Option[Set[Int]] = None

  /** Detect, if the region contains only boundaries beneath the municipality
    * level. If there is no boundary at all, it is also treated, as if we are on
    * the municipal level.
    *
    * @param maybeBoundaries
    *   Possible boundaries
    * @return
    *   true, if there is nothing above municipalities apparent
    */
  private def isOnMunicipalLevel(maybeBoundaries: Option[Set[Int]]) =
    maybeBoundaries match {
      case Some(boundaries) =>
        boundaries.maxOption match {
          case Some(highestBoundary) => highestBoundary < 6
          case None                  => true
        }
      case None => true
    }
}
