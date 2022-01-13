/*
 * © 2022. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.guardian

import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.ActorContext
import edu.ie3.osmogrid.cfg.{ConfigFailFast, OsmoGridConfig}
import edu.ie3.osmogrid.cfg.OsmoGridConfig.{Generation, Output}
import edu.ie3.osmogrid.guardian.OsmoGridGuardian.{
  InputDataProviderDied,
  LvCoordinatorDied,
  Request,
  ResultEventListenerDied,
  Run,
  RunData
}
import edu.ie3.osmogrid.io.input.InputDataProvider
import edu.ie3.osmogrid.io.output.ResultListener
import edu.ie3.osmogrid.lv.LvCoordinator
import edu.ie3.osmogrid.lv.LvCoordinator.ReqLvGrids

import java.util.UUID

trait RunSupport {

  /** Initiate a generation run and return the updated run meta data
    *
    * @param run
    *   Current run meta data
    * @param ctx
    *   Current actor context
    * @param lvCoordinatorAdapter
    *   Message adapter to understand [[LvCoordinator]]
    * @return
    *   Updated run meta data
    */
  protected def initRun(
      run: Run,
      ctx: ActorContext[Request],
      lvCoordinatorAdapter: ActorRef[LvCoordinator.Response]
  ): RunData = {
    val log = ctx.log
    ConfigFailFast.check(run.cfg, run.additionalListener)
    log.info(s"Initializing grid generation for run with id '${run.runId}'!")

    val inputProvider =
      spawnInputDataProvider(run.runId, run.cfg.input, ctx)
    val resultEventListener =
      spawnResultListener(run.runId, run.cfg.output, ctx)
    /* Check, which voltage level configs are given. Start with lv level, if this is desired for. */
    run.cfg.generation match {
      case Generation(Some(lvConfig)) =>
        ctx.log.info("Starting low voltage grid coordinator ...")
        startLvGridGeneration(
          run.runId,
          lvConfig,
          lvCoordinatorAdapter,
          ctx
        )
      case unsupported =>
        ctx.log.error(
          s"Received unsupported grid generation config '$unsupported'. Stopping run with id '${run.runId}'!"
        )
        stopChildrenByRun(
          RunData(run, resultEventListener, inputProvider),
          ctx
        )
    }

    RunData(run, resultEventListener, inputProvider)
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
  ): ActorRef[InputDataProvider.Request] = {
    ctx.log.info("Starting input data provider ...")
    val inputProvider =
      ctx.spawn(
        InputDataProvider(inputConfig),
        s"InputDataProvider_${runId.toString}"
      )
    ctx.watchWith(inputProvider, InputDataProviderDied(runId))
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
  ): Vector[ActorRef[ResultListener.ResultEvent]] = {
    val resultListener = outputConfig match {
      case Output(Some(_)) =>
        ctx.log.info("Starting output data listener ...")
        Vector(
          ctx.spawn(
            ResultListener(runId, outputConfig),
            s"PersistenceResultListener_${runId.toString}"
          )
        )
      case Output(None) =>
        Vector.empty
    }
    resultListener.foreach(
      ctx.watchWith(_, ResultEventListenerDied(runId))
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
  ): Unit = {
    val lvCoordinator =
      ctx.spawn(LvCoordinator(), s"LvCoordinator_${runId.toString}")
    ctx.watchWith(lvCoordinator, LvCoordinatorDied(runId))

    ctx.log.info("Starting voltage level grid generation ...")
    lvCoordinator ! ReqLvGrids(runId, lvConfig, lvCoordinatorAdapter)
  }

  /** Stop all children for the given run
    *
    * @param runData
    *   Current run meta data
    * @param ctx
    *   Current actor context
    */
  protected def stopChildrenByRun(
      runData: RunData,
      ctx: ActorContext[Request]
  ): Unit = {
    ctx.unwatch(runData.inputDataProvider)
    ctx.stop(runData.inputDataProvider)
    runData.resultEventListener.foreach(ctx.unwatch)
    runData.resultEventListener.foreach(ctx.stop)
  }
}
