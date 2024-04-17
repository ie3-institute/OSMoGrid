/*
 * © 2022. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.guardian.run

import edu.ie3.osmogrid.cfg.ConfigFallback.lvConfig
import edu.ie3.osmogrid.cfg.OsmoGridConfig.{Generation, Output}
import edu.ie3.osmogrid.cfg.{ConfigFailFast, OsmoGridConfig}
import edu.ie3.osmogrid.exception.UnsupportedRequestException
import edu.ie3.osmogrid.io.input.{InputDataEvent, InputDataProvider}
import edu.ie3.osmogrid.io.output.{ResultListener, ResultListenerProtocol}
import edu.ie3.osmogrid.lv.{LvCoordinator, LvRequest, LvResponse, ReqLvGrids}
import edu.ie3.osmogrid.mv.{MvCoordinator, MvRequest, MvResponse, ReqMvGrids}
import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.scaladsl.ActorContext

import java.util.UUID
import scala.util.{Failure, Success, Try}

trait RunSupport {

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

        /* Check, which voltage level configs are given. Start with lv level, if this is desired for. */
        validConfig.generation match {
          case Generation(lv, mv) =>
            implicit val implicitCtx: ActorContext[RunRequest] = ctx
            implicit val id: UUID = runGuardianData.runId
            val msgAdapters = runGuardianData.msgAdapters

            ctx.log.info("Starting input provider ...")
            implicit val inputProvider: ActorRef[InputDataEvent] =
              spawnInputDataProvider(validConfig.input)

            // spin up listeners, watch them and wait until they terminate in this state
            val resultListener = spawnResultListener(runGuardianData.cfg.output)

            val mvCoordinator = mv.map { cfg =>
              ctx.log.info("Starting medium voltage grid coordinator ...")
              startMvGridGeneration(cfg, msgAdapters.mvCoordinator)
            }

            // if a mv coordinator was spawned, a lv coordinator is needed
            // if no lv config found, use fallback config
            val lvCfg = mvCoordinator.map(_ => lv.getOrElse(lvConfig))

            val lvCoordinator = lvCfg.map { cfg =>
              ctx.log.info("Starting low voltage grid coordinator ...")
              startLvGridGeneration(cfg, msgAdapters.lvCoordinator)
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
          case unsupported =>
            ctx.log.error(
              s"Received unsupported grid generation config '$unsupported'. Stopping run with id '${runGuardianData.runId}'!"
            )
            Failure(
              UnsupportedRequestException(
                s"Unable to issue a generation run with the given parameters: '${validConfig.generation}'"
              )
            )
        }
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
      inputConfig: OsmoGridConfig.Input
  )(implicit
      runId: UUID,
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
      outputConfig: OsmoGridConfig.Output
  )(implicit
      runId: UUID,
      ctx: ActorContext[RunRequest]
  ): Option[ActorRef[ResultListenerProtocol]] = {
    val resultListener = outputConfig match {
      case Output(Some(_), _) =>
        ctx.log.info("Starting output data listener ...")
        Some(
          ctx.spawn(
            ResultListener(runId, outputConfig),
            s"PersistenceResultListener_${runId.toString}"
          )
        )
      case Output(None, _) =>
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
      lvCoordinatorAdapter: ActorRef[LvResponse]
  )(implicit
      runId: UUID,
      inputDataProvider: ActorRef[InputDataEvent],
      ctx: ActorContext[RunRequest]
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
      mvConfig: OsmoGridConfig.Generation.Mv,
      mvCoordinatorAdapter: ActorRef[MvResponse]
  )(implicit
      runId: UUID,
      inputDataProvider: ActorRef[InputDataEvent],
      ctx: ActorContext[RunRequest]
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
