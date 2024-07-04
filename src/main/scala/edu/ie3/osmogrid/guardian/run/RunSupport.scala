/*
 * © 2022. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.guardian.run

import edu.ie3.osmogrid.cfg.OsmoGridConfig.Generation.Lv.{
  BoundaryAdminLevel,
  Osm
}
import edu.ie3.osmogrid.cfg.OsmoGridConfig.Generation.{Lv, Mv}
import edu.ie3.osmogrid.cfg.OsmoGridConfig.Output
import edu.ie3.osmogrid.cfg.{ConfigFailFast, OsmoGridConfig}
import edu.ie3.osmogrid.io.input.{InputDataEvent, InputDataProvider}
import edu.ie3.osmogrid.io.output.{ResultListener, ResultListenerProtocol}
import edu.ie3.osmogrid.lv.{LvCoordinator, LvRequest, LvResponse, ReqLvGrids}
import edu.ie3.osmogrid.mv.{MvCoordinator, MvRequest, MvResponse, ReqMvGrids}
import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.scaladsl.ActorContext

import java.util.UUID
import scala.util.{Success, Try}

trait RunSupport {

  private val lvFallback: Lv = Lv(
    averagePowerDensity = 12.5, // W/m^2
    boundaryAdminLevel = BoundaryAdminLevel(lowest = 8, starting = 2),
    considerHouseConnectionPoints = false,
    loadSimultaneousFactor = 0.2,
    minDistance = 10,
    osm = Osm(None)
  )

  /** Initiate a generation run and return the updated run meta data
    *
    * @param runGuardianData
    *   Meta information describing the current actor's state
    * @param ctx
    *   Current actor context
    * @return
    *   Updated run meta data
    */
  protected def initRun(
      runGuardianData: RunGuardianData,
      ctx: ActorContext[RunRequest]
  ): Try[ChildReferences] = {
    val log = ctx.log
    ConfigFailFast
      .check(
        runGuardianData.cfg,
        runGuardianData.additionalListener
      )
      .flatMap { validConfig =>
        log.info(
          s"Initializing grid generation for run with id '${runGuardianData.runId}'!"
        )

        val gridOutput = validConfig.grids.output
        val generation = validConfig.generation

        val mvFallback = if (gridOutput.hv) {
          Mv(true)
        } else Mv(false)

        val lv = if (gridOutput.lv || gridOutput.mv) {
          generation.lv.orElse {
            ctx.log.info(
              s"No lv generation config found! Using fallback lv config."
            )
            Some(lvFallback)
          }
        } else None

        val mv = if (gridOutput.mv) {
          generation.mv.orElse {
            ctx.log.info(
              s"No mv generation config found! Using fallback mv config."
            )
            Some(mvFallback)
          }
        } else None

        val id: UUID = runGuardianData.runId
        val msgAdapters = runGuardianData.msgAdapters

        ctx.log.info("Starting input provider ...")
        implicit val inputProvider: ActorRef[InputDataEvent] =
          spawnInputDataProvider(id, validConfig.input, ctx)

        // spin up listeners, watch them and wait until they terminate in this state
        val resultListener =
          spawnResultListener(id, runGuardianData.cfg.output, ctx)

        /* Check, which voltage level configs are given. Start with lv level, if this is desired for. */
        // spin up lv coordinator if a config is given
        val lvCoordinator = lv.map { cfg =>
          ctx.log.info("Starting low voltage grid coordinator ...")
          startLvGridGeneration(cfg, msgAdapters.lvCoordinator, id, ctx)
        }

        // spin up mv coordinator if a config is given
        val mvCoordinator = mv.map { cfg =>
          ctx.log.info("Starting medium voltage grid coordinator ...")

          // updating the mv generation config
          val updatedConfig = validConfig.copy(generation.copy(mv = Some(cfg)))
          startMvGridGeneration(
            updatedConfig,
            msgAdapters.mvCoordinator,
            id,
            ctx
          )
        }

        Success(
          ChildReferences(
            inputProvider,
            resultListener,
            runGuardianData.additionalListener,
            lvCoordinator,
            mvCoordinator
          )
        )
      }
  }

  /** Spawn an input data provider for this run
    *
    * @param runId
    *   Identifier for the targeted run
    * @param inputConfig
    *   Configuration for the input behavior
    * @param ctx
    *   Current actor context
    * @return
    *   Reference to an [[InputDataProvider]]
    */
  private def spawnInputDataProvider(
      runId: UUID,
      inputConfig: OsmoGridConfig.Input,
      ctx: ActorContext[RunRequest]
  ): ActorRef[InputDataEvent] = {
    ctx.log.info("Starting input data provider ...")
    val inputProvider =
      ctx.spawn(
        InputDataProvider(inputConfig),
        s"InputDataProvider_${runId.toString}"
      )
    ctx.watchWith(inputProvider, InputDataProviderDied)
    inputProvider
  }

  /** Spawn a result listener for the specified run
    *
    * @param runId
    *   Identifier for the targeted run
    * @param outputConfig
    *   Configuration of the output behavior
    * @param ctx
    *   Current actor context
    * @return
    *   References to [[ResultListener]]
    */
  private def spawnResultListener(
      runId: UUID,
      outputConfig: OsmoGridConfig.Output,
      ctx: ActorContext[RunRequest]
  ): Option[ActorRef[ResultListenerProtocol]] = {
    val resultListener = outputConfig match {
      case Output(_, Some(_), _) =>
        ctx.log.info("Starting output data listener ...")
        Some(
          ctx.spawn(
            ResultListener(runId, outputConfig),
            s"PersistenceResultListener_${runId.toString}"
          )
        )
      case Output(_, None, _) =>
        ctx.log.warn(s"No result listener configured for run $runId.")
        None
    }
    resultListener.foreach(
      ctx.watchWith(_, ResultEventListenerDied)
    )
    resultListener
  }

  /** Spawn a [[LvCoordinator]] for the targeted run and ask it to start
    * conversion
    * @param lvConfig
    *   Configuration for low voltage grid generation
    *
    * @param lvCoordinatorAdapter
    *   Message adapter to understand responses from [[LvCoordinator]]
    * @param runId
    *   Identifier for the targeted run
    * @param inputDataProvider
    *   Reference to the [[InputDataProvider]] for this run
    * @param ctx
    *   Current actor context
    */
  private def startLvGridGeneration(
      lvConfig: OsmoGridConfig.Generation.Lv,
      lvCoordinatorAdapter: ActorRef[LvResponse],
      runId: UUID,
      ctx: ActorContext[RunRequest]
  )(implicit
      inputDataProvider: ActorRef[InputDataEvent]
  ): ActorRef[LvRequest] = {
    val lvCoordinator =
      ctx.spawn(
        LvCoordinator(lvConfig, inputDataProvider, lvCoordinatorAdapter),
        s"LvCoordinator_${runId.toString}"
      )
    ctx.watchWith(lvCoordinator, LvCoordinatorDied)

    ctx.log.info("Starting voltage level grid generation ...")
    lvCoordinator ! ReqLvGrids

    lvCoordinator
  }

  /** Spawn a [[MvCoordinator]] for the targeted run and ask it to start.
    *
    * @param mvConfig
    *   configuration for medium voltage grid generation
    * @param mvCoordinatorAdapter
    *   message adapter to understand responses from [[MvCoordinator]]
    * @param runId
    *   identifier for the targeted run
    * @param ctx
    *   current actor context
    * @return
    *   an [[ActorRef]] for a [[MvRequest]]
    */
  private def startMvGridGeneration(
      mvConfig: OsmoGridConfig,
      mvCoordinatorAdapter: ActorRef[MvResponse],
      runId: UUID,
      ctx: ActorContext[RunRequest]
  )(implicit
      inputDataProvider: ActorRef[InputDataEvent]
  ): ActorRef[MvRequest] = {
    val mvCoordinator =
      ctx.spawn(
        MvCoordinator(mvConfig, inputDataProvider, mvCoordinatorAdapter),
        s"MvCoordinator_${runId.toString}"
      )
    ctx.watchWith(mvCoordinator, MvCoordinatorDied)

    ctx.log.info("Starting voltage level grid generation ...")
    mvCoordinator ! ReqMvGrids

    mvCoordinator
  }
}
