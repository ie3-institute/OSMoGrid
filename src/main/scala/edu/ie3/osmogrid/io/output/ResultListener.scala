/*
 * © 2021. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.io.output

import org.apache.pekko.actor.typed.scaladsl.{
  ActorContext,
  Behaviors,
  StashBuffer
}
import org.apache.pekko.actor.typed.{Behavior, PostStop}
import edu.ie3.osmogrid.ActorStopSupport
import edu.ie3.osmogrid.cfg.OsmoGridConfig
import edu.ie3.osmogrid.cfg.OsmoGridConfig.Output
import edu.ie3.osmogrid.exception.IllegalConfigException
import edu.ie3.osmogrid.io.output.PersistenceListenerEvent.{
  InitComplete,
  InitFailed,
  ResultHandlingFailed,
  ResultHandlingSucceeded
}

import java.nio.file.Path
import java.text.SimpleDateFormat
import java.time.ZonedDateTime
import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success}

object ResultListener extends ActorStopSupport[ListenerStateData] {

  def apply(
      runId: UUID,
      cfg: OsmoGridConfig.Output
  ): Behavior[ResultListenerProtocol] = {
    Behaviors.withStash[ResultListenerProtocol](100) { buffer =>
      Behaviors.setup[ResultListenerProtocol] { ctx =>
        ctx.pipeToSelf(initSinks(runId, cfg)) {
          case Success(sink) =>
            InitComplete(ListenerStateData(runId, ctx, buffer, sink))
          case Failure(cause) =>
            InitFailed(cause)
        }
        init(ctx, buffer)
      }
    }
  }

  private def idle(
      stateData: ListenerStateData
  ): Behavior[ResultListenerProtocol] =
    Behaviors.receiveMessagePartial { case gridResult: GridResult =>
      stateData.ctx.pipeToSelf(stateData.sink.handleResult(gridResult)) {
        case Success(_) =>
          ResultHandlingSucceeded
        case Failure(exception) =>
          ResultHandlingFailed(exception)
      }
      save(stateData)
    }

  private def save(
      stateData: ListenerStateData
  ): Behavior[ResultListenerProtocol] =
    Behaviors
      .receiveMessage[ResultListenerProtocol] {
        case ResultHandlingFailed(cause) =>
          stateData.ctx.log.error(
            s"Error during persistence of grid result. Shutting down!",
            cause
          )
          Behaviors.stopped
        case ResultHandlingSucceeded =>
          Behaviors.stopped
        case other =>
          stateData.buffer.stash(other)
          Behaviors.same
      }
      .receiveSignal { case (ctx, PostStop) =>
        if (!stateData.buffer.isEmpty)
          ctx.log.warn(
            s"Stash of ResultListener is not empty! This indicates an invalid system state!"
          )
        postStopCleanUp(ctx.log, stateData)
      }

  private def init(
      ctx: ActorContext[ResultListenerProtocol],
      buffer: StashBuffer[ResultListenerProtocol]
  ): Behavior[ResultListenerProtocol] =
    Behaviors.receiveMessage {
      case InitComplete(stateData) =>
        stateData.buffer.unstashAll(idle(stateData))
      case InitFailed(cause) =>
        ctx.log.error(s"Cannot instantiate ResultListener!", cause)
        Behaviors.stopped
      case other =>
        // stash all other messages for later processing
        buffer.stash(other)
        Behaviors.same
    }

  private def initSinks(
      runId: UUID,
      cfg: OsmoGridConfig.Output
  ): Future[ResultSink] = {
    val runStartTimeUTC: String =
      new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new java.util.Date())

    cfg match {
      case Output(addTimestampToOutputDir, Some(csv), _) =>
        val optionalSuffix =
          if (addTimestampToOutputDir) s"_$runStartTimeUTC" else ""

        Future(
          ResultCsvSink(
            runId,
            Path.of(csv.directory + optionalSuffix),
            csv.separator,
            csv.hierarchic
          )
        )
      case unsupported =>
        Future.failed(
          IllegalConfigException(
            s"Cannot instantiate sink of type '$unsupported'"
          )
        )
    }
  }

  override protected def cleanUp(stateData: ListenerStateData): Unit = {
    stateData.sink.close()
  }
}
