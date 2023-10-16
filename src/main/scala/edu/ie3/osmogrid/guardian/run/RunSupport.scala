/*
 * © 2022. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.guardian.run

import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.ActorContext
import edu.ie3.osmogrid.cfg.OsmoGridConfig.{Generation, Output}
import edu.ie3.osmogrid.cfg.{ConfigFailFast, OsmoGridConfig}
import edu.ie3.osmogrid.exception.UnsupportedRequestException
import edu.ie3.osmogrid.io.input.{InputDataEvent, InputDataProvider}
import edu.ie3.osmogrid.io.output.{ResultListener, ResultListenerProtocol}
import edu.ie3.osmogrid.lv.coordinator
import edu.ie3.osmogrid.lv.coordinator.{
  LvCoordinator,
  Request => LvCoordinatorRequest
}
import edu.ie3.osmogrid.mv.{MvCoordinator, MvRequest, MvResponse, ReqMvGrids}

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
      ctx: ActorContext[Request]
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
        // TODO: Re-do this part!
        validConfig.generation match {
          case Generation(Some(lvConfig), Some(mvConfig)) =>
            ctx.log.info("Starting low voltage grid coordinator ...")
            val inputProvider =
              spawnInputDataProvider(
                runGuardianData.runId,
                validConfig.input,
                ctx
              )

            // spin up listeners, watch them and wait until they terminate in this state
            val resultListener = spawnResultListener(
              runGuardianData.runId,
              runGuardianData.cfg.output,
              ctx
            )

            val lvCoordinator = startLvGridGeneration(
              runGuardianData.runId,
              inputProvider,
              lvConfig,
              runGuardianData.msgAdapters.lvCoordinator,
              ctx
            )

            val mvCoordinator = startMvGridGeneration(
              runGuardianData.runId,
              mvConfig,
              runGuardianData.msgAdapters.mvCoordinator,
              ctx
            )

            Success(
              ChildReferences(
                inputProvider,
                resultListener,
                runGuardianData.additionalListener,
                Some(lvCoordinator),
                Some(mvCoordinator)
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
      runId: UUID,
      inputConfig: OsmoGridConfig.Input,
      ctx: ActorContext[Request]
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
  protected def spawnResultListener(
      runId: UUID,
      outputConfig: OsmoGridConfig.Output,
      ctx: ActorContext[Request]
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
    *
    * @param runId
    *   Identifier for the targeted run
    * @param inputDataProvider
    *   Reference to the [[InputDataProvider]] for this run
    * @param lvConfig
    *   Configuration for low voltage grid generation
    * @param lvCoordinatorAdapter
    *   Message adapter to understand responses from [[LvCoordinator]]
    * @param ctx
    *   Current actor context
    */
  private def startLvGridGeneration(
      runId: UUID,
      inputDataProvider: ActorRef[InputDataEvent],
      lvConfig: OsmoGridConfig.Generation.Lv,
      lvCoordinatorAdapter: ActorRef[coordinator.Response],
      ctx: ActorContext[Request]
  ): ActorRef[LvCoordinatorRequest] = {
    val lvCoordinator =
      ctx.spawn(
        LvCoordinator(lvConfig, inputDataProvider, lvCoordinatorAdapter),
        s"LvCoordinator_${runId.toString}"
      )
    ctx.watchWith(lvCoordinator, LvCoordinatorDied)

    ctx.log.info("Starting voltage level grid generation ...")
    lvCoordinator ! coordinator.ReqLvGrids

    lvCoordinator
  }

  /** Spawn a [[MvCoordinator]] for the targeted run and ask it to start.
    * @param runId
    *   identifier for the targeted run
    * @param mvConfig
    *   configuration for medium voltage grid generation
    * @param mvCoordinatorAdapter
    *   message adapter to understand responses from [[MvCoordinator]]
    * @param ctx
    *   current actor context
    * @return
    *   an [[ActorRef]] for a [[MvRequest]]
    */
  private def startMvGridGeneration(
      runId: UUID,
      mvConfig: OsmoGridConfig.Generation.Mv,
      mvCoordinatorAdapter: ActorRef[MvResponse],
      ctx: ActorContext[Request]
  ): ActorRef[MvRequest] = {
    val mvCoordinator =
      ctx.spawn(
        MvCoordinator(mvConfig, mvCoordinatorAdapter),
        s"LvCoordinator_${runId.toString}"
      )
    ctx.watchWith(mvCoordinator, MvCoordinatorDied)

    ctx.log.info("Starting voltage level grid generation ...")
    mvCoordinator ! ReqMvGrids

    mvCoordinator
  }
}
