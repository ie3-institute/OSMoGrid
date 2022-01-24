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
import edu.ie3.osmogrid.guardian.run.RunGuardian
import edu.ie3.osmogrid.io.input.InputDataProvider
import edu.ie3.osmogrid.io.output.ResultListener
import edu.ie3.osmogrid.lv.LvCoordinator
import edu.ie3.osmogrid.lv.LvCoordinator.ReqLvGrids

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
    Try {
      ConfigFailFast.check(
        runGuardianData.cfg,
        runGuardianData.additionalListener
      )
    }.flatMap { validConfig =>
      log.info(
        s"Initializing grid generation for run with id '${runGuardianData.runId}'!"
      )

      /* Check, which voltage level configs are given. Start with lv level, if this is desired for. */
      runGuardianData.cfg.generation match {
        case Generation(Some(lvConfig)) =>
          ctx.log.info("Starting low voltage grid coordinator ...")
          val (inputProvider, resultEventListener) =
            spawnIoActors(
              runGuardianData.runId,
              runGuardianData.cfg.input,
              runGuardianData.cfg.output,
              ctx
            )
          val lvCoordinator = startLvGridGeneration(
            runGuardianData.runId,
            lvConfig,
            runGuardianData.msgAdapters.lvCoordinator,
            ctx
          )
          Success(
            ChildReferences(
              inputProvider,
              resultEventListener,
              runGuardianData.additionalListener,
              Some(lvCoordinator)
            )
          )
        case unsupported =>
          ctx.log.error(
            s"Received unsupported grid generation config '$unsupported'. Stopping run with id '${runGuardianData.runId}'!"
          )
          Failure(
            UnsupportedRequestException(
              s"Unable to issue a generation run with the given parameters: '${runGuardianData.cfg.generation}'"
            )
          )
      }
    }
  }

  /** Spawns both the input and the output actor for the given specific run
    *
    * @param runId
    *   Identifier for the targeted run
    * @param inputConfig
    *   Configuration for the input behavior
    * @param outputConfig
    *   Configuration of the output behavior
    * @param ctx
    *   Current actor context
    * @return
    *   Reference to an [[InputDataProvider]] as well as [[ResultListener]]
    */
  private def spawnIoActors(
      runId: UUID,
      inputConfig: OsmoGridConfig.Input,
      outputConfig: OsmoGridConfig.Output,
      ctx: ActorContext[Request]
  ): (
      ActorRef[InputDataProvider.Request],
      Option[ActorRef[ResultListener.ResultEvent]]
  ) = (
    spawnInputDataProvider(runId, inputConfig, ctx),
    spawnResultListener(runId, outputConfig, ctx)
  )

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
  ): ActorRef[InputDataProvider.Request] = {
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
      ctx: ActorContext[Request]
  ): Option[ActorRef[ResultListener.ResultEvent]] = {
    val resultListener = outputConfig match {
      case Output(Some(_)) =>
        ctx.log.info("Starting output data listener ...")
        Some(
          ctx.spawn(
            ResultListener(runId, outputConfig),
            s"PersistenceResultListener_${runId.toString}"
          )
        )
      case Output(None) =>
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
    * @param lvConfig
    *   Configuration for low voltage grid generation
    * @param lvCoordinatorAdapter
    *   Message adapter to understand responses from [[LvCoordinator]]
    * @param ctx
    *   Current actor context
    */
  private def startLvGridGeneration(
      runId: UUID,
      lvConfig: OsmoGridConfig.Generation.Lv,
      lvCoordinatorAdapter: ActorRef[LvCoordinator.Response],
      ctx: ActorContext[Request]
  ): ActorRef[LvCoordinator.Request] = {
    val lvCoordinator =
      ctx.spawn(LvCoordinator(), s"LvCoordinator_${runId.toString}")
    ctx.watchWith(lvCoordinator, LvCoordinatorDied)

    ctx.log.info("Starting voltage level grid generation ...")
    lvCoordinator ! ReqLvGrids(lvConfig, lvCoordinatorAdapter)

    lvCoordinator
  }
}
