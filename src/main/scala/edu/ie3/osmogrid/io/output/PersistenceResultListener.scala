/*
 * © 2021. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.io.output

import akka.actor.typed.Behavior
import akka.actor.typed.ActorRef
import akka.actor.typed.PostStop
import akka.actor.typed.scaladsl.{ActorContext, Behaviors, StashBuffer}
import edu.ie3.datamodel.models.input.container.{
  GridContainer,
  JointGridContainer
}
import edu.ie3.osmogrid.cfg.OsmoGridConfig
import edu.ie3.osmogrid.cfg.OsmoGridConfig.Output
import edu.ie3.osmogrid.exception.IllegalConfigException
import edu.ie3.osmogrid.io.output.ResultListenerProtocol.{
  GridResult,
  Response,
  ResultHandled
}

import concurrent.ExecutionContext.Implicits.global
import java.util.UUID
import scala.concurrent.Future
import scala.util.{Failure, Success}

object PersistenceResultListener {

  private sealed trait PersistenceListenerEvent extends ResultListenerProtocol

  // internal API
  private final case class InitComplete(stateData: ListenerStateData)
      extends PersistenceListenerEvent

  private final case class InitFailed(cause: Throwable)
      extends PersistenceListenerEvent

  // todo JH internal persistence handling w/o external protocol (HandledResult)

  private final case class ListenerStateData(
      runId: UUID,
      cfg: OsmoGridConfig.Output,
      ctx: ActorContext[ResultListenerProtocol],
      buffer: StashBuffer[ResultListenerProtocol],
      sink: ResultSink
  )

  def apply(
      runId: UUID,
      cfg: OsmoGridConfig.Output
  ): Behavior[ResultListenerProtocol] = {
    Behaviors.withStash[ResultListenerProtocol](100) { buffer =>
      Behaviors.setup[ResultListenerProtocol] { ctx =>
        init(runId, cfg, ctx, buffer)
      }
    }
  }

  private def idle(
      stateData: ListenerStateData
  ): Behavior[ResultListenerProtocol] =
    Behaviors
      .receiveMessagePartial[ResultListenerProtocol] {
        case gridResult @ GridResult(grid, replyTo) =>
          stateData.ctx.pipeToSelf(stateData.sink.handleResult(gridResult)) {
            case Success(_) =>
              ResultHandled(stateData.runId, stateData.ctx.self)
            case Failure(exception) =>
              stateData.ctx.log.error(
                s"Error during persistence of grid result: $exception"
              )
              throw exception
          }
          save(stateData, replyTo)
      }
      .receiveSignal { case (context, PostStop) =>
        stateData.sink.close()
        context.log.info(s"$this stopped!")
        Behaviors.same
      }

  private def save(
      stateData: ListenerStateData,
      replyTo: ActorRef[Response]
  ): Behavior[ResultListenerProtocol] =
    Behaviors.receiveMessage {
      case ResultHandled(runId, self) =>
        replyTo ! ResultHandled(runId, self)
        stateData.buffer.unstashAll(idle(stateData))
      case other =>
        stateData.buffer.stash(other)
        Behaviors.same
    }

  private def init(
      runId: UUID,
      cfg: OsmoGridConfig.Output,
      ctx: ActorContext[ResultListenerProtocol],
      buffer: StashBuffer[ResultListenerProtocol]
  ): Behavior[ResultListenerProtocol] = {
    ctx.pipeToSelf(initSinks(runId, cfg)) {
      case Success(sink) =>
        InitComplete(ListenerStateData(runId, cfg, ctx, buffer, sink))
      case Failure(cause) =>
        InitFailed(cause)
    }
    Behaviors.receiveMessage {
      case InitComplete(stateData) =>
        stateData.buffer.unstashAll(idle(stateData))
      case InitFailed(cause) =>
        ctx.log.error(s"Cannot instantiate $this!", cause)
        throw cause
      case other =>
        // stash all other messages for later processing
        buffer.stash(other)
        Behaviors.same
    }
  }

  private def initSinks(
      runId: UUID,
      cfg: OsmoGridConfig.Output
  ): Future[ResultSink] = {
    cfg match {
      case Output(Some(csv)) =>
        Future(
          ResultCsvSink(runId, csv.directory, csv.separator)
        )
      case unsupported =>
        Future.failed(
          IllegalConfigException(
            s"Cannot instantiate sink of type '$unsupported'"
          )
        )
    }
  }

}
